package dev.strategos.core.sim.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.AiSlots;
import dev.strategos.core.sim.AiStateStore;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.EntitySlots;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.UnitRecord;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.garrison.GarrisonStore;
import dev.strategos.core.sim.nav.MemberPath;
import dev.strategos.core.sim.nav.PathRequest;
import dev.strategos.core.sim.nav.PathResult;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.order.StanceStore;
import dev.strategos.core.sim.unitai.StateId;
import dev.strategos.core.sim.unitai.UnitAi;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * A match snapshot as data. Seeds and hashes travel as {@code Hex64} strings, ticks as longs, slot arrays as int streams
 * (an {@code IntArrayTag} in NBT), templates by string id. {@link #encode} adds {@code "v"}; {@link #decode} migrates
 * first.
 */
@CoreApi
public final class MatchSnapshotCodec {
    private static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);

    /** Arrays that are no slot table decode to an error, never an exception. */
    private static final Codec<EntitySlots> SLOTS = RecordCodecBuilder.<int[][]>create(i -> i.group(
                            INTS.fieldOf("generations").forGetter(s -> s[0]),
                            INTS.fieldOf("free").forGetter(s -> s[1]))
                    .apply(i, (generations, free) -> new int[][] {generations, free}))
            .flatXmap(
                    s -> {
                        String problem = EntitySlots.problem(s[0], s[1]);
                        return problem == null
                                ? DataResult.success(new EntitySlots(s[0], s[1]))
                                : DataResult.error(() -> problem);
                    },
                    slots -> DataResult.success(new int[][] {slots.generations(), slots.freeIndices()}));

    private static final Codec<double[]> DOUBLES = Codec.DOUBLE
            .listOf()
            .xmap(
                    l -> l.stream().mapToDouble(Double::doubleValue).toArray(),
                    a -> Arrays.stream(a).boxed().toList());

    private static final Codec<PathStore.Waypoints> WAYPOINTS = RecordCodecBuilder.create(i -> i.group(
                    DOUBLES.fieldOf("x").forGetter(PathStore.Waypoints::xs),
                    DOUBLES.fieldOf("z").forGetter(PathStore.Waypoints::zs),
                    INTS.fieldOf("floor").forGetter(PathStore.Waypoints::floors),
                    Codec.BOOL.optionalFieldOf("truncated", false).forGetter(PathStore.Waypoints::truncated))
            .apply(i, PathStore.Waypoints::new));

    /** Each field defaults to a unit that never asked, the only path an M0 save has. */
    private static final Codec<PathStore.UnitPath> UNIT_PATH = RecordCodecBuilder.create(i -> i.group(
                    WAYPOINTS
                            .optionalFieldOf("waypoints", PathStore.Waypoints.NONE)
                            .forGetter(PathStore.UnitPath::waypoints),
                    Codec.INT.optionalFieldOf("cursor", 0).forGetter(PathStore.UnitPath::cursor),
                    PathStatus.CODEC.optionalFieldOf("status", PathStatus.OK).forGetter(PathStore.UnitPath::status),
                    Codec.INT.optionalFieldOf("request", 0).forGetter(PathStore.UnitPath::request),
                    Codec.LONG
                            .optionalFieldOf("lastRequest", PathStore.NEVER)
                            .forGetter(PathStore.UnitPath::lastRequest),
                    Codec.BOOL.optionalFieldOf("repath", false).forGetter(PathStore.UnitPath::repath))
            .apply(i, PathStore.UnitPath::new));

    private static final Codec<PathRequest> REQUEST = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("id").forGetter(PathRequest::id),
                    Codec.INT.fieldOf("handle").forGetter(PathRequest::handle),
                    Codec.LONG.fieldOf("tick").forGetter(PathRequest::tick),
                    Codec.INT.fieldOf("fromNode").forGetter(PathRequest::fromNode),
                    Codec.DOUBLE.optionalFieldOf("fromX", Double.NaN).forGetter(PathRequest::fromX),
                    Codec.DOUBLE.optionalFieldOf("fromZ", Double.NaN).forGetter(PathRequest::fromZ),
                    Codec.DOUBLE.fieldOf("goalX").forGetter(PathRequest::goalX),
                    Codec.DOUBLE.fieldOf("goalZ").forGetter(PathRequest::goalZ),
                    Codec.INT.fieldOf("cap").forGetter(PathRequest::cap),
                    Codec.INT.optionalFieldOf("groupId", 0).forGetter(PathRequest::groupId),
                    Codec.BOOL.optionalFieldOf("join", false).forGetter(PathRequest::join))
            .apply(i, PathRequest::new));

    private static final Codec<MemberPath> MEMBER_PATH = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("handle").forGetter(MemberPath::handle),
                    Codec.INT.fieldOf("fromNode").forGetter(MemberPath::fromNode),
                    Codec.DOUBLE.fieldOf("fromX").forGetter(MemberPath::fromX),
                    Codec.DOUBLE.fieldOf("fromZ").forGetter(MemberPath::fromZ),
                    Codec.intRange(MemberPath.NONE, MemberPath.SEARCH)
                            .fieldOf("join")
                            .forGetter(MemberPath::join),
                    WAYPOINTS.fieldOf("waypoints").forGetter(MemberPath::waypoints))
            .apply(i, MemberPath::new));

    private static final Codec<PathResult> RESULT = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("requestId").forGetter(PathResult::requestId),
                    PathStatus.CODEC.fieldOf("status").forGetter(PathResult::status),
                    WAYPOINTS.fieldOf("waypoints").forGetter(PathResult::waypoints),
                    Codec.INT.fieldOf("expansions").forGetter(PathResult::expansions),
                    MEMBER_PATH.listOf().optionalFieldOf("members", List.of()).forGetter(PathResult::members))
            .apply(i, PathResult::new));

    private static final Codec<PathStore.Delivery> DELIVERY = RecordCodecBuilder.create(i -> i.group(
                    REQUEST.fieldOf("request").forGetter(PathStore.Delivery::request),
                    Codec.LONG.fieldOf("due").forGetter(PathStore.Delivery::due),
                    RESULT.fieldOf("result").forGetter(PathStore.Delivery::result))
            .apply(i, PathStore.Delivery::new));

    private static final Codec<PathStore.Waiting> WAITING = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("id").forGetter(PathStore.Waiting::id),
                    Codec.INT.fieldOf("handle").forGetter(PathStore.Waiting::handle),
                    Codec.INT.optionalFieldOf("group", 0).forGetter(PathStore.Waiting::group))
            .apply(i, PathStore.Waiting::new));

    private static final Codec<PathStore.Queue> QUEUE = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.optionalFieldOf("nextRequestId", 1).forGetter(PathStore.Queue::nextRequestId),
                    DELIVERY.listOf().optionalFieldOf("deliveries", List.of()).forGetter(PathStore.Queue::deliveries),
                    Codec.LONG
                            .optionalFieldOf("lastPageChange", PathStore.NEVER)
                            .forGetter(PathStore.Queue::lastPageChange),
                    WAITING.listOf().optionalFieldOf("waiting", List.of()).forGetter(PathStore.Queue::waiting))
            .apply(i, PathStore.Queue::new));

    private static final Codec<MotionStore.Ladder> LADDER = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.optionalFieldOf("detourX", 0.0).forGetter(MotionStore.Ladder::detourX),
                    Codec.DOUBLE.optionalFieldOf("detourZ", 0.0).forGetter(MotionStore.Ladder::detourZ),
                    Codec.INT.optionalFieldOf("detourTicks", 0).forGetter(MotionStore.Ladder::detourTicks),
                    Codec.INT.optionalFieldOf("ghostTicks", 0).forGetter(MotionStore.Ladder::ghostTicks))
            .apply(i, MotionStore.Ladder::new));

    /** Each field defaults to a unit that never touched terrain, the only motion an M0 save has. */
    private static final Codec<MotionStore.Extra> MOTION = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.optionalFieldOf("vx", 0.0).forGetter(MotionStore.Extra::vx),
                    Codec.DOUBLE.optionalFieldOf("vz", 0.0).forGetter(MotionStore.Extra::vz),
                    Codec.INT.optionalFieldOf("floor", MotionStore.NO_FLOOR).forGetter(MotionStore.Extra::floor),
                    Codec.BOOL.optionalFieldOf("rising", false).forGetter(MotionStore.Extra::rising),
                    Codec.DOUBLE.optionalFieldOf("aimX", 0.0).forGetter(MotionStore.Extra::aimX),
                    Codec.DOUBLE.optionalFieldOf("aimZ", 0.0).forGetter(MotionStore.Extra::aimZ),
                    Codec.DOUBLE
                            .optionalFieldOf("best", Double.POSITIVE_INFINITY)
                            .forGetter(MotionStore.Extra::best),
                    Codec.INT.optionalFieldOf("stuck", 0).forGetter(MotionStore.Extra::stuck),
                    Codec.BOOL.optionalFieldOf("latched", false).forGetter(MotionStore.Extra::latched),
                    Codec.DOUBLE.optionalFieldOf("latchX", 0.0).forGetter(MotionStore.Extra::latchX),
                    Codec.DOUBLE.optionalFieldOf("latchZ", 0.0).forGetter(MotionStore.Extra::latchZ),
                    Codec.INT.optionalFieldOf("pushed", 0).forGetter(MotionStore.Extra::pushed),
                    LADDER.optionalFieldOf("ladder", MotionStore.Ladder.NONE).forGetter(MotionStore.Extra::ladder))
            .apply(i, MotionStore.Extra::new));

    /** A leaf of the unit tree by its name; M0's {@code "idle"} and {@code "walking"} name its two leaves. */
    private static final Codec<StateId> LEAF = Codec.STRING.comapFlatMap(
            name -> {
                StateId leaf = UnitAi.leaf(name);
                return leaf != null
                        ? DataResult.success(leaf)
                        : DataResult.error(() -> "unknown unit state '" + name + "'");
            },
            StateId::name);

    private static final Codec<List<Integer>> DATA = Codec.INT
            .listOf()
            .validate(words -> words.size() == 2
                    ? DataResult.success(words)
                    : DataResult.error(() -> "a unit state holds 2 data words, not " + words.size()));

    /**
     * A unit's machine, inlined into the unit: {@code "state"} as in M0, and {@code "data"} only once a word is set, so
     * a unit with both words 0 saves exactly as before.
     */
    private static final MapCodec<AiStateStore.Saved> AI = RecordCodecBuilder.mapCodec(i -> i.group(
                    LEAF.fieldOf("state").forGetter(AiStateStore.Saved::state),
                    DATA.optionalFieldOf("data", List.of(0, 0)).forGetter(s -> List.of(s.data0(), s.data1())))
            .apply(i, (state, data) -> new AiStateStore.Saved(state, data.get(0), data.get(1))));

    private static final Codec<Integer> ORDER_KIND = Codec.STRING.comapFlatMap(
            name -> {
                Integer kind = OrderKind.of(name);
                return kind != null
                        ? DataResult.success(kind)
                        : DataResult.error(() -> "unknown order kind '" + name + "'");
            },
            OrderKind::name);

    private static final Codec<OrderQueues.Order> ORDER = RecordCodecBuilder.create(i -> i.group(
                    ORDER_KIND.fieldOf("kind").forGetter(OrderQueues.Order::kind),
                    Vec2d.CODEC.fieldOf("target").forGetter(o -> new Vec2d(o.x(), o.z())),
                    Codec.INT.optionalFieldOf("group", 0).forGetter(OrderQueues.Order::group),
                    Codec.INT.optionalFieldOf("slot", 0).forGetter(OrderQueues.Order::slot),
                    Codec.INT.optionalFieldOf("attacks", 0).forGetter(OrderQueues.Order::target))
            .apply(
                    i,
                    (kind, target, group, slot, unit) ->
                            new OrderQueues.Order(kind, target.x(), target.z(), group, slot, unit)));

    /**
     * The rest of a unit's queue, inlined into the unit beside its {@code "target"}: the current order's kind, group,
     * slot and attacked unit, and the orders after it, each written only when set, so M0's single order saves exactly
     * as before.
     */
    private static final MapCodec<OrderQueues.Saved> ORDERS = RecordCodecBuilder.mapCodec(i -> i.group(
                    ORDER_KIND.optionalFieldOf("kind", OrderKind.MOVE).forGetter(OrderQueues.Saved::kind),
                    Codec.INT.optionalFieldOf("group", 0).forGetter(OrderQueues.Saved::group),
                    Codec.INT.optionalFieldOf("slot", 0).forGetter(OrderQueues.Saved::slot),
                    Codec.INT.optionalFieldOf("attacks", 0).forGetter(OrderQueues.Saved::target),
                    ORDER.listOf().optionalFieldOf("queue", List.of()).forGetter(OrderQueues.Saved::next))
            .apply(i, OrderQueues.Saved::new));

    private static final Codec<UnitRecord> UNIT = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("handle").forGetter(UnitRecord::handle),
                    TemplateId.CODEC.fieldOf("template").forGetter(UnitRecord::template),
                    FactionSlot.CODEC.fieldOf("faction").forGetter(UnitRecord::faction),
                    Codec.DOUBLE.fieldOf("x").forGetter(UnitRecord::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(UnitRecord::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(UnitRecord::z),
                    Codec.FLOAT.fieldOf("yaw").forGetter(UnitRecord::yaw),
                    AI.forGetter(UnitRecord::ai),
                    Vec2d.CODEC.optionalFieldOf("target").forGetter(u -> Optional.ofNullable(u.target())),
                    UNIT_PATH.optionalFieldOf("path", PathStore.UnitPath.NONE).forGetter(UnitRecord::path),
                    MOTION.optionalFieldOf("motion", MotionStore.Extra.NONE).forGetter(UnitRecord::motion),
                    ORDERS.forGetter(UnitRecord::orders),
                    Codec.DOUBLE.optionalFieldOf("damage", 0.0).forGetter(UnitRecord::damage))
            .apply(
                    i,
                    (handle, template, faction, x, y, z, yaw, ai, target, path, motion, orders, damage) ->
                            new UnitRecord(
                                    handle,
                                    template,
                                    faction,
                                    x,
                                    y,
                                    z,
                                    yaw,
                                    ai,
                                    target.orElse(null),
                                    path,
                                    motion,
                                    orders,
                                    damage)));

    private static final Codec<PageRef> PAGE = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("x").forGetter(PageRef::x),
                    Codec.INT.fieldOf("z").forGetter(PageRef::z),
                    Hex64.CODEC.fieldOf("slice").forGetter(PageRef::slice))
            .apply(i, PageRef::new));

    /** A node's deposit or animal (ECO-08, -16), as two flat optional fields: a builder takes at most sixteen. */
    private record NodeLink(String template, int unit) {}

    private static final MapCodec<NodeLink> NODE_LINK = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.optionalFieldOf("template", "").forGetter(NodeLink::template),
                    Codec.INT.optionalFieldOf("unit", 0).forGetter(NodeLink::unit))
            .apply(i, NodeLink::new));

    private static final Codec<NodeStore.Saved> NODE = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("kind").forGetter(NodeStore.Saved::kind),
                    Codec.INT.fieldOf("resource").forGetter(NodeStore.Saved::resource),
                    Codec.INT.fieldOf("subtype").forGetter(NodeStore.Saved::subtype),
                    Codec.LONG.fieldOf("perBlock").forGetter(NodeStore.Saved::perBlock),
                    Codec.LONG.fieldOf("amount").forGetter(NodeStore.Saved::amount),
                    Codec.DOUBLE.fieldOf("anchorX").forGetter(NodeStore.Saved::anchorX),
                    Codec.DOUBLE.fieldOf("anchorZ").forGetter(NodeStore.Saved::anchorZ),
                    Codec.INT.fieldOf("baseY").forGetter(NodeStore.Saved::baseY),
                    Codec.LONG.listOf().fieldOf("blocks").forGetter(NodeStore.Saved::blocks),
                    Codec.INT.fieldOf("gone").forGetter(NodeStore.Saved::gone),
                    Codec.INT.listOf().fieldOf("slots").forGetter(NodeStore.Saved::slots),
                    Codec.INT.listOf().fieldOf("claims").forGetter(NodeStore.Saved::claims),
                    Codec.LONG.optionalFieldOf("most", 0L).forGetter(NodeStore.Saved::most),
                    Codec.LONG.optionalFieldOf("regrow", 0L).forGetter(NodeStore.Saved::regrow),
                    Codec.INT.optionalFieldOf("growth", 0).forGetter(NodeStore.Saved::growth),
                    NODE_LINK.forGetter(n -> new NodeLink(n.template(), n.unit())))
            .apply(
                    i,
                    (kind,
                            resource,
                            subtype,
                            perBlock,
                            amount,
                            anchorX,
                            anchorZ,
                            baseY,
                            blocks,
                            gone,
                            slots,
                            claims,
                            most,
                            regrow,
                            growth,
                            link) -> new NodeStore.Saved(
                            kind,
                            resource,
                            subtype,
                            perBlock,
                            amount,
                            anchorX,
                            anchorZ,
                            baseY,
                            blocks,
                            gone,
                            slots,
                            claims,
                            most,
                            regrow,
                            growth,
                            link.template(),
                            link.unit())));

    private static final Codec<CarryStore.Saved> CARRY = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("handle").forGetter(CarryStore.Saved::handle),
                    Codec.INT.fieldOf("resource").forGetter(CarryStore.Saved::resource),
                    Codec.INT.fieldOf("subtype").forGetter(CarryStore.Saved::subtype),
                    Codec.LONG.fieldOf("amount").forGetter(CarryStore.Saved::amount))
            .apply(i, CarryStore.Saved::new));

    private static final Codec<BuildingStore.Site> SITE = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.optionalFieldOf("work", 0L).forGetter(BuildingStore.Site::work),
                    Codec.INT.optionalFieldOf("placed", 0).forGetter(BuildingStore.Site::placed),
                    Codec.INT.listOf().optionalFieldOf("slots", List.of()).forGetter(BuildingStore.Site::slots),
                    Codec.INT.listOf().optionalFieldOf("claims", List.of()).forGetter(BuildingStore.Site::claims))
            .apply(i, BuildingStore.Site::new));

    private static final Codec<BuildingStore.Production> PRODUCTION = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.listOf().optionalFieldOf("queue", List.of()).forGetter(BuildingStore.Production::queue),
                    Codec.LONG.optionalFieldOf("trained", 0L).forGetter(BuildingStore.Production::trained),
                    Codec.BOOL.optionalFieldOf("reserved", false).forGetter(BuildingStore.Production::reserved),
                    Codec.BOOL.optionalFieldOf("waiting", false).forGetter(BuildingStore.Production::waiting),
                    Codec.INT
                            .listOf()
                            .optionalFieldOf("batches", List.of())
                            .forGetter(BuildingStore.Production::batches),
                    Codec.BOOL.optionalFieldOf("autoReseed", false).forGetter(BuildingStore.Production::autoReseed))
            .apply(i, BuildingStore.Production::new));

    private static final Codec<BuildingStore.Rally> RALLY = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.optionalFieldOf("kind", BuildingStore.RALLY_NONE).forGetter(BuildingStore.Rally::kind),
                    Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(BuildingStore.Rally::x),
                    Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(BuildingStore.Rally::z),
                    Codec.INT.optionalFieldOf("target", 0).forGetter(BuildingStore.Rally::target))
            .apply(i, BuildingStore.Rally::new));

    private static final Codec<BuildingStore.Saved> BUILDING = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("generation").forGetter(BuildingStore.Saved::generation),
                    Codec.BOOL.fieldOf("alive").forGetter(BuildingStore.Saved::alive),
                    Codec.INT.fieldOf("template").forGetter(BuildingStore.Saved::template),
                    Codec.INT.fieldOf("faction").forGetter(BuildingStore.Saved::faction),
                    Codec.INT.fieldOf("x").forGetter(BuildingStore.Saved::anchorX),
                    Codec.INT.fieldOf("z").forGetter(BuildingStore.Saved::anchorZ),
                    Codec.INT.fieldOf("rotation").forGetter(BuildingStore.Saved::rotation),
                    Codec.INT.fieldOf("baseY").forGetter(BuildingStore.Saved::baseY),
                    Codec.INT.fieldOf("state").forGetter(BuildingStore.Saved::state),
                    Codec.LONG.fieldOf("progress").forGetter(BuildingStore.Saved::progress),
                    Codec.DOUBLE.fieldOf("damage").forGetter(BuildingStore.Saved::damage),
                    SITE.optionalFieldOf("site", BuildingStore.Site.NONE).forGetter(BuildingStore.Saved::site),
                    PRODUCTION
                            .optionalFieldOf("production", BuildingStore.Production.NONE)
                            .forGetter(BuildingStore.Saved::production),
                    RALLY.optionalFieldOf("rally", BuildingStore.Rally.NONE).forGetter(BuildingStore.Saved::rally),
                    Codec.INT.optionalFieldOf("field", 0).forGetter(BuildingStore.Saved::field),
                    Codec.INT.optionalFieldOf("stage", 0).forGetter(BuildingStore.Saved::stage))
            .apply(i, BuildingStore.Saved::new));

    static final Codec<AiSlots.Saved> COMPUTER = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("faction").forGetter(AiSlots.Saved::faction),
                    Codec.STRING.fieldOf("profile").forGetter(AiSlots.Saved::profile),
                    Codec.STRING.fieldOf("script").forGetter(AiSlots.Saved::script),
                    Codec.LONG.listOf().fieldOf("memory").forGetter(AiSlots.Saved::memory))
            .apply(i, AiSlots.Saved::new));

    private static final Codec<Damage.Bonus> SHOT_BONUS = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("token").forGetter(Damage.Bonus::token),
                    Codec.FLOAT.fieldOf("multiplier").forGetter(Damage.Bonus::multiplier))
            .apply(i, Damage.Bonus::new));

    private static final Codec<Damage> SHOT_DAMAGE = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("target").forGetter(Damage::target),
                    Codec.INT.fieldOf("attacker").forGetter(Damage::attacker),
                    Codec.FLOAT.fieldOf("hack").forGetter(Damage::hack),
                    Codec.FLOAT.fieldOf("pierce").forGetter(Damage::pierce),
                    Codec.FLOAT.fieldOf("crush").forGetter(Damage::crush),
                    Codec.FLOAT.fieldOf("fire").forGetter(Damage::fire),
                    SHOT_BONUS.listOf().fieldOf("bonuses").forGetter(Damage::bonuses),
                    Codec.BOOL.fieldOf("lethal").forGetter(Damage::lethal))
            .apply(i, Damage::new));

    private static final Codec<ShotStore.Release> RELEASE = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("x").forGetter(ShotStore.Release::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(ShotStore.Release::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(ShotStore.Release::z),
                    Codec.LONG.fieldOf("tick").forGetter(ShotStore.Release::tick))
            .apply(i, ShotStore.Release::new));

    private static final Codec<ShotStore.Shot> SHOT = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("id").forGetter(ShotStore.Shot::id),
                    Codec.INT.fieldOf("shooter").forGetter(ShotStore.Shot::shooter),
                    Codec.INT.fieldOf("target").forGetter(ShotStore.Shot::target),
                    Codec.LONG.fieldOf("landTick").forGetter(ShotStore.Shot::landTick),
                    Codec.DOUBLE.fieldOf("landingX").forGetter(ShotStore.Shot::landingX),
                    Codec.DOUBLE.fieldOf("landingY").forGetter(ShotStore.Shot::landingY),
                    Codec.DOUBLE.fieldOf("landingZ").forGetter(ShotStore.Shot::landingZ),
                    SHOT_DAMAGE.fieldOf("damage").forGetter(ShotStore.Shot::damage),
                    RELEASE.optionalFieldOf("release").forGetter(shot -> Optional.ofNullable(shot.release())))
            .apply(
                    i,
                    (id, shooter, target, landTick, x, y, z, damage, release) ->
                            new ShotStore.Shot(id, shooter, target, landTick, x, y, z, damage, release.orElse(null))));

    private static final Codec<ShotStore.Snapshot> SHOTS = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("nextId").forGetter(ShotStore.Snapshot::nextId),
                    SHOT.listOf().fieldOf("rows").forGetter(ShotStore.Snapshot::rows))
            .apply(i, ShotStore.Snapshot::new));

    private static final Codec<StanceStore.Saved> STANCE = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("unit").forGetter(StanceStore.Saved::handle),
                    Codec.INT.fieldOf("stance").forGetter(StanceStore.Saved::stance),
                    Codec.DOUBLE.fieldOf("x").forGetter(StanceStore.Saved::x),
                    Codec.DOUBLE.fieldOf("z").forGetter(StanceStore.Saved::z))
            .apply(i, StanceStore.Saved::new));

    private static final Codec<GarrisonStore.Saved> GARRISON = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("unit").forGetter(GarrisonStore.Saved::unit),
                    Codec.INT.fieldOf("building").forGetter(GarrisonStore.Saved::building),
                    Codec.BOOL.optionalFieldOf("bell", false).forGetter(GarrisonStore.Saved::bell))
            .apply(i, GarrisonStore.Saved::new));

    static final Codec<MatchSnapshot.Play> PLAY = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.listOf().optionalFieldOf("outcome", List.of()).forGetter(MatchSnapshot.Play::outcome),
                    COMPUTER.listOf().optionalFieldOf("ai", List.of()).forGetter(MatchSnapshot.Play::ai),
                    Codec.LONG.optionalFieldOf("aiWake", 0L).forGetter(MatchSnapshot.Play::aiWake),
                    Codec.LONG.listOf().optionalFieldOf("alerts", List.of()).forGetter(MatchSnapshot.Play::alerts),
                    SHOTS.optionalFieldOf("shots", ShotStore.Snapshot.EMPTY).forGetter(MatchSnapshot.Play::shots),
                    STANCE.listOf().optionalFieldOf("stances", List.of()).forGetter(MatchSnapshot.Play::stances),
                    GARRISON.listOf().optionalFieldOf("garrisons", List.of()).forGetter(MatchSnapshot.Play::garrisons))
            .apply(i, MatchSnapshot.Play::new));

    private static final Codec<MatchSnapshot.TemplateBinding> BINDING = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("index").forGetter(MatchSnapshot.TemplateBinding::index),
                    TemplateId.CODEC.fieldOf("id").forGetter(MatchSnapshot.TemplateBinding::id))
            .apply(i, MatchSnapshot.TemplateBinding::new));

    private record Tail(MatchSnapshot.Play play, List<MatchSnapshot.TemplateBinding> bindings) {}

    private static final MapCodec<Tail> TAIL = RecordCodecBuilder.mapCodec(i -> i.group(
                    PLAY.optionalFieldOf("play", MatchSnapshot.Play.EMPTY).forGetter(Tail::play),
                    BINDING.listOf()
                            .optionalFieldOf("templateBindings", List.of())
                            .forGetter(Tail::bindings))
            .apply(i, Tail::new));

    /** The fields, without {@code "v"}. */
    public static final Codec<MatchSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                    MatchConfig.CODEC.fieldOf("config").forGetter(MatchSnapshot::config),
                    Codec.LONG.fieldOf("tick").forGetter(MatchSnapshot::tick),
                    Hex64.CODEC.fieldOf("templateSetHash").forGetter(MatchSnapshot::templateSetHash),
                    SLOTS.fieldOf("slots").forGetter(MatchSnapshot::slots),
                    UNIT.listOf().fieldOf("units").forGetter(MatchSnapshot::units),
                    CommandEnvelope.CODEC.listOf().fieldOf("pendingCommands").forGetter(MatchSnapshot::pendingCommands),
                    PAGE.listOf().fieldOf("pages").forGetter(MatchSnapshot::pages),
                    QUEUE.optionalFieldOf("paths", PathStore.Queue.EMPTY).forGetter(MatchSnapshot::paths),
                    Codec.INT
                            .optionalFieldOf("nextGroup", MatchState.FIRST_GROUP)
                            .forGetter(MatchSnapshot::nextGroup),
                    Codec.LONG.listOf().optionalFieldOf("treasury", List.of()).forGetter(MatchSnapshot::treasury),
                    Codec.LONG
                            .listOf()
                            .optionalFieldOf("terrainEdits", List.of())
                            .forGetter(MatchSnapshot::terrainEdits),
                    NODE.listOf().optionalFieldOf("nodes", List.of()).forGetter(MatchSnapshot::nodes),
                    BUILDING.listOf().optionalFieldOf("buildings", List.of()).forGetter(MatchSnapshot::buildings),
                    Codec.LONG.listOf().optionalFieldOf("ledger", List.of()).forGetter(MatchSnapshot::ledger),
                    CARRY.listOf().optionalFieldOf("carried", List.of()).forGetter(MatchSnapshot::carried),
                    TAIL.forGetter(s -> new Tail(s.play(), s.templateBindings())))
            .apply(
                    i,
                    (config,
                            tick,
                            hash,
                            slots,
                            units,
                            commands,
                            pages,
                            paths,
                            group,
                            treasury,
                            edits,
                            nodes,
                            buildings,
                            ledger,
                            carried,
                            tail) -> new MatchSnapshot(
                            config,
                            tick,
                            hash,
                            slots,
                            units,
                            commands,
                            pages,
                            paths,
                            group,
                            treasury,
                            edits,
                            nodes,
                            buildings,
                            ledger,
                            carried,
                            tail.play(),
                            tail.bindings())));

    private MatchSnapshotCodec() {}

    /** The snapshot with {@code "v": CURRENT}; an encoding error is a bug and throws. */
    public static <T> T encode(DynamicOps<T> ops, MatchSnapshot snapshot) {
        T encoded = CODEC.encodeStart(ops, snapshot)
                .getOrThrow(message -> new IllegalStateException("cannot encode a snapshot: " + message));
        return new Dynamic<>(ops, encoded)
                .set("v", new Dynamic<>(ops, ops.createInt(SchemaVersion.CURRENT)))
                .getValue();
    }

    /**
     * Migrates to the current version, then decodes; any problem is an error, never an exception. The codecs report
     * bad values themselves; a value a record's constructor still refuses becomes an error here too.
     */
    public static <T> DataResult<MatchSnapshot> decode(Dynamic<T> raw, Migrations migrations) {
        try {
            return migrations.migrate(raw, SchemaVersion.CURRENT).flatMap(CODEC::parse);
        } catch (RuntimeException e) {
            return DataResult.error(() -> "cannot decode the snapshot: " + e.getMessage());
        }
    }
}
