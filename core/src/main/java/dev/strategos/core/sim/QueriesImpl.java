package dev.strategos.core.sim;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FactionLook;
import dev.strategos.core.protocol.MatchResults;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.econ.GatherSlots;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.econ.ResourceDef;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.core.sim.stats.StatService;
import dev.strategos.core.sim.unitai.ActivityTier;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.core.sim.vision.VisionSystem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@link MatchQueries} over a match's state. */
final class QueriesImpl implements MatchQueries {
    private final MatchState state;
    private @Nullable List<FactionLook> looks; // factionLooks' last answer, valid while the stats' versions hold
    private long looksVersion;
    private final PathService paths;

    QueriesImpl(MatchState state, PathService paths) {
        this.state = state;
        this.paths = paths;
    }

    @Override
    public @Nullable String templateSwapProblem(TemplateSet next) {
        return state.templateSwapProblem(next);
    }

    @Override
    public List<BuildingView> buildings() {
        BuildingStore b = state.buildings();
        List<BuildingView> out = new ArrayList<>();
        int dropsites = state.stats().tokenPath("Dropsite/resources");
        int housing = state.stats().path("Housing/capacity");
        for (int handle : b.handles()) {
            int template = b.template(handle);
            String drops = dropsites < 0 ? null : state.templates().text(template, dropsites);
            double house = housing < 0 ? Double.NaN : state.stats().blockValue(template, b.faction(handle), housing);
            double max = state.buildingMaxHp(handle);
            out.add(new BuildingView(
                    handle,
                    state.templates().byIndex(template).id(),
                    b.faction(handle),
                    state.footprintOf(handle),
                    b.baseY(handle),
                    b.state(handle),
                    b.progress(handle),
                    max - b.damage(handle),
                    max,
                    drops == null || drops.isBlank()
                            ? List.of()
                            : List.of(drops.trim().split("\\s+")),
                    Double.isFinite(house) ? house : 0,
                    java.util.Arrays.stream(b.queue(handle)).boxed().toList(),
                    trainedThousandths(handle),
                    b.rallyKind(handle),
                    b.rallyX(handle),
                    b.rallyZ(handle),
                    b.field(handle) == 0 ? -1 : state.nodes().amount(b.field(handle)),
                    b.field(handle) == 0 ? 0 : state.nodes().ripening(b.field(handle)),
                    b.autoReseed(handle),
                    java.util.stream.IntStream.range(0, b.queueSize(handle))
                            .mapToObj(k -> b.batch(handle, k))
                            .toList()));
        }
        return out;
    }

    @Override
    public List<TechId> researched(FactionSlot faction) {
        return faction.value() < state.config().factions() ? state.progress().of(faction.value()) : List.of();
    }

    @Override
    public @Nullable TechId techOf(int sourceId) {
        return state.progress().techOf(sourceId);
    }

    @Override
    public List<NodeView> nodes() {
        NodeStore n = state.nodes();
        EconomyRules economy = state.config().rules().economy();
        List<NodeView> out = new ArrayList<>(n.count());
        for (int id = 1; id <= n.count(); id++) {
            ResourceDef r = economy.resources().get(n.resource(id));
            out.add(new NodeView(
                    id,
                    n.kind(id),
                    r.id(),
                    r.subtypes().get(n.subtype(id)),
                    n.amount(id),
                    n.anchorX(id),
                    n.anchorZ(id),
                    n.blocks(id).length,
                    n.gone(id),
                    n.slotCount(id),
                    n.freeSlots(id, GatherSlots.standable(state, id)),
                    n.most(id),
                    n.ripening(id),
                    n.gone(id) < n.blocks(id).length ? NodeStore.x(n.blocks(id)[n.gone(id)]) : -1,
                    n.gone(id) < n.blocks(id).length ? NodeStore.y(n.blocks(id)[n.gone(id)]) : -1,
                    n.gone(id) < n.blocks(id).length ? NodeStore.z(n.blocks(id)[n.gone(id)]) : -1,
                    n.template(id)));
        }
        return out;
    }

