package dev.strategos.core.sim.prod;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.econ.Dropsites;
import dev.strategos.core.sim.econ.Fields;
import dev.strategos.core.sim.order.OrderKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Training (PROD-01, ECO-06, PROD-03, 04 §11.5): a finished building's queue trains its head entry, which takes its
 * population when it starts, or waits with a "housing needed" notice, told once, keeping its progress; trained for its
 * {@code Cost/time}, the unit appears at the cell outside the footprint nearest the blueprint's door and takes the
 * building's rally orders. A batch (PROD-02) is one entry of up to five: their population taken together, trained for
 * the unit's time × count^e (e the building's {@code Trainer/batchExponent}, 0.8 by default), rounded to ticks once,
 * all of them appearing together. A building's housing counts while it stands finished.
 */
@CoreApi
public final class Production {
    /** The token path of what a building trains. */
    public static final String TRAINER = "Trainer/units";

    private Production() {}

    /** Whether building {@code handle} trains template {@code template}: its {@code Trainer/units} names it. */
    public static boolean trains(MatchState s, int handle, int template) {
        BuildingStore b = s.buildings();
        int path = s.stats().tokenPath(TRAINER);
        List<String> units = path < 0 ? null : s.stats().blockTokens(b.template(handle), b.faction(handle), path);
        return units != null
                && units.contains(s.templates().byIndex(template).id().value());
    }

    /** The path of a building's batch exponent (PROD-02). */
    public static final String BATCH_EXPONENT = "Trainer/batchExponent";

    /** The default batch exponent: five train in 5^0.8, about 3.6, units' time. */
    public static final double DEFAULT_BATCH_EXPONENT = 0.8;

    /** The ticks an entry of {@code template} trains for: its {@code Cost/time} seconds, at least one tick. */
    public static long ticks(MatchState s, int template, int faction) {
        int path = s.stats().path(Construction.TIME);
        double seconds = path < 0 ? Double.NaN : s.stats().blockValue(template, faction, path);
        return Double.isFinite(seconds) && seconds > 0 ? Math.max(1, Math.round(seconds * 20)) : 1;
    }

    /**
     * The ticks building {@code handle}'s entry of {@code count} of {@code template} trains for (PROD-02): the unit's
     * seconds × 20 × count^e, rounded once; a single entry's are {@link #ticks}.
     */
    public static long ticks(MatchState s, int handle, int template, int count) {
        BuildingStore b = s.buildings();
        int faction = b.faction(handle);
        if (count <= 1) {
            return ticks(s, template, faction);
        }
        int path = s.stats().path(Construction.TIME);
        double seconds = path < 0 ? Double.NaN : s.stats().blockValue(template, faction, path);
        if (!(Double.isFinite(seconds) && seconds > 0)) {
            return 1;
        }
        return Math.max(1, Math.round(seconds * 20 * DetMath.pow(count, exponent(s, handle))));
    }

    /** Building {@code handle}'s batch exponent: its {@code Trainer/batchExponent}, else {@value #DEFAULT_BATCH_EXPONENT}. */
    static double exponent(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int path = s.stats().path(BATCH_EXPONENT);
        double v = path < 0 ? Double.NaN : s.stats().blockValue(b.template(handle), b.faction(handle), path);
        return Double.isFinite(v) && v > 0 && v <= 1 ? v : DEFAULT_BATCH_EXPONENT;
    }

    /** What building {@code handle} houses: its {@code Housing/capacity}, 0 for none. */
    public static int housing(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int path = s.stats().path(Population.HOUSING);
        double v = path < 0 ? Double.NaN : s.stats().blockValue(b.template(handle), b.faction(handle), path);
        return Double.isFinite(v) && v > 0 ? (int) Math.round(v) : 0;
    }

    /** One tick of building {@code handle}'s queue. */
    public static void step(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        if (b.state(handle) != BuildingStore.COMPLETE || b.queueSize(handle) == 0) {
            return;
        }
        int faction = b.faction(handle);
        int template = b.queue(handle)[0];
        int count = b.batch(handle, 0);
        Population pop = s.population();
        if (!b.reserved(handle)) {
            int need = s.templatePopulation(template, faction) * count;
            if (!pop.fits(faction, need)) {
                if (!b.waiting(handle)) {
                    b.setWaiting(handle, true);
                    s.outputs().add(new Notice(new FactionSlot(faction), NoticeKind.HOUSING_NEEDED, handle, ""));
                }
                return; // it keeps its progress
            }
            pop.add(faction, need);
            b.setReserved(handle, true);
            b.setWaiting(handle, false);
        }
        long done = b.trained(handle) + 1;
        b.setTrained(handle, done);
        if (done < ticks(s, handle, template, count)) {
            return;
        }
        int[] door = door(s, handle);
        if (door == null) {
            return; // walled in: it waits at the door for ground to free
        }
        pop.remove(faction, s.templatePopulation(template, faction) * count); // the reservation becomes the units' own
        b.dequeue(handle, 0);
        double floor = Placing.floor(s.surfaces(), s.config().bounds(), door[0], door[1]);
        double y = Double.isNaN(floor) ? s.config().bounds().flatFloorY() : floor;
        for (int n = 0; n < count; n++) { // a batch steps out together, the crowd spreading it
            int unit = s.spawnUnit(template, faction, door[0] + 0.5, y, door[1] + 0.5, 0f);
            rally(s, handle, unit);
        }
    }

