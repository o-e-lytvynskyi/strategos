package dev.strategos.core.sim.garrison;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.BellRung;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import java.util.ArrayList;
import java.util.List;

/**
 * The town bell (BLD-16, 04 §7.4): ringing it puts a garrison order before the orders of every own worker in reach, to
 * the nearest holder with room, marked with {@link Garrisons#BELL_SLOT}; the all clear drops those orders from the
 * queues they stand in and lets out the units they put inside, each back to the order that waited behind.
 */
public final class Bell {
    /** How far from the ringing building a worker hears the bell, in blocks, measured to the building's middle. */
    public static final double REACH = 64;

    /** The class of unit the bell calls in. */
    public static final String WORKER = "Worker";

    private Bell() {}

    /** What a ring did: workers sent in, and workers it could not (no room, no door, a full queue). */
    public record Rung(int sent, int refused) {}

    /**
     * Rings the bell at finished holder {@code building}: each own worker within {@link #REACH} that is not inside, not
     * walking into a building and not already called gets a bell order before its others, to the nearest holder of its
     * faction with room (counting those already walking there), the lower handle on a tie. Workers go in ascending
     * order of index. The ring sounds as {@link BellRung} at the building's middle.
     */
    public static Rung ring(MatchState s, int building) {
        int faction = s.buildings().faction(building);
        Footprint at = s.footprintOf(building);
        double cx = middleX(at);
        double cz = middleZ(at);
        s.outputs().add(new BellRung((int) Math.floor(cx), s.buildings().baseY(building) + 2, (int) Math.floor(cz)));

        List<Holder> holders = holders(s, faction);
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i)
                    && s.identity().faction(i) == faction
                    && s.orders().kind(i) == OrderKind.GARRISON) {
                for (Holder h : holders) {
                    if (h.handle == s.orders().target(i)) {
                        h.room--; // on its way there
                    }
                }
            }
        }

        int sent = 0;
        int refused = 0;
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (!s.entities().aliveAt(i)
                    || s.identity().faction(i) != faction
                    || !s.stats().classes(i).contains(WORKER)
                    || s.garrison().inside(i)
                    || walkingIn(s, i)) {
                continue;
            }
            double dx = s.motion().x(i) - cx;
            double dz = s.motion().z(i) - cz;
            if (dx * dx + dz * dz > REACH * REACH) {
                continue;
            }
            Holder best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (Holder h : holders) {
                double d = (s.motion().x(i) - h.x) * (s.motion().x(i) - h.x)
                        + (s.motion().z(i) - h.z) * (s.motion().z(i) - h.z);
                if (h.room > 0 && d < bestD && Garrisons.admits(s, i, h.handle)) {
                    best = h;
                    bestD = d;
                }
            }
            int handle = s.entities().handleAt(i);
            if (best == null
                    || !s.orders()
                            .pushFront(
                                    i,
                                    OrderKind.GARRISON,
                                    best.doorX,
                                    best.doorZ,
                                    0,
                                    Garrisons.BELL_SLOT,
                                    best.handle)) {
                refused++;
                continue;
            }
            best.room--;
            sent++;
            s.bus().publish(new OrderReceived(handle));
        }
        return new Rung(sent, refused);
    }

    /**
     * The all clear for faction {@code faction}: the bell's orders still waiting or being walked go from their queues,
     * and everyone the bell called in comes out at the door of the building it is in, in the order they went in. Units
     * garrisoned by a command of their own stay. Returns how many units were affected.
     */
    public static int allClear(MatchState s, int faction) {
        int affected = 0;
        for (int building : s.buildings().handles()) {
            if (s.buildings().faction(building) != faction) {
                continue;
            }
            int[] inside = s.garrison().occupants(building);
            int[] called = new int[inside.length];
            int n = 0;
            for (int unit : inside) {
                if (s.garrison().belled(Handle.index(unit))) {
                    called[n++] = unit;
                }
            }
            if (n > 0) {
                Garrisons.release(s, building, java.util.Arrays.copyOf(called, n));
                affected += n;
            }
        }
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i)
                    && s.identity().faction(i) == faction
                    && !s.garrison().inside(i)) {
                affected += drop(s, i);
            }
        }
        return affected;
    }

    /**
     * Takes the bell's orders out of unit {@code i}'s queue, the others keeping their places. When the current order was
     * one, the unit takes up the order behind it, or goes idle with none.
     */
    private static int drop(MatchState s, int i) {
        OrderQueues orders = s.orders();
        int size = orders.size(i);
        List<OrderQueues.Order> keep = new ArrayList<>(size);
        boolean current = false;
        for (int k = 0; k < size; k++) {
            OrderQueues.Order o = orders.order(i, k);
            if (o.kind() == OrderKind.GARRISON && o.slot() == Garrisons.BELL_SLOT) {
                current |= k == 0;
            } else {
                keep.add(o);
            }
        }
        if (keep.size() == size) {
            return 0;
        }
        orders.clear(i);
        for (OrderQueues.Order o : keep) {
            orders.queue(i, o.kind(), o.x(), o.z(), o.group(), o.slot(), o.target());
        }
        if (current) {
            int handle = s.entities().handleAt(i);
            s.bus().publish(keep.isEmpty() ? new StopOrdered(handle) : new OrderReceived(handle));
        }
        return 1;
    }

    private static boolean walkingIn(MatchState s, int i) {
        OrderQueues orders = s.orders();
        for (int k = 0; k < orders.size(i); k++) {
            OrderQueues.Order o = orders.order(i, k);
            if (o.kind() == OrderKind.GARRISON && (k == 0 || o.slot() == Garrisons.BELL_SLOT)) {
                return true;
            }
        }
        return false;
    }

    /** A holder a worker may be called to: its door, its middle, and the places left once those walking in are counted. */
    private static final class Holder {
        final int handle;
        final double x;
        final double z;
        final double doorX;
        final double doorZ;
        int room;

        Holder(int handle, double x, double z, double doorX, double doorZ, int room) {
            this.handle = handle;
            this.x = x;
            this.z = z;
            this.doorX = doorX;
            this.doorZ = doorZ;
            this.room = room;
        }
    }

    private static List<Holder> holders(MatchState s, int faction) {
        List<Holder> out = new ArrayList<>();
        for (int handle : s.buildings().handles()) {
            if (s.buildings().faction(handle) != faction || s.buildings().state(handle) != BuildingStore.COMPLETE) {
                continue;
            }
            int capacity = Garrisons.capacity(s, handle);
            int[] door = capacity > 0 ? Garrisons.door(s, handle) : null;
            if (door != null) {
                Footprint at = s.footprintOf(handle);
                out.add(new Holder(
                        handle,
                        middleX(at),
                        middleZ(at),
                        door[0] + 0.5,
                        door[1] + 0.5,
                        capacity - s.garrison().count(handle)));
            }
        }
        return out;
    }

    private static double middleX(Footprint at) {
        return at.anchorX() + at.spanX() / 2.0;
    }

    private static double middleZ(Footprint at) {
        return at.anchorZ() + at.spanZ() / 2.0;
    }
}