    /** The head entry's progress, in thousandths of its training. */
    private long trainedThousandths(int handle) {
        BuildingStore b = state.buildings();
        if (b.queueSize(handle) == 0) {
            return 0;
        }
        long ticks =
                dev.strategos.core.sim.prod.Production.ticks(state, handle, b.queue(handle)[0], b.batch(handle, 0));
        return Math.min(BuildingStore.FULL, b.trained(handle) * BuildingStore.FULL / ticks);
    }

    @Override
    public int populationUsed(FactionSlot faction) {
        return state.population().used(faction.value());
    }

    @Override
    public int populationCap(FactionSlot faction) {
        return state.population().cap(faction.value());
    }

    @Override
    public int[] workers(FactionSlot faction) {
        int resources = state.config().rules().economy().size();
        int[] out = new int[resources + 1];
        for (int i = 0; i < state.entities().highWater(); i++) {
            if (!state.entities().aliveAt(i)
                    || state.identity().faction(i) != faction.value()
                    || !state.stats().canGather(i)) {
                continue;
            }
            int node = state.orders().kind(i) == dev.strategos.core.sim.order.OrderKind.GATHER
                    ? state.orders().target(i)
                    : 0;
            if (node >= 1 && node <= state.nodes().count()) {
                out[state.nodes().resource(node)]++;
            } else if (state.ai().state(i) == UnitState.IDLE) {
                out[resources]++;
            }
        }
        return out;
    }

    @Override
    public FactionTreasury treasury(FactionSlot faction) {
        return state.treasury().view(faction.value());
    }

    @Override
    public @Nullable Stance explicitStance(int handle) {
        return state.entities().isAlive(handle) ? state.stances().explicit(Handle.index(handle)) : null;
    }

    @Override
    public int carriedResource(int handle) {
        return state.entities().isAlive(handle) ? state.carry().resource(Handle.index(handle)) : -1;
    }

    @Override
    public long carriedAmount(int handle) {
        return state.entities().isAlive(handle) ? state.carry().amount(Handle.index(handle)) : 0;
    }

    @Override
    public double hitPoints(int handle) {
        return state.entities().isAlive(handle)
                ? state.stats().maxHealth(Handle.index(handle)) - state.health().damage(Handle.index(handle))
                : Double.NaN;
    }

    @Override
    public double maxHitPoints(int handle) {
        return state.entities().isAlive(handle) ? state.stats().maxHealth(Handle.index(handle)) : Double.NaN;
    }

    @Override
    public long tick() {
        return state.tick();
    }

    @Override
    public MatchConfig config() {
        return state.config();
    }

    @Override
    public TemplateSet templates() {
        return state.templates();
    }

    @Override
    public int unitCount() {
        return state.entities().aliveCount();
    }

    @Override
    public boolean isAlive(int handle) {
        return state.entities().isAlive(handle);
    }

    @Override
    public @Nullable UnitView unit(int handle) {
        if (!state.entities().isAlive(handle)) {
            return null;
        }
        int i = Handle.index(handle);
        int templateIndex = state.identity().templateIndex(i);
        OrderQueues orders = state.orders();
        MotionStore motion = state.motion();
        return new UnitView(
                handle,
                state.templates().byIndex(templateIndex).id(),
                templateIndex,
                new FactionSlot(state.identity().faction(i)),
                motion.x(i),
                motion.y(i),
                motion.z(i),
                motion.yaw(i),
                state.ai().state(i),
                orders.kind(i) != OrderKind.NONE ? new Vec2d(orders.targetX(i), orders.targetZ(i)) : null);
    }

    @Override
    public int[] unitHandles() {
        return handles(-1);
    }

    @Override
    public int[] unitHandlesOf(FactionSlot faction) {
        return handles(faction.value());
    }

    @Override
    public SurfaceGrid surfaces() {
        return state.surfaces();
    }

    @Override
    public long navigationBytes() {
        return state.navigationBytes();
    }

    @Override
    public List<PageRef> pageRefs() {
        return state.pageRefs();
    }

