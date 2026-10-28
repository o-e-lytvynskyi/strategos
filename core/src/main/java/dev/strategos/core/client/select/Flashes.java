package dev.strategos.core.client.select;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.HotPath;
import java.util.Arrays;
import java.util.List;

/**
 * The under-attack flash (CMB-17): the player's own units whose hit points fell in a {@code unit_hud} the client took
 * within the last {@value #MILLIS} milliseconds, each with a strength from 1, just hit, to 0, over. Only what the
 * client received decides; an enemy never flashes, and a unit the viewer's frame lacks has no flash.
 */
@HotPath
public final class Flashes {
    public static final long MILLIS = 400;

    private Flashes() {}

    /** The flashing units' indices and strengths, as many as extracted; arrays are reused, never shrunk. */
    public static final class Buffer {
        private int[] indices = new int[64];
        private float[] strengths = new float[64];
        private int count;

        public int count() {
            return count;
        }

        public int index(int k) {
            return indices[k];
        }

        public float strength(int k) {
            return strengths[k];
        }

        /** Unit {@code index}'s strength, 0 when it does not flash; the buffer holds few, so a scan. */
        public float strengthOf(int index) {
            for (int k = 0; k < count; k++) {
                if (indices[k] == index) {
                    return strengths[k];
                }
            }
            return 0;
        }

        void add(int index, float strength) {
            if (count == indices.length) {
                indices = Arrays.copyOf(indices, count * 2);
                strengths = Arrays.copyOf(strengths, count * 2);
            }
            indices[count] = index;
            strengths[count++] = strength;
        }
    }

    /** Fills {@code out} with the units of faction {@code own} hit within {@link #MILLIS} before {@code nowMillis}. */
    public static void extract(ClientMatchModel model, int own, long nowMillis, Buffer out) {
        out.count = 0;
        List<UnitSummary.Entry> units = model.units();
        for (int k = 0; k < units.size(); k++) {
            UnitSummary.Entry u = units.get(k);
            long hit = model.hitAt(u.index());
            long age = nowMillis - hit;
            if (u.faction() == own && hit != Long.MIN_VALUE && age >= 0 && age < MILLIS) {
                out.add(u.index(), 1f - (float) age / MILLIS);
            }
        }
    }
}
