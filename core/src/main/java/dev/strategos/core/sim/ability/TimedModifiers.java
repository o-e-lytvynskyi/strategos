package dev.strategos.core.sim.ability;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.StatService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The personal modifiers abilities give for a number of ticks (UNIT-10): per (unit, ability) the tick on which they
 * come off again and the source id they were added under, over {@link StatService#addPersonal} and {@link
 * StatService#removePersonal}. Sorted by (handle, ability id), so the hash and the save do not depend on the order
 * things happened in; a unit that gets the same ability again has its modifiers renewed, never doubled. The modifiers
 * themselves are not saved: they follow from the ability's definition.
 */
public final class TimedModifiers {
    private int[] handles = new int[4];
    private String[] ids = new String[4];
    private long[] expiries = new long[4];
    private int[] sources = new int[4];
    private int count;

    public boolean empty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    /** The tick on which unit {@code handle}'s modifiers of ability {@code id} come off, or 0 when it has none. */
    public long expiryOf(int handle, String id) {
        int at = find(handle, id);
        return at >= 0 ? expiries[at] : 0;
    }

    /**
     * Gives unit {@code handle} {@code modifiers} (all under {@code source}) until tick {@code expiry}: any it already
     * has from this ability come off first.
     */
    public void apply(StatService stats, int handle, String id, int source, long expiry, List<Modifier> modifiers) {
        int at = find(handle, id);
        if (at >= 0) {
            stats.removePersonal(handle, sources[at]);
            expiries[at] = expiry;
            sources[at] = source;
        } else {
            insert(-at - 1, handle, id, expiry, source);
        }
        for (Modifier m : modifiers) {
            stats.addPersonal(handle, m);
        }
    }

    /**
     * At the tick boundary: every entry whose tick has come, or whose unit is gone, is dropped, and a living unit's
     * modifiers come off. Allocates nothing.
     */
    public void expire(long tick, StatService stats, EntityStore alive) {
        int kept = 0;
        for (int k = 0; k < count; k++) {
            boolean live = alive.isAlive(handles[k]);
            if (live && expiries[k] > tick) {
                if (kept != k) {
                    move(k, kept);
                }
                kept++;
            } else if (live) {
                stats.removePersonal(handles[k], sources[k]);
            }
        }
        for (int k = kept; k < count; k++) {
            ids[k] = "";
        }
        count = kept;
    }

    /** The hash with the entries of living units mixed in, in order. */
    public long mix(long h, EntityStore alive) {
        for (int k = 0; k < count; k++) {
            if (alive.isAlive(handles[k])) {
                h = Hasher.mix(h, handles[k]);
                h = Hasher.mix(h, ids[k].length());
                for (int c = 0; c < ids[k].length(); c++) {
                    h = Hasher.mix(h, ids[k].charAt(c));
                }
                h = Hasher.mix(h, expiries[k]);
            }
        }
        return h;
    }

    /** The living units' entries: handle, ability id, expiry. */
    List<Entry> entries(EntityStore alive) {
        List<Entry> out = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            if (alive.isAlive(handles[k])) {
                out.add(new Entry(handles[k], ids[k], expiries[k]));
            }
        }
        return out;
    }

    /** One entry as a save keeps it. */
    record Entry(int handle, String id, long expiry) {}

    /** Forgets every entry, leaving the stats alone (a restore starts from fresh stats). */
    void clear() {
        Arrays.fill(ids, 0, count, "");
        count = 0;
    }

    private int find(int handle, String id) {
        int lo = 0;
        int hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = Integer.compare(handles[mid], handle);
            if (c == 0) {
                c = ids[mid].compareTo(id);
            }
            if (c == 0) {
                return mid;
            }
            if (c < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -(lo + 1);
    }

    private void insert(int at, int handle, String id, long expiry, int source) {
        if (count == handles.length) {
            int size = count * 2;
            handles = Arrays.copyOf(handles, size);
            ids = Arrays.copyOf(ids, size);
            expiries = Arrays.copyOf(expiries, size);
            sources = Arrays.copyOf(sources, size);
        }
        System.arraycopy(handles, at, handles, at + 1, count - at);
        System.arraycopy(ids, at, ids, at + 1, count - at);
        System.arraycopy(expiries, at, expiries, at + 1, count - at);
        System.arraycopy(sources, at, sources, at + 1, count - at);
        handles[at] = handle;
        ids[at] = id;
        expiries[at] = expiry;
        sources[at] = source;
        count++;
    }

    private void move(int from, int to) {
        handles[to] = handles[from];
        ids[to] = ids[from];
        expiries[to] = expiries[from];
        sources[to] = sources[from];
    }
}
