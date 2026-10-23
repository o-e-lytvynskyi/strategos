package dev.strategos.core.sim.prod;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.CoreApi;
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
 * building's rally orders. A building's housing counts while it stands finished.
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

    /** The ticks an entry of {@code template} trains for: its {@code Cost/time} seconds, at least one tick. */
    public static long ticks(MatchState s, int template, int faction) {
        int path = s.stats().path(Construction.TIME);
        double seconds = path < 0 ? Double.NaN : s.stats().blockValue(template, faction, path);
        return Double.isFinite(seconds) && seconds > 0 ? Math.max(1, Math.round(seconds * 20)) : 1;
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
        Population pop = s.population();
        if (!b.reserved(handle)) {
            int need = s.templatePopulation(template, faction);
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
        if (done < ticks(s, template, faction)) {
            return;
        }
        int[] door = door(s, handle);
        if (door == null) {
            return; // walled in: it waits at the door for ground to free
        }
        pop.remove(faction, s.templatePopulation(template, faction)); // the reservation becomes the unit's own
        b.dequeue(handle, 0);
        double floor = Placing.floor(s.surfaces(), s.config().bounds(), door[0], door[1]);
        double y = Double.isNaN(floor) ? s.config().bounds().flatFloorY() : floor;
        int unit = s.spawnUnit(template, faction, door[0] + 0.5, y, door[1] + 0.5, 0f);
        rally(s, handle, unit);
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

    /** Building {@code handle} now stands finished: its housing counts, and a field starts to ripen. */
    public static void finished(MatchState s, int handle) {
        s.population().house(s.buildings().faction(handle), housing(s, handle));
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
        if (index == 0 && b.reserved(handle)) {
            s.population().remove(faction, s.templatePopulation(b.queue(handle)[0], faction));
        }
        int template = b.dequeue(handle, index);
        s.treasury().refund(faction, s.costs().of(s.stats(), template, faction));
    }

    /** Counts every faction's population afresh from the state: a restored match's counters. */
    public static void recount(MatchState s) {
        Population pop = s.population();
        pop.reset();
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i)) {
                pop.add(s.identity().faction(i), s.unitPopulation(i));
            }
        }
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            if (b.state(handle) == BuildingStore.COMPLETE) {
                pop.house(b.faction(handle), housing(s, handle));
            }
            if (b.queueSize(handle) > 0 && b.reserved(handle)) {
                pop.add(b.faction(handle), s.templatePopulation(b.queue(handle)[0], b.faction(handle)));
            }
        }
    }
}
