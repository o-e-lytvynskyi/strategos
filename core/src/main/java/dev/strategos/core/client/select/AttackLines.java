package dev.strategos.core.client.select;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.HotPath;
import java.util.Arrays;
import java.util.List;

/**
 * The attack lines of the selection (CMB-17, 04 §13): from each selected unit to the unit its current order attacks,
 * extracted into a reusable {@link Buffer} once a frame and then drawn. Everything comes from what the viewer
 * received: the details {@code unit_detail} answered for its own units, whose target the server leaves out when the
 * viewer may not see it, and positions from {@code unit_summary}; a target the frame holds no unit for draws nothing.
 */
@HotPath
public final class AttackLines {
    private AttackLines() {}

    /** Lines in world-plane coordinates, as many as extracted; arrays grow and are reused, never shrunk. */
    public static final class Buffer {
        private float[] coords = new float[4 * 64];
        private int count;

        public int count() {
            return count;
        }

        /** Line {@code k}'s start X (match-local), as extracted. */
        public float fromX(int k) {
            return coords[4 * k];
        }

        public float fromZ(int k) {
            return coords[4 * k + 1];
        }

        public float toX(int k) {
            return coords[4 * k + 2];
        }

        public float toZ(int k) {
            return coords[4 * k + 3];
        }

        void add(float fromX, float fromZ, float toX, float toZ) {
            if (4 * count == coords.length) {
                coords = Arrays.copyOf(coords, coords.length * 2);
            }
            int at = 4 * count++;
            coords[at] = fromX;
            coords[at + 1] = fromZ;
            coords[at + 2] = toX;
            coords[at + 3] = toZ;
        }

        void clear() {
            count = 0;
        }
    }

    /**
     * Fills {@code out} with a line for each selected unit ({@code selected}'s first {@code count} handles, ascending,
     * as {@link Selection#handles()} lists them) whose received detail names a target the last frame holds,
     * from the unit's place to the target's; both in match-local blocks, as the frame has them. The unit's place is
     * its entry in the frame; one the frame lacks has no line.
     */
    public static void extract(ClientMatchModel model, int[] selected, int count, Buffer out) {
        out.clear();
        List<UnitDetail.Unit> details = model.details();
        for (int k = 0; k < details.size(); k++) {
            UnitDetail.Unit d = details.get(k);
            if (d.target() == UnitDetail.NO_TARGET || Arrays.binarySearch(selected, 0, count, d.handle()) < 0) {
                continue;
            }
            UnitSummary.Entry from = model.unit(Handle.index(d.handle()));
            UnitSummary.Entry to = model.unit(d.target());
            if (from != null && to != null) {
                out.add((float) from.x(), (float) from.z(), (float) to.x(), (float) to.z());
            }
        }
    }
}
