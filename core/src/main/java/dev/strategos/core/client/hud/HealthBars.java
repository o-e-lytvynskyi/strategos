package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.UnitHud;
import java.util.ArrayList;
import java.util.List;

/**
 * The health bars' frame data (CMB-06, 04 §13.6): a bar over each selected or hurt unit, from the hit points the
 * client knows, extracted once a frame and then drawn. A bar sits half a block over the head, is as wide as the
 * unit's footprint allows within a minimum, and fills from the left in a colour from green, whole, to red.
 */
public final class HealthBars {
    public static final double ABOVE = 0.5;
    public static final double MIN_WIDTH = 0.9;

    private HealthBars() {}

    /** A unit the frame may bar: its feet, height and width in the world, whether selected, and its health in 255ths. */
    public record Unit(double x, double y, double z, double height, double width, boolean selected, int health) {}

    /** A bar centred at ({@code x}, {@code y}, {@code z}), {@code width} wide, {@code fill} of it filled in {@code argb}. */
    public record Bar(double x, double y, double z, double width, double fill, int argb) {}

    /** A site's bar colour: the progress, amber. */
    public static final int SITE = 0xFFF5B942;

    /**
     * A progress bar over each of the player's own foundations and sites (UI-09): over its footprint's middle, half a
     * block above its blueprint's top, as wide as its shorter side up to four blocks, filled to its progress. World
     * coordinates from the match's origin ({@code originX}, {@code originZ}).
     */
    public static List<Bar> sites(
            List<dev.strategos.core.protocol.BuildingState.Entry> buildings,
            dev.strategos.core.content.TemplateSet templates,
            int originX,
            int originZ) {
        List<Bar> out = new ArrayList<>();
        for (var e : buildings) {
            var bp = e.own() && e.state() != CommandCard.COMPLETE ? templates.blueprint(e.template()) : null;
            if (bp == null) {
                continue;
            }
            var at = dev.strategos.core.content.Footprint.of(bp, e.x(), e.z(), e.rotation());
            out.add(new Bar(
                    originX + at.anchorX() + at.spanX() / 2.0,
                    e.baseY() + bp.sizeY() + 0.5,
                    originZ + at.anchorZ() + at.spanZ() / 2.0,
                    Math.min(4, Math.min(at.spanX(), at.spanZ())),
                    Math.max(0, Math.min(1, e.progress() / 1000.0)),
                    SITE));
        }
        return out;
    }

    public static List<Bar> extract(List<Unit> units) {
        List<Bar> bars = new ArrayList<>();
        for (Unit u : units) {
            if (!u.selected() && u.health() >= UnitHud.WHOLE) {
                continue;
            }
            double fill = u.health() / (double) UnitHud.WHOLE;
            bars.add(new Bar(
                    u.x(),
                    u.y() + u.height() + ABOVE,
                    u.z(),
                    Math.max(MIN_WIDTH, u.width() * 1.5),
                    fill,
                    colour(fill)));
        }
        return bars;
    }

    /** Green when whole, yellow at half, red near nothing; opaque. */
    public static int colour(double fill) {
        double f = Math.max(0, Math.min(1, fill));
        int r = (int) Math.round(255 * Math.min(1, 2 * (1 - f)));
        int g = (int) Math.round(255 * Math.min(1, 2 * f));
        return 0xFF000000 | r << 16 | g << 8 | 0x30;
    }
}
