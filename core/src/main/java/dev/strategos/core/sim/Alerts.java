package dev.strategos.core.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * The live under-attack alerts (UI-12), {@link AlertSystem}'s: each its faction, where it was raised and its last blow.
 * Saved, since an alert wakes its computer player (AI-01) and a restored match must not raise one anew (MATCH-04); not
 * hashed, as before, so M3's journals keep their hashes. Beside them, for the factions a computer plays, the blows and
 * the building losses its defence reads (AI-04): saved in the same list, tagged by a faction below zero.
 */
final class Alerts {
    /** How long a blow is kept: 30 seconds. */
    static final long BLOW_KEEP = 600;

    /** How long a loss is kept for the rebuilder: 5 minutes. */
    static final long LOSS_KEEP = 6_000;

    /** A live alert: its faction, where it was raised, its last blow. */
    record Alert(int faction, double x, double z, long last) {}

    /** A blow on a computer player's unit or building. */
    record Blow(int faction, int handle, long tick) {}

    /** A computer player's destroyed building. */
    record Loss(int faction, int template, int x, int z, int rotation, long tick) {}

    final List<Alert> list = new ArrayList<>();
    final List<Blow> blows = new ArrayList<>();
    final List<Loss> losses = new ArrayList<>();

    /**
     * Four longs an alert: the faction, x and z as their bits, the last blow's tick. A blow: -1 - faction, the handle,
     * 0, the tick. A loss: -1000 - faction, template * 4 + rotation, x * 2^32 + z, the tick.
     */
    List<Long> save() {
        List<Long> out = new ArrayList<>((list.size() + blows.size() + losses.size()) * 4);
        for (Alert a : list) {
            out.add((long) a.faction());
            out.add(Double.doubleToLongBits(a.x()));
            out.add(Double.doubleToLongBits(a.z()));
            out.add(a.last());
        }
        for (Blow b : blows) {
            out.add(-1L - b.faction());
            out.add((long) b.handle());
            out.add(0L);
            out.add(b.tick());
        }
        for (Loss l : losses) {
            out.add(-1000L - l.faction());
            out.add(l.template() * 4L + l.rotation());
            out.add(((long) l.x() << 32) | (l.z() & 0xFFFFFFFFL));
            out.add(l.tick());
        }
        return out;
    }

    void restore(List<Long> saved) {
        list.clear();
        blows.clear();
        losses.clear();
        for (int k = 0; k + 3 < saved.size(); k += 4) {
            long tag = saved.get(k);
            if (tag >= 0) {
                list.add(new Alert(
                        (int) tag,
                        Double.longBitsToDouble(saved.get(k + 1)),
                        Double.longBitsToDouble(saved.get(k + 2)),
                        saved.get(k + 3)));
            } else if (tag > -1000) {
                blows.add(new Blow((int) (-1 - tag), (int) (long) saved.get(k + 1), saved.get(k + 3)));
            } else {
                long what = saved.get(k + 1);
                long at = saved.get(k + 2);
                losses.add(new Loss(
                        (int) (-1000 - tag),
                        (int) (what >> 2),
                        (int) (at >> 32),
                        (int) at,
                        (int) (what & 3),
                        saved.get(k + 3)));
            }
        }
    }

    /** Drops what is old by {@code tick}. */
    void prune(long tick) {
        blows.removeIf(b -> tick - b.tick() >= BLOW_KEEP);
        losses.removeIf(l -> tick - l.tick() >= LOSS_KEEP);
    }

    /** A computer player's faction {@code faction} was hit at {@code handle}. */
    void blow(int faction, int handle, long tick) {
        for (int k = 0; k < blows.size(); k++) { // one entry a unit, its latest blow: a big fight stays small
            if (blows.get(k).handle() == handle) {
                blows.set(k, new Blow(faction, handle, tick));
                return;
            }
        }
        blows.add(new Blow(faction, handle, tick));
    }

    /** Records a destroyed building. */
    void lost(int faction, int template, int x, int z, int rotation, long tick) {
        losses.add(new Loss(faction, template, x, z, rotation, tick));
    }
}
