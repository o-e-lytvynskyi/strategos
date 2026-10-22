package dev.strategos.core.sim.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.EntitySlots;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.UnitRecord;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.nav.PathRequest;
import dev.strategos.core.sim.nav.PathResult;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
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
                    Codec.DOUBLE.fieldOf("goalX").forGetter(PathRequest::goalX),
                    Codec.DOUBLE.fieldOf("goalZ").forGetter(PathRequest::goalZ),
                    Codec.INT.fieldOf("cap").forGetter(PathRequest::cap),
                    Codec.INT.optionalFieldOf("groupId", 0).forGetter(PathRequest::groupId),
                    Codec.BOOL.optionalFieldOf("join", false).forGetter(PathRequest::join))
            .apply(i, PathRequest::new));

    private static final Codec<PathResult> RESULT = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("requestId").forGetter(PathResult::requestId),
                    PathStatus.CODEC.fieldOf("status").forGetter(PathResult::status),
                    WAYPOINTS.fieldOf("waypoints").forGetter(PathResult::waypoints),
                    Codec.INT.fieldOf("expansions").forGetter(PathResult::expansions))
            .apply(i, PathResult::new));

    private static final Codec<PathStore.Delivery> DELIVERY = RecordCodecBuilder.create(i -> i.group(
                    REQUEST.fieldOf("request").forGetter(PathStore.Delivery::request),
                    Codec.LONG.fieldOf("due").forGetter(PathStore.Delivery::due),
                    RESULT.fieldOf("result").forGetter(PathStore.Delivery::result))
            .apply(i, PathStore.Delivery::new));

    private static final Codec<PathStore.Queue> QUEUE = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.optionalFieldOf("nextRequestId", 1).forGetter(PathStore.Queue::nextRequestId),
                    DELIVERY.listOf().optionalFieldOf("deliveries", List.of()).forGetter(PathStore.Queue::deliveries))
            .apply(i, PathStore.Queue::new));

    private static final Codec<UnitRecord> UNIT = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("handle").forGetter(UnitRecord::handle),
                    TemplateId.CODEC.fieldOf("template").forGetter(UnitRecord::template),
                    FactionSlot.CODEC.fieldOf("faction").forGetter(UnitRecord::faction),
                    Codec.DOUBLE.fieldOf("x").forGetter(UnitRecord::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(UnitRecord::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(UnitRecord::z),
                    Codec.FLOAT.fieldOf("yaw").forGetter(UnitRecord::yaw),
                    UnitState.CODEC.fieldOf("state").forGetter(UnitRecord::state),
                    Vec2d.CODEC.optionalFieldOf("target").forGetter(u -> Optional.ofNullable(u.target())),
                    UNIT_PATH.optionalFieldOf("path", PathStore.UnitPath.NONE).forGetter(UnitRecord::path))
            .apply(
                    i,
                    (handle, template, faction, x, y, z, yaw, state, target, path) ->
                            new UnitRecord(handle, template, faction, x, y, z, yaw, state, target.orElse(null), path)));

    private static final Codec<PageRef> PAGE = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("x").forGetter(PageRef::x),
                    Codec.INT.fieldOf("z").forGetter(PageRef::z),
                    Hex64.CODEC.fieldOf("slice").forGetter(PageRef::slice))
            .apply(i, PageRef::new));

    /** The fields, without {@code "v"}. */
    public static final Codec<MatchSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                    MatchConfig.CODEC.fieldOf("config").forGetter(MatchSnapshot::config),
                    Codec.LONG.fieldOf("tick").forGetter(MatchSnapshot::tick),
                    Hex64.CODEC.fieldOf("templateSetHash").forGetter(MatchSnapshot::templateSetHash),
                    SLOTS.fieldOf("slots").forGetter(MatchSnapshot::slots),
                    UNIT.listOf().fieldOf("units").forGetter(MatchSnapshot::units),
                    CommandEnvelope.CODEC.listOf().fieldOf("pendingCommands").forGetter(MatchSnapshot::pendingCommands),
                    PAGE.listOf().fieldOf("pages").forGetter(MatchSnapshot::pages),
                    QUEUE.optionalFieldOf("paths", PathStore.Queue.EMPTY).forGetter(MatchSnapshot::paths))
            .apply(i, MatchSnapshot::new));

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
