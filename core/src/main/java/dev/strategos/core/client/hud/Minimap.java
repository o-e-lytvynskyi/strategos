package dev.strategos.core.client.hud;

import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.UnitSummary;
import java.util.ArrayList;
import java.util.List;

/**
 * The minimap's geometry and dots (UI-11): a {@code sizeX} × {@code sizeZ} match space drawn north up into the
 * HUD's square, scaled to fit its longer side and centred; {@link #toMap} and {@link #toWorld} are each other's
 * inverses, so a click lands on the column it shows. Dots: a unit as one pixel-square in its faction's colour from
 * {@code unit_summary}, a building as its footprint.
 */
public record Minimap(HudRect rect, int sizeX, int sizeZ) {
    /** One dot: where, how large, in GUI pixels, and its faction. */
    public record Dot(double x, double y, double w, double h, int faction) {}

    /** GUI pixels a block covers. */
    public double scale() {
        return Math.min(rect.width() / (double) sizeX, rect.height() / (double) sizeZ);
    }

    /** The left and top of the image inside the square. */
    public double[] origin() {
        double s = scale();
        return new double[] {rect.x() + (rect.width() - sizeX * s) / 2, rect.y() + (rect.height() - sizeZ * s) / 2};
    }

    /** Match-local ({@code x}, {@code z}) as a GUI point. */
    public double[] toMap(double x, double z) {
        double[] o = origin();
        double s = scale();
        return new double[] {o[0] + x * s, o[1] + z * s};
    }

    /** A GUI point as match-local (x, z), clamped to the space. */
    public double[] toWorld(double px, double py) {
        double[] o = origin();
        double s = scale();
        double x = Math.max(0, Math.min(sizeX, (px - o[0]) / s));
        double z = Math.max(0, Math.min(sizeZ, (py - o[1]) / s));
        return new double[] {x, z};
    }

    /** Whether GUI point ({@code px}, {@code py}) lies on the image. */
    public boolean contains(double px, double py) {
        double[] o = origin();
        double s = scale();
        return px >= o[0] && py >= o[1] && px < o[0] + sizeX * s && py < o[1] + sizeZ * s;
    }

    /** The dots of {@code units} and {@code buildings}: buildings first, under the units. */
    public List<Dot> dots(List<UnitSummary.Entry> units, List<BuildingState.Entry> buildings, TemplateSet templates) {
        List<Dot> out = new ArrayList<>(units.size() + buildings.size());
        double s = scale();
        double unit = Math.max(1, s);
        for (BuildingState.Entry b : buildings) {
            Blueprint bp = templates.has(b.template()) ? templates.blueprint(b.template()) : null;
            Footprint at = bp == null ? null : Footprint.of(bp, b.x(), b.z(), b.rotation());
            double[] p = toMap(b.x(), b.z());
            double w = at == null ? 2 : Math.max(2, at.spanX() * s);
            double h = at == null ? 2 : Math.max(2, at.spanZ() * s);
            out.add(new Dot(p[0], p[1], w, h, b.faction()));
        }
        for (UnitSummary.Entry u : units) {
            double[] p = toMap(u.qx() / 8.0, u.qz() / 8.0);
            out.add(new Dot(p[0] - unit / 2, p[1] - unit / 2, unit, unit, u.faction()));
        }
        return out;
    }

    /**
     * The camera's frame: where the window's four corners, top left first and clockwise, meet the ground at height
     * {@code groundY}, match-local x then z; a corner looking above the horizon reaches {@value #FAR} blocks out.
     */
    public static double[] frame(View view, double groundY, double originX, double originZ) {
        double[][] corners = {{0, 0}, {view.width(), 0}, {view.width(), view.height()}, {0, view.height()}};
        double[] out = new double[8];
        for (int k = 0; k < 4; k++) {
            Picking.Ray ray = Picking.ray(view, corners[k][0], corners[k][1]);
            double t = ray.dy() < -1e-6 ? (groundY - ray.oy()) / ray.dy() : FAR;
            double[] p = ray.at(Math.min(FAR, Math.max(0, t)));
            out[2 * k] = p[0] - originX;
            out[2 * k + 1] = p[2] - originZ;
        }
        return out;
    }

    /** How far a corner's ray may reach. */
    public static final double FAR = 512;
}
