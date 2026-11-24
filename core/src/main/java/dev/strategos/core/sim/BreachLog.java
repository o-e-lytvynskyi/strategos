package dev.strategos.core.sim;

import java.util.Map;
import java.util.TreeMap;

/**
 * What the units of one match have breached (BLD-13): per unit handle, the wall segment its last breach attacked and
 * the tick before which it does not attack that segment again. Not saved and not hashed: a restored match forgets it,
 * so a unit may try one segment again early, which costs one attempt and changes nothing else.
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

    /** Takes unit {@code unit}'s record away; whether it had one. */
    public boolean take(int unit) {
        return last.remove(unit) != null;
    }
}
