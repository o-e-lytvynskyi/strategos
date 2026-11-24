package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntPredicate;

/**
 * What the units of one match have breached (BLD-13): per unit handle, the wall segment its last breach attacked and
 * the tick before which it does not attack that segment again. Saved ({@code Play.breaches}) and hashed while it holds
 * any record: a unit's next step reads it, so a match restored without it would walk otherwise (the M9 AI soak's seed 13
 * diverged 20 ticks after a journal restore). A record stays until its unit's walk takes it, expired or not, because
 * {@link #take} answers whether one was there.
 */
public final class BreachLog {
    private final Map<Integer, long[]> last = new TreeMap<>();

    /** Unit {@code unit} attacked {@code segment} and leaves it alone until tick {@code until}. */
    public void put(int unit, int segment, long until) {
        last.put(unit, new long[] {segment, until});
    }

    /** Whether unit {@code unit} attacked {@code segment} lately: its tick of leave has not come. */
    public boolean resting(int unit, int segment, long tick) {
        long[] e = last.get(unit);
        return e != null && e[0] == segment && tick < e[1];
    }

    public boolean empty() {
        return last.isEmpty();
    }

    /**
     * The state hash with this log's records of living units mixed in, in handle order. A dead unit's record is inert
     * (handles carry a generation, and only a living unit's walk reads or takes one), so neither the hash nor the save
     * carries it: a restored match, which has none, hashes alike.
     */
    public long mix(long h, IntPredicate alive) {
        for (Map.Entry<Integer, long[]> e : last.entrySet()) {
            if (!alive.test(e.getKey())) {
                continue;
            }
            h = Hasher.mix(h, e.getKey());
            h = Hasher.mix(h, e.getValue()[0]);
            h = Hasher.mix(h, e.getValue()[1]);
        }
        return h;
    }

    /** The saved form: per record, the unit handle, the segment and the tick of leave, in handle order. */
    public List<Long> save(IntPredicate alive) {
        List<Long> out = new ArrayList<>(3 * last.size());
        for (Map.Entry<Integer, long[]> e : last.entrySet()) {
            if (!alive.test(e.getKey())) {
                continue;
            }
            out.add((long) e.getKey());
            out.add(e.getValue()[0]);
            out.add(e.getValue()[1]);
        }
        return out;
    }

    /** Replaces this log with a saved one ({@link #save}). */
    public void restore(List<Long> saved) {
        if (saved.size() % 3 != 0) {
            throw new IllegalArgumentException("breaches: " + saved.size() + " numbers, not a multiple of 3");
        }
        last.clear();
        for (int k = 0; k < saved.size(); k += 3) {
            long unit = saved.get(k);
            if (unit != (int) unit || last.containsKey((int) unit)) {
                throw new IllegalArgumentException("breaches: a bad or repeated unit handle " + unit);
            }
            last.put((int) unit, new long[] {saved.get(k + 1), saved.get(k + 2)});
        }
    }

    /** Takes unit {@code unit}'s record away; whether it had one. */
    public boolean take(int unit) {
        return last.remove(unit) != null;
    }
}
