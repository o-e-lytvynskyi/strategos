package dev.strategos.core.sim.garrison;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;

/**
 * Who is inside which building (BLD-16): for each unit index the building it sits in, and for each building its
 * occupants in the order they went in, which is the order they come out. The unit keeps everything else it has: its
 * handle, hit points and order queue stay in their own stores. A unit the town bell called in is marked, so the all
 * clear lets out those and no others. Nothing is kept for a match nobody garrisons in, so such a
 * match saves and hashes as before.
 */
public final class GarrisonStore {
    private int[] building = new int[0]; // by unit index: the building handle, 0 for none
    private final Map<Integer, List<Integer>> occupants = new TreeMap<>(); // building handle -> unit handles, in order
    private boolean[] belled = new boolean[0]; // by unit index: went in at the bell's call
    private int total;
    private int belledTotal;

    /**
     * One saved row: a unit, the building it is in and whether the bell called it in; rows come building by building,
     * in entry order.
     */
    public record Saved(int unit, int building, boolean bell) {
        public Saved {
            if (unit == 0 || building == 0) {
                throw new IllegalArgumentException("a garrison row needs a unit and a building");
            }
        }

        /** A unit that went in on its own order. */
        public Saved(int unit, int building) {
            this(unit, building, false);
        }
    }

    /** Grows the per-unit table to hold {@code size} indices. */
    public void ensureCapacity(int size) {
        if (size > building.length) {
            building = Arrays.copyOf(building, Math.max(size, building.length * 2));
            belled = Arrays.copyOf(belled, building.length);
        }
    }

    /** Whether nobody is inside any building. */
    public boolean empty() {
        return total == 0;
    }

    /** Whether unit {@code index} is inside a building. */
    public boolean inside(int index) {
        return index < building.length && building[index] != 0;
    }

    /** The building unit {@code index} is inside, or 0. */
    public int buildingOf(int index) {
        return index < building.length ? building[index] : 0;
    }

    /** Whether unit {@code index} is inside because the bell called it. */
    public boolean belled(int index) {
        return index < belled.length && belled[index];
    }

    /** How many units are inside building {@code handle}. */
    public int count(int handle) {
        List<Integer> list = occupants.get(handle);
        return list == null ? 0 : list.size();
    }

    /** The handles of the units inside building {@code handle}, in the order they went in. */
    public int[] occupants(int handle) {
        List<Integer> list = occupants.get(handle);
        if (list == null) {
            return new int[0];
        }
        int[] out = new int[list.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = list.get(k);
        }
        return out;
    }

    /** Unit {@code index}, whose handle is {@code unit}, goes inside building {@code handle}, after those already in. */
    public void add(int index, int unit, int handle) {
        add(index, unit, handle, false);
    }

    /** As {@link #add(int, int, int)}; {@code bell} when the town bell called the unit in. */
    public void add(int index, int unit, int handle, boolean bell) {
        ensureCapacity(index + 1);
        if (building[index] != 0) {
            throw new IllegalStateException("unit " + index + " is inside a building already");
        }
        building[index] = handle;
        if (bell) {
            belled[index] = true;
            belledTotal++;
        }
        occupants.computeIfAbsent(handle, h -> new ArrayList<>()).add(unit);
        total++;
    }

    /** Unit {@code index} is no longer inside: it came out, or it died. Nothing happens if it was not inside. */
    public void remove(int index) {
        if (!inside(index)) {
            return;
        }
        int handle = building[index];
        building[index] = 0;
        if (belled[index]) {
            belled[index] = false;
            belledTotal--;
        }
        List<Integer> list = occupants.get(handle);
        if (list != null) {
            for (int k = 0; k < list.size(); k++) {
                if (Handle.index(list.get(k)) == index) {
                    list.remove(/* index */ k);
                    break;
                }
            }
            if (list.isEmpty()) {
                occupants.remove(handle);
            }
        }
        total--;
    }

    /** The rows, building by building, each building's units in entry order. */
    public List<Saved> save() {
        List<Saved> rows = new ArrayList<>(total);
        for (Map.Entry<Integer, List<Integer>> e : occupants.entrySet()) {
            for (int unit : e.getValue()) {
                rows.add(new Saved(unit, e.getKey(), belled(Handle.index(unit))));
            }
        }
        return rows;
    }

    /** Rows saved by {@link #save}; {@code indexOf} maps a unit handle to its index. */
    public void restore(List<Saved> rows, IntUnaryOperator indexOf) {
        for (Saved row : rows) {
            add(indexOf.applyAsInt(row.unit()), row.unit(), row.building(), row.bell());
        }
    }

    /** Folds the rows into hash {@code h}. */
    public long mix(long h) {
        h = Hasher.mix(h, 0x47415252L);
        for (Map.Entry<Integer, List<Integer>> e : occupants.entrySet()) {
            h = Hasher.mix(h, e.getKey());
            h = Hasher.mix(h, e.getValue().size());
            for (int unit : e.getValue()) {
                h = Hasher.mix(h, unit);
            }
        }
        if (belledTotal > 0) { // only once the bell called someone in, so older states keep their hashes
            h = Hasher.mix(h, 0x42454C4CL);
            for (List<Integer> list : occupants.values()) {
                for (int unit : list) {
                    if (belled(Handle.index(unit))) {
                        h = Hasher.mix(h, unit);
                    }
                }
            }
        }
        return h;
    }
}
