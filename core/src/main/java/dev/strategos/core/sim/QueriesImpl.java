package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.econ.ResourceDef;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.core.sim.unitai.ActivityTier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@link MatchQueries} over a match's state. */
final class QueriesImpl implements MatchQueries {
    private final MatchState state;
    private final PathService paths;

    QueriesImpl(MatchState state, PathService paths) {
        this.state = state;
        this.paths = paths;
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
                    b.field(handle) == 0 ? 0 : state.nodes().ripening(b.field(handle))));
        }
        return out;
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
                    n.freeSlots(id),
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
}
