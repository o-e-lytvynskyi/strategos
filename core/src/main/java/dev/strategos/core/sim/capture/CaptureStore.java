package dev.strategos.core.sim.capture;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The capture points of buildings being captured (M9 spec §2.6), per faction in thousandths, and the strength their
 * capturers put in this second. A building its owner fully holds has no entry. Saved ({@code Play.capture}) and hashed
 * only while an entry exists, so matches without capture keep their hashes.
 */
@CoreApi
public final class CaptureStore {
    private final int factions;
    private final Map<Integer, long[]> points = new TreeMap<>();
    private final Map<Integer, long[]> strength = new TreeMap<>(); // per building: per faction, thousandths × ticks

    public CaptureStore(int factions) {
        this.factions = factions;
    }

    public int factions() {
        return factions;
    }

    /** A capturer of {@code faction} adds {@code perSecond} thousandths of strength to building {@code handle} this tick. */
    public void contribute(int handle, int faction, long perSecond) {
        if (faction < 0 || faction >= factions || perSecond <= 0) {
            return;
        }
        strength.computeIfAbsent(handle, k -> new long[factions])[faction] += perSecond;
    }

    /** This second's summed strength of building {@code handle}, per faction (per-tick sums; divide by the ticks). */
    long[] takeStrength(int handle) {
        long[] s = strength.remove(handle);
        return s == null ? new long[factions] : s;
    }

    boolean contributed(int handle) {
        return strength.containsKey(handle);
    }

    /** Building {@code handle}'s points, or null while its owner holds them all. */
    public long @org.jspecify.annotations.Nullable [] points(int handle) {
        return points.get(handle);
    }

    long[] open(int handle, int owner, long total) {
        return points.computeIfAbsent(handle, k -> {
            long[] p = new long[factions];
            p[owner] = total;
            return p;
        });
    }

    void close(int handle) {
        points.remove(handle);
    }

    /** The buildings with an entry or with strength this second, in handle order. */
    int[] active() {
        java.util.TreeSet<Integer> all = new java.util.TreeSet<>(points.keySet());
        all.addAll(strength.keySet());
        return all.stream().mapToInt(Integer::intValue).toArray();
    }

    /** A building gone: its entry and its strength go too. */
    public void forget(int handle) {
        points.remove(handle);
        strength.remove(handle);
    }

    public boolean empty() {
        return points.isEmpty() && strength.isEmpty();
    }

    public long mix(long h) {
        for (Map.Entry<Integer, long[]> e : points.entrySet()) {
            h = Hasher.mix(h, e.getKey());
            for (long v : e.getValue()) {
                h = Hasher.mix(h, v);
            }
        }
        for (Map.Entry<Integer, long[]> e : strength.entrySet()) {
            h = Hasher.mix(h, ~e.getKey());
            for (long v : e.getValue()) {
                h = Hasher.mix(h, v);
            }
        }
        return h;
    }

    /** {@code [handle, kind (0 points, 1 strength), values × factions]…}; empty when nothing is being captured. */
    public List<Long> save() {
        List<Long> out = new ArrayList<>();
        put(out, points, 0);
        put(out, strength, 1);
        return out;
    }

    public void restore(List<Long> saved) {
        points.clear();
        strength.clear();
        int stride = 2 + factions;
        if (saved.size() % stride != 0) {
            throw new IllegalArgumentException("capture state of " + saved.size() + " numbers for " + factions);
        }
        for (int i = 0; i < saved.size(); i += stride) {
            long[] v = new long[factions];
            for (int f = 0; f < factions; f++) {
                v[f] = saved.get(i + 2 + f);
            }
            (saved.get(i + 1) == 0 ? points : strength).put((int) (long) saved.get(i), v);
        }
    }

    private static void put(List<Long> out, Map<Integer, long[]> m, long kind) {
        for (Map.Entry<Integer, long[]> e : m.entrySet()) {
            out.add((long) e.getKey());
            out.add(kind);
            for (long v : e.getValue()) {
                out.add(v);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("CaptureStore[");
        points.forEach(
                (k, v) -> b.append(k).append('=').append(Arrays.toString(v)).append(' '));
        return b.append(']').toString();
    }
}