    /** The free cell outside the footprint nearest the blueprint's door, or its middle without one; null for none. */
    static int @Nullable [] door(MatchState s, int handle) {
        Blueprint bp = s.blueprintOf(handle);
        Footprint at = s.footprintOf(handle);
        Blueprint.Marker m = bp.marker("door");
        if (m == null) {
            m = bp.marker("spawn");
        }
        int cx = m == null ? at.anchorX() + at.spanX() / 2 : at.worldX(m.x(), m.z());
        int cz = m == null ? at.anchorZ() + at.spanZ() / 2 : at.worldZ(m.x(), m.z());
        return Construction.nearestFree(s, at, cx, cz);
    }

    /** New unit {@code unit} takes building {@code handle}'s rally orders: walk there, gather there, or go to it. */
    static void rally(MatchState s, int handle, int unit) {
        BuildingStore b = s.buildings();
        int i = Handle.index(unit);
        double x = b.rallyX(handle);
        double z = b.rallyZ(handle);
        int target = b.rallyTarget(handle);
        switch (b.rallyKind(handle)) {
            case BuildingStore.RALLY_POINT -> s.orders().replace(i, OrderKind.MOVE, x, z, 0, 0);
            case BuildingStore.RALLY_NODE -> {
                if (target >= 1
                        && target <= s.nodes().count()
                        && s.gatherRates()
                                .gathers(
                                        s.stats(),
                                        i,
                                        s.nodes().resource(target),
                                        s.nodes().subtype(target))) {
                    s.orders().replace(i, OrderKind.GATHER, x, z, 0, 0, target);
                } else {
                    s.orders().replace(i, OrderKind.MOVE, x, z, 0, 0);
                }
            }
            case BuildingStore.RALLY_BUILDING -> {
                if (!b.alive(target)) {
                    return;
                }
                double[] edge = Dropsites.dropPoint(
                        s.footprintOf(target), s.motion().x(i), s.motion().z(i));
                MatchBounds bounds = s.config().bounds();
                s.orders()
                        .replace(
                                i,
                                OrderKind.MOVE,
                                Math.max(0.5, Math.min(bounds.sizeX() - 0.5, edge[0])),
                                Math.max(0.5, Math.min(bounds.sizeZ() - 0.5, edge[1])),
                                0,
                                0);
            }
            default -> {
                return; // no rally point: it stands at the door
            }
        }
        s.bus().publish(new OrderReceived(unit));
    }

    /**
     * Building {@code handle} now stands finished: its housing counts, a field starts to ripen, and a farmstead sows its
     * reach by itself until its owner turns that off (ECO-17).
     */
    public static void finished(MatchState s, int handle) {
        s.population().house(s.buildings().faction(handle), housing(s, handle));
        if (Fields.reach(s, handle) > 0) {
            s.buildings().setAutoReseed(handle, true);
        }
        if (Fields.isField(s, s.buildings().template(handle), s.buildings().faction(handle))) {
            Fields.create(s, handle); // a field's crop starts to ripen (ECO-09)
        }
    }

    /**
     * Building {@code handle} goes: its housing no longer counts, its queue is refunded and its head's reservation
     * given back.
     */
    public static void lost(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int faction = b.faction(handle);
        if (b.state(handle) == BuildingStore.COMPLETE) {
            s.population().unhouse(faction, housing(s, handle));
        }
        Fields.lost(s, handle);
        while (b.queueSize(handle) > 0) {
            cancel(s, handle, b.queueSize(handle) - 1);
        }
    }

    /** Entry {@code index} of building {@code handle}'s queue is cancelled: its cost refunded, its reservation freed. */
    public static void cancel(MatchState s, int handle, int index) {
        BuildingStore b = s.buildings();
        int faction = b.faction(handle);
        int count = b.batch(handle, index);
        if (index == 0 && b.reserved(handle)) {
            s.population().remove(faction, s.templatePopulation(b.queue(handle)[0], faction) * count);
        }
        int template = b.dequeue(handle, index);
        long[] cost = s.costs().of(s.stats(), template, faction);
        for (int r = 0; r < cost.length; r++) {
            cost[r] = Math.multiplyExact(cost[r], count);
        }
        s.treasury().refund(faction, cost);
    }

    /** Counts every faction's population afresh from the state: a restored match's counters. */
    public static void recount(MatchState s) {
        Population pop = s.population();
        pop.reset();
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i) && s.identity().faction(i) != s.gaia()) { // Gaia has no population (ECO-16)
                pop.add(s.identity().faction(i), s.unitPopulation(i));
            }
        }
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            if (b.state(handle) == BuildingStore.COMPLETE) {
                pop.house(b.faction(handle), housing(s, handle));
            }
            if (b.queueSize(handle) > 0 && b.reserved(handle)) {
                pop.add(
                        b.faction(handle),
                        s.templatePopulation(b.queue(handle)[0], b.faction(handle)) * b.batch(handle, 0));
            }
        }
    }
}
