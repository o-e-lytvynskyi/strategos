package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.hud.ResourceBar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import org.jspecify.annotations.Nullable;

/** The resource bar's parts (UI-07), laid out from the model. Split out of {@link RtsHud} in M4 T1. */
final class ResourceBarSegments {
    private ResourceBarSegments() {}

    /** One part of the resource bar: where it is, its icon (null for none), its text and colour, and its tooltip. */
    record Segment(HudRect at, @Nullable String icon, String text, int colour, String tooltip) {}

    /**
     * The bar's parts, left to right: each resource's icon and stock, with its income a minute where all of them fit,
     * the population (red at its cap) and the idle workers.
     */
    static List<Segment> of(Font font, HudRect bar, ResourceBar b) {
        for (boolean incomes : new boolean[] {true, false}) {
            List<Segment> out = new ArrayList<>();
            int x = bar.x() + 3;
            for (ResourceBar.Entry e : b.resources()) {
                String text =
                        incomes && e.perMinute() > 0 ? e.stock() + " +" + e.perMinute() : String.valueOf(e.stock());
                int w = 10 + font.width(text);
                out.add(new Segment(
                        new HudRect(x, bar.y(), w, bar.height()),
                        resourceIcon(e.id()),
                        text,
                        HudTheme.DEFAULT.text(),
                        e.id() + ": " + e.stock() + " · " + e.perMinute() + " a minute · " + e.workers()
                                + " gathering"));
                x += w + 6;
            }
            String pop = b.populationUsed() + "/" + b.populationCap();
            int pw = 10 + font.width(pop);
            out.add(new Segment(
                    new HudRect(x, bar.y(), pw, bar.height()),
                    "minecraft:player_head",
                    pop,
                    b.capped() ? 0xFFFF6060 : HudTheme.DEFAULT.text(),
                    b.capped() ? "Population " + pop + ": build houses to train more" : "Population " + pop));
            x += pw + 6;
            String idle = String.valueOf(b.idleWorkers());
            int iw = 10 + font.width(idle);
            out.add(new Segment(
                    new HudRect(x, bar.y(), iw, bar.height()),
                    "minecraft:wooden_axe",
                    idle,
                    b.idleWorkers() > 0 ? HudTheme.DEFAULT.accent() : HudTheme.DEFAULT.dimText(),
                    b.idleWorkers() + " idle workers"));
            x += iw;
            if (x <= bar.right() || !incomes) {
                return out;
            }
        }
        return List.of();
    }

    /** A resource's icon, by its id: the item a player knows it by. */
    private static String resourceIcon(String id) {
        return switch (id) {
            case "food" -> "minecraft:apple";
            case "wood" -> "minecraft:oak_log";
            case "stone" -> "minecraft:cobblestone";
            case "iron" -> "minecraft:iron_ingot";
            default -> "minecraft:paper";
        };
    }
}