    @Override
    public PathStore.@Nullable UnitPath path(int handle) {
        return state.entities().isAlive(handle) ? state.paths().unitPath(Handle.index(handle)) : null;
    }

    @Override
    public long[] ladderRungs() {
        return state.rungs();
    }

    @Override
    public ActivityTier tier(int handle) {
        if (!state.entities().isAlive(handle)) {
            return ActivityTier.IDLE;
        }
        int i = Handle.index(handle);
        return ActivityTier.of(state.ai().state(i), state.tiers().recent(i, state.tick()));
    }

    @Override
    public int[] tierCounts() {
        int[] counts = new int[ActivityTier.values().length];
        EntityStore entities = state.entities();
        for (int i = 0; i < entities.highWater(); i++) {
            if (entities.aliveAt(i)) {
                counts[
                        ActivityTier.of(state.ai().state(i), state.tiers().recent(i, state.tick()))
                                .code()]++;
            }
        }
        return counts;
    }

    @Override
    public int[] finishedPathJobs(long tick) {
        return paths.finished(state, tick);
    }

    @Override
    public List<OrderQueues.Order> orders(int handle) {
        if (!state.entities().isAlive(handle)) {
            return List.of();
        }
        int i = Handle.index(handle);
        OrderQueues orders = state.orders();
        List<OrderQueues.Order> out = new ArrayList<>(orders.size(i));
        for (int k = 0; k < orders.size(i); k++) {
            out.add(orders.order(i, k));
        }
        return out;
    }

    @Override
    public MotionStore.@Nullable Extra motion(int handle) {
        return state.entities().isAlive(handle) ? state.motion().extra(Handle.index(handle)) : null;
    }

    @Override
    public @Nullable StatBreakdown stats(int handle, String path) {
        return state.entities().isAlive(handle) ? state.stats().breakdown(Handle.index(handle), path) : null;
    }

    @Override
    public List<FactionLook> factionLooks() {
        StatService stats = state.stats();
        long version = 0;
        for (int f = 0; f < state.config().factions(); f++) {
            version += stats.version(f);
        }
        if (looks != null && version == looksVersion) {
            return looks; // no faction's modifiers moved since
        }
        looksVersion = version;
        looks = computeLooks(stats);
        return looks;
    }

    private List<FactionLook> computeLooks(StatService stats) {
        int weapon = stats.path(CorePaths.LOOK_WEAPON);
        int armour = stats.path(CorePaths.LOOK_ARMOUR);
        if (weapon < 0 && armour < 0) {
            return List.of();
        }
        int factions = state.config().factions();
        int[][] tiers = new int[factions][2];
        for (int t = 0; t < state.templates().indexCount(); t++) {
            if (!state.templates().has(t)) {
                continue;
            }
            for (int f = 0; f < factions; f++) {
                tiers[f][0] = Math.max(tiers[f][0], tier(stats, t, f, weapon));
                tiers[f][1] = Math.max(tiers[f][1], tier(stats, t, f, armour));
            }
        }
        var out = new ArrayList<FactionLook>(factions);
        for (int[] pair : tiers) {
            out.add(new FactionLook(pair[0], pair[1]));
        }
        return List.copyOf(out);
    }

    /** A faction's tier of a template at a value path: its block's value, 0 for no path or a template without it. */
    private static int tier(StatService stats, int template, int faction, int path) {
        if (path < 0) {
            return 0;
        }
        double v = stats.blockValue(template, faction, path);
        return v > 0 ? (int) Math.min(v, CorePaths.MAX_LOOK) : 0;
    }

