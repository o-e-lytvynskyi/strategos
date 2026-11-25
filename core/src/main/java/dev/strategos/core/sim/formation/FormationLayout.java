package dev.strategos.core.sim.formation;

import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.SlotLayout;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.combat.Engagement;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Chooses the places of a formation's members (ORD-06). Each unit has a class: melee, which stands in front, ranged,
 * which stands behind, and the rest (workers, healers, siege), in the middle. A class takes whole rows, the block of
 * the front class first; a box is {@link SlotLayout#width the square's side} wide, a line a third of its members, so
 * its three classes make three ranks. Within a class the units are taken greedily by their projection onto the
 * facing, the one farthest ahead first, a row's worth at a time; each row's units then take its columns by their
 * projection across the facing, left to right, so no two of a row cross. Every tie goes to the lower handle, so the
 * result depends on the units' places and nothing else.
 */
public final class FormationLayout {
    private static final int MELEE = 0;
    private static final int OTHER = 1;
    private static final int RANGED = 2;

    private FormationLayout() {}

    /**
     * Gives {@code handles} their places round {@code c}'s anchor, as the unit stand now relative to its facing, and
     * sets them on {@code c}.
     */
    public static void assign(FormationController c, MatchState s, int[] handles) {
        int n = handles.length;
        double[] ahead = new double[n];
        double[] left = new double[n];
        int[] kind = new int[n];
        double spacing = SlotLayout.MIN_SPACING;
        for (int t = 0; t < n; t++) {
            int i = Handle.index(handles[t]);
            double dx = s.motion().x(i) - c.anchorX();
            double dz = s.motion().z(i) - c.anchorZ();
            ahead[t] = dx * c.facingX() + dz * c.facingZ();
            left[t] = -dx * c.facingZ() + dz * c.facingX();
            kind[t] = s.stats().canRanged(i) ? RANGED : s.stats().canAttack(i) ? MELEE : OTHER;
            spacing = Math.max(spacing, SlotLayout.spacing(Engagement.radius(s, i)));
        }
        int width = c.shape() == FormationShape.BOX ? SlotLayout.width(n) : Math.max(1, (n + 2) / 3);
        Integer[] order = new Integer[n];
        Arrays.setAll(order, t -> t);
        Arrays.sort(
                order,
                Comparator.<Integer>comparingInt(t -> kind[t])
                        .thenComparingDouble(t -> -ahead[t])
                        .thenComparingDouble(t -> -left[t])
                        .thenComparingInt(t -> handles[t]));
        int rows = 0;
        for (int k = MELEE; k <= RANGED; k++) {
            rows += (count(kind, k) + width - 1) / width;
        }
        int[] members = new int[n];
        double[] lateral = new double[n];
        double[] forward = new double[n];
        int slot = 0;
        int row = 0;
        int at = 0;
        while (at < n) {
            int rowKind = kind[order[at]];
            int inClass = count(kind, rowKind) - classStart(kind, order, at, rowKind);
            int size = Math.min(width, inClass);
            Integer[] chunk = Arrays.copyOfRange(order, at, at + size);
            Arrays.sort(
                    chunk, Comparator.<Integer>comparingDouble(t -> -left[t]).thenComparingInt(t -> handles[t]));
            for (int column = 0; column < size; column++) {
                members[slot] = handles[chunk[column]];
                lateral[slot] = ((size - 1) / 2.0 - column) * spacing;
                forward[slot] = ((rows - 1) / 2.0 - row) * spacing;
                slot++;
            }
            row++;
            at += size;
        }
        c.setPlaces(members, lateral, forward);
    }

    private static int count(int[] kind, int k) {
        int count = 0;
        for (int v : kind) {
            if (v == k) {
                count++;
            }
        }
        return count;
    }

    /** How many units of {@code rowKind} come before position {@code at} of the sorted {@code order}. */
    private static int classStart(int[] kind, Integer[] order, int at, int rowKind) {
        int before = 0;
        for (int p = 0; p < at; p++) {
            if (kind[order[p]] == rowKind) {
                before++;
            }
        }
        return before;
    }
}
