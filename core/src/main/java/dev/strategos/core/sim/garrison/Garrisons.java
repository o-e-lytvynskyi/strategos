package dev.strategos.core.sim.garrison;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.prod.Production;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a garrison is made of (BLD-16): the holder's numbers, which units a building admits, and the way out. A unit goes
 * in through {@code unitai.Garrisoning} and comes out here, at the building's door, to the free cells round it in a
 * fixed order, back to the orders it kept.
 */
public final class Garrisons {
    /** How far from the door's cell centre a unit may stand and still go in, in blocks. */
    public static final double REACH = 2.5;

    /** How far above or below the door's floor a unit may stand and still go in, in blocks. */
    public static final double RISE = 2;

    /** The slot of a garrison order the town bell gave: the all clear finds its orders, and the units inside, by it. */
    public static final int BELL_SLOT = 1;

    private Garrisons() {}

    /** Building {@code handle}'s capacity: its {@code GarrisonHolder/capacity}, 0 for a building that holds none. */
    public static int capacity(MatchState s, int handle) {
        int path = s.stats().path(CorePaths.GARRISON_CAPACITY);
        if (path < 0) {
            return 0;
        }
        double v = s.stats()
                .blockValue(s.buildings().template(handle), s.buildings().faction(handle), path);
        return Double.isFinite(v) && v > 0 ? (int) Math.round(v) : 0;
    }

    /** Whether building {@code handle} can take unit {@code index} in at all: finished, its faction's, of a class it admits. */
    public static boolean admits(MatchState s, int index, int handle) {
        BuildingStore b = s.buildings();
        if (!b.alive(handle) || b.state(handle) != BuildingStore.COMPLETE || capacity(s, handle) == 0) {
            return false;
        }
        if (b.faction(handle) != s.identity().faction(index)) {
            return false;
        }
        int path = s.stats().tokenPath(CorePaths.GARRISON_CLASSES);
        List<String> admitted = path < 0 ? null : s.stats().blockTokens(b.template(handle), b.faction(handle), path);
        if (admitted == null) {
            return false;
        }
        for (String token : s.stats().classes(index)) {
            if (admitted.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** Whether building {@code handle} has no room for one more. */
    public static boolean full(MatchState s, int handle) {
        return s.garrison().count(handle) >= capacity(s, handle);
    }

    /** The cell units go to to enter building {@code handle}: its door's, or null when it is walled in. */
    public static int @Nullable [] door(MatchState s, int handle) {
        return Production.door(s, handle);
    }

    /**
     * Lets the units {@code handles} (all inside building {@code building}) out, in that order, each onto the next free
     * cell round the door, and back to what it was doing: its next order, else idle. Units already out are skipped.
     */
    public static void release(MatchState s, int building, int[] handles) {
        if (handles.length == 0) {
            return;
        }
        Footprint at = s.footprintOf(building);
        int[] door = door(s, building);
        int cx = door == null ? at.anchorX() + at.spanX() / 2 : door[0];
        int cz = door == null ? at.anchorZ() + at.spanZ() / 2 : door[1];
        List<int[]> used = new ArrayList<>(handles.length);
        for (int handle : handles) {
            int i = Handle.index(handle);
            if (!s.entities().isAlive(handle) || s.garrison().buildingOf(i) != building) {
                continue;
            }
            int[] cell = nextFree(s, at, cx, cz, s.mobility(i), used);
            s.garrison().remove(i);
            if (cell != null) {
                used.add(cell);
                double floor = Placing.standFloor(s.surfacesFor(i), s.config().bounds(), cell[0], cell[1]);
                double y = Double.isNaN(floor) ? s.motion().y(i) : floor;
                s.motion().set(i, cell[0] + 0.5, y, cell[1] + 0.5, s.motion().yaw(i));
                s.frame()
                        .add(handle, cell[0] + 0.5, y, cell[1] + 0.5, s.motion().yaw(i), (byte) 0);
            }
            s.motion().setLatch(i, false, 0, 0); // an arrival latch would walk it back to the door
            s.paths().setRepath(i, true);
            s.bus().publish(new OrderReceived(handle)); // its machine leaves GARRISONED for its next order
        }
    }

    /**
     * Everyone inside building {@code building} comes out, as it falls. The ejection happens after the tick's grid
     * rebuild, so the grid is rebuilt once more when anyone came out, as a resumed match builds it.
     */
    public static void ejectAll(MatchState s, int building) {
        int[] inside = s.garrison().occupants(building);
        release(s, building, inside);
        if (inside.length > 0) {
            s.spatial().rebuild(s);
        }
    }

    /**
     * Puts saved rows back, refusing any that cannot be: the unit alive and in the GARRISONED leaf, the building alive,
     * finished, a holder with room and the unit's own faction's, no unit twice; and every unit in the GARRISONED leaf
     * has a row. Called after the units and buildings are restored.
     */
    public static void restore(MatchState s, List<GarrisonStore.Saved> rows) {
        for (GarrisonStore.Saved row : rows) {
            int index = Handle.index(row.unit());
            String what = "a saved garrison row for " + Handle.toString(row.unit());
            if (!s.entities().isAlive(row.unit())) {
                throw new IllegalArgumentException(what + ", which is no live unit");
            }
            if (!s.buildings().alive(row.building())
                    || s.buildings().state(row.building()) != BuildingStore.COMPLETE
                    || capacity(s, row.building()) == 0) {
                throw new IllegalArgumentException(
                        what + " names building " + Handle.toString(row.building()) + ", which is no finished holder");
            }
            if (s.buildings().faction(row.building()) != s.identity().faction(index)) {
                throw new IllegalArgumentException(what + " is in another faction's building");
            }
            if (!s.garrisoned(index)) {
                throw new IllegalArgumentException(what + ", whose state is not GARRISONED");
            }
            if (s.garrison().inside(index)) {
                throw new IllegalArgumentException(what + " twice");
            }
            if (full(s, row.building())) {
                throw new IllegalArgumentException(what + " exceeds the building's capacity");
            }
            s.garrison().add(index, row.unit(), row.building(), row.bell());
        }
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i) && s.garrisoned(i) && !s.garrison().inside(i)) {
                throw new IllegalArgumentException(
                        "unit " + Handle.toString(s.entities().handleAt(i)) + " is GARRISONED with no garrison row");
            }
        }
    }

    /**
     * The nearest cell to (cx, cz) that is free ground outside {@code at} and not in {@code used}, ring by ring, the lower
     * distance and then the lower X and Z first; the nearest free one again when every cell in reach is used. Null when
     * there is no free ground at all.
     */
    private static int @Nullable [] nextFree(
            MatchState s, Footprint at, int cx, int cz, MobilityClass mobility, List<int[]> used) {
        int[] reuse = null;
        for (int r = 0; r <= Math.max(at.spanX(), at.spanZ()) + 8; r++) {
            int[] best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != r
                            || !Construction.free(s, at, x, z, mobility)) {
                        continue;
                    }
                    if (reuse == null) {
                        reuse = new int[] {x, z};
                    }
                    double d = (x - cx) * (double) (x - cx) + (z - cz) * (double) (z - cz);
                    if (d < bestD && !taken(used, x, z)) {
                        bestD = d;
                        best = new int[] {x, z};
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return reuse;
    }

    private static boolean taken(List<int[]> used, int x, int z) {
        for (int[] cell : used) {
            if (cell[0] == x && cell[1] == z) {
                return true;
            }
        }
        return false;
    }
}