    @Override
    public @Nullable MatchResults matchResults() {
        Outcome o = state.outcome();
        if (!o.ended()) {
            return null;
        }
        int factions = state.config().factions();
        MatchStats ms = state.matchStats();
        var perFaction = new ArrayList<MatchResults.FactionStats>(factions);
        for (int f = 0; f < factions; f++) {
            FactionTreasury t = state.treasury().view(f);
            List<Long> gathered = t.gathered();
            int maxPhase = 0;
            for (int p = 0; p < 8; p++) {
                if (ms.phaseTick(f, p) >= 0) {
                    maxPhase = p + 1;
                }
            }
            var phases = new ArrayList<Long>(maxPhase);
            for (int p = 0; p < maxPhase; p++) {
                phases.add(ms.phaseTick(f, p));
            }
            perFaction.add(new MatchResults.FactionStats(
                    ms.unitsTrained(f), ms.unitsLost(f), ms.buildingsBuilt(f), ms.buildingsLost(f), gathered, phases));
        }
        return new MatchResults(o.winner(), o.endedAt(), perFaction);
    }

    @Override
    public PathService.Stats pathStats() {
        return paths.stats(state);
    }

    /** The live handles, of one faction or of all when {@code faction} is -1, sorted. */
    private int[] handles(int faction) {
        EntityStore entities = state.entities();
        int[] handles = new int[entities.aliveCount()];
        int n = 0;
        for (int i = 0; i < entities.highWater(); i++) {
            if (entities.aliveAt(i) && (faction < 0 || state.identity().faction(i) == faction)) {
                handles[n++] = entities.handleAt(i);
            }
        }
        int[] result = Arrays.copyOf(handles, n);
        Arrays.sort(result);
        return result;
    }

    @Override
    public List<WorldEffect> worldClaims() {
        return WorldClaims.of(state);
    }

    @Override
    public @org.jspecify.annotations.Nullable String placementRefusal(int template, int x, int z, int faction) {
        var b = state.templates().blueprint(template);
        if (b == null) {
            return "UNSUPPORTED";
        }
        var at = dev.strategos.core.content.Footprint.of(b, x, z, 0);
        var failed = dev.strategos.core.content.PlacementRules.check(
                        dev.strategos.core.sim.build.Placing.world(state, faction),
                        b,
                        at,
                        faction,
                        dev.strategos.core.sim.build.Placing.params(state, template, faction))
                .failed();
        return failed == null || failed == dev.strategos.core.content.PlacementRules.Rule.AFFORDABLE
                ? null
                : failed.name();
    }

    @Override
    public double floorAt(int x, int z) {
        return dev.strategos.core.sim.build.Placing.floor(
                state.surfaces(), state.config().bounds(), x, z);
    }

    @Override
    public boolean footprintAt(int x, int z) {
        return state.footprintAt(x, z);
    }

    @Override
    public dev.strategos.core.sim.combat.CombatTimes.Stats combatTimes() {
        return state.combatTimes().stats();
    }

    @Override
    public dev.strategos.core.sim.econ.EconomyTimes.Stats economyTimes() {
        return state.economyTimes().stats();
    }

    @Override
    public List<StartSlot> startPositions() {
        return state.startPositions();
    }

    @Override
    public boolean visibleUnit(int faction, int handle) {
        if (seesAll(faction)) {
            return true;
        }
        VisionSystem vision = state.visionSystem();
        return vision != null && vision.visibility(faction).isVisible(handle);
    }

    @Override
    public boolean visibleBuilding(int faction, BuildingView building) {
        if (seesAll(faction)) {
            return true;
        }
        VisionSystem vision = state.visionSystem();
        if (vision == null || faction < 0 || faction >= state.config().factions()) {
            return true;
        }
        VisionGrid grid = vision.grid(faction);
        Footprint f = building.footprint();
        for (int x = f.anchorX(); x < f.anchorX() + f.spanX(); x++) {
            for (int z = f.anchorZ(); z < f.anchorZ() + f.spanZ(); z++) {
                if (grid.count(VisionGrid.cellOf(x), VisionGrid.cellOf(z)) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Revealed map, AI omniscience, or a faction outside the player set: everything is seen. */
    private boolean seesAll(int faction) {
        PlayRules play = state.config().rules().play();
        return play.revealMap()
                || play.aiVision()
                || state.visionSystem() == null
                || faction < 0
                || faction >= state.config().factions();
    }
}
