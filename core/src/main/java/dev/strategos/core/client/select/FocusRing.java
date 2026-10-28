package dev.strategos.core.client.select;

import java.util.ArrayList;
import java.util.List;

/**
 * The ring on a focused unit (ORD-05): a band {@link NodeRing#WIDTH} wide of {@value #SEGMENTS} flat quads round a
 * centre, each a corner after the other, as solid quads with no particles, so a frozen frame draws it the same every
 * time. A building's focus ring is {@link NodeRing}'s band round its footprint.
 */
public final class FocusRing {
    /** How many quads make the ring. */
    public static final int SEGMENTS = 16;

    private FocusRing() {}

    /** One quad of the band, match-local: corners (x0, z0) to (x3, z3) in order. */
    public record Quad(double x0, double z0, double x1, double z1, double x2, double z2, double x3, double z3) {}

    /** The band whose inner edge is at {@code radius} from ({@code cx}, {@code cz}). */
    public static List<Quad> of(double cx, double cz, double radius) {
        List<Quad> out = new ArrayList<>(SEGMENTS);
        double outer = radius + NodeRing.WIDTH;
        for (int k = 0; k < SEGMENTS; k++) {
            double a0 = 2 * Math.PI * k / SEGMENTS;
            double a1 = 2 * Math.PI * (k + 1) / SEGMENTS;
            out.add(new Quad(
                    cx + Math.cos(a0) * radius,
                    cz + Math.sin(a0) * radius,
                    cx + Math.cos(a0) * outer,
                    cz + Math.sin(a0) * outer,
                    cx + Math.cos(a1) * outer,
                    cz + Math.sin(a1) * outer,
                    cx + Math.cos(a1) * radius,
                    cz + Math.sin(a1) * radius));
        }
        return out;
    }
}
