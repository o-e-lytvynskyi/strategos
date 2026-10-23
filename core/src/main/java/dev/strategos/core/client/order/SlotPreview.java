package dev.strategos.core.client.order;

import dev.strategos.core.shared.SlotLayout;
import java.util.List;

/**
 * Where a group order's members will stand (UI-05.1–2), as the server's slot planner lays them out before it snaps
 * them to the terrain: {@link SlotLayout}'s rows centred on the target, facing from the members' centroid to it,
 * {@link SlotLayout#spacing} apart for the widest body, a place beyond the match's edge drawn back inside. The client
 * has no surface grid, so the server's places may lie up to its snap distance, 3 columns, from these (spec M2 §1.6).
 */
public final class SlotPreview {
    private SlotPreview() {}

    /**
     * The places, match-local, for members standing at {@code members} (x, z pairs), the widest body's {@code radius},
     * the target, and the match's size.
     */
    public static double[][] places(
            List<double[]> members, double radius, double targetX, double targetZ, double sizeX, double sizeZ) {
        int n = members.size();
        double[][] out = new double[n][];
        if (n == 0) {
            return out;
        }
        double cx = 0;
        double cz = 0;
        for (double[] m : members) {
            cx += m[0];
            cz += m[1];
        }
        cx /= n;
        cz /= n;
        double dx = targetX - cx;
        double dz = targetZ - cz;
        double length = Math.sqrt(dx * dx + dz * dz);
        double fx = length > 1e-9 ? dx / length : 1;
        double fz = length > 1e-9 ? dz / length : 0;
        double spacing = SlotLayout.spacing(radius);
        int width = SlotLayout.width(n);
        for (int k = 0; k < n; k++) {
            double x = SlotLayout.x(k, n, width, spacing, targetX, fx, fz);
            double z = SlotLayout.z(k, n, width, spacing, targetZ, fx, fz);
            out[k] = new double[] {Math.max(0.5, Math.min(sizeX - 0.5, x)), Math.max(0.5, Math.min(sizeZ - 0.5, z))};
        }
        return out;
    }
}
