package dev.strategos.core.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * The live under-attack alerts (UI-12), {@link AlertSystem}'s: each its faction, where it was raised and its last blow.
 * Saved, since an alert wakes its computer player (AI-01) and a restored match must not raise one anew (MATCH-04); not
 * hashed, as before, so M3's journals keep their hashes.
 */
final class Alerts {
    /** A live alert: its faction, where it was raised, its last blow. */
    record Alert(int faction, double x, double z, long last) {}

    final List<Alert> list = new ArrayList<>();

    /** Four longs an alert: the faction, x and z as their bits, the last blow's tick. */
    List<Long> save() {
        List<Long> out = new ArrayList<>(list.size() * 4);
        for (Alert a : list) {
            out.add((long) a.faction());
            out.add(Double.doubleToLongBits(a.x()));
            out.add(Double.doubleToLongBits(a.z()));
            out.add(a.last());
        }
        return out;
    }

    void restore(List<Long> saved) {
        list.clear();
        for (int k = 0; k + 3 < saved.size(); k += 4) {
            list.add(new Alert(
                    (int) (long) saved.get(k),
                    Double.longBitsToDouble(saved.get(k + 1)),
                    Double.longBitsToDouble(saved.get(k + 2)),
                    saved.get(k + 3)));
        }
    }
}
