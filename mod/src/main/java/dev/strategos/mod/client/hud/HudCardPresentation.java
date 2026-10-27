package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.shared.Stance;
import dev.strategos.mod.client.StrategosClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Card labels, icons and cursor coordinates shared by the HUD's drawing paths. */
final class HudCardPresentation {
    private final HudTheme theme = HudTheme.DEFAULT;
    private final HudPaint paint;

    HudCardPresentation(HudPaint paint) {
        this.paint = paint;
    }

    /** A template's name, its translation key in the language files. */
    static String name(TemplateSet templates, int index) {
        return templates.has(index)
                ? Component.translatable(templates.byIndex(index).name()).getString()
                : "?";
    }

    /** What the hovered card button does, and what it costs. */
    String describe(CommandCard.Button b, TemplateSet templates) {
        String what = switch (b.kind()) {
            case TRAIN -> "Train " + name(templates, b.template());
            case BUILD -> "Build " + name(templates, b.template());
            case RESEED -> "Reseed";
            case BUILD_PAGE -> "Build";
            case BACK -> "Back";
            case STOP -> "Stop";
            case AUTO_RESEED -> b.on() ? "Auto-reseed: on (click to turn off)" : "Auto-reseed: off (click to turn on)";
            case CANCEL_BUILDING -> "Cancel: the share not yet built comes back";
            case STANCE -> stanceLabel(b);
            case RING_BELL -> Component.translatable("strategos.card.ring_bell").getString();
            case ALL_CLEAR -> Component.translatable("strategos.card.all_clear").getString();
            case DEMOLISH -> b.on() ? "Click again to demolish, for nothing back" : "Demolish";
        };
        List<String> resources = StrategosClient.resources();
        StringBuilder text = new StringBuilder(what);
        for (int k = 0; k < b.cost().size(); k++) {
            if (b.cost().get(k) > 0) {
                text.append(" · ")
                        .append(b.cost().get(k) / 1000)
                        .append(' ')
                        .append(k < resources.size() ? resources.get(k) : "?");
            }
        }
        if (b.kind() == CommandCard.Kind.TRAIN || b.kind() == CommandCard.Kind.BUILD) {
            for (CommandCard.Line line : CommandCard.breakdown(templates, b.template())) {
                text.append(" · ").append(line.label()).append(' ').append(plain(line.value()));
            }
        }
        String reason = b.reason();
        if (reason != null) {
            text.append(" — ").append(reason);
        }
        return text.toString();
    }

    /** A stance button's tooltip: its translated name, and that the selection holds it. */
    private static String stanceLabel(CommandCard.Button b) {
        Stance stance = b.stance();
        String name = stance == null
                ? "?"
                : Component.translatable(CommandCard.stanceKey(stance)).getString();
        return b.on() ? name + " (current)" : name;
    }

    /** The item a stance button is drawn with. */
    static String stanceItem(@Nullable Stance stance) {
        return switch (stance == null ? Stance.AGGRESSIVE : stance) {
            case VIOLENT -> "minecraft:iron_axe";
            case AGGRESSIVE -> "minecraft:iron_sword";
            case DEFENSIVE -> "minecraft:shield";
            case STAND_GROUND -> "minecraft:iron_boots";
            case PASSIVE -> "minecraft:white_banner";
        };
    }

    /** A number as a player reads it: a whole one bare, else to one decimal. */
    static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    void icon(GuiGraphics g, Font font, TemplateSet templates, int template, HudRect cell) {
        String id = CommandCard.icon(templates, template);
        if (id != null) {
            paint.item(g, id, cell);
        } else {
            String letter = name(templates, template).substring(0, 1);
            g.drawString(
                    font,
                    letter,
                    cell.x() + (cell.width() - font.width(letter)) / 2,
                    cell.y() + (cell.height() - font.lineHeight) / 2 + 1,
                    theme.text(),
                    false);
        }
    }

    /** The cursor in GUI pixels, as Minecraft turns window points into a screen's mouse position. */
    static double[] cursorGui(Minecraft mc) {
        return new double[] {
            mc.mouseHandler.xpos()
                    * mc.getWindow().getGuiScaledWidth()
                    / mc.getWindow().getScreenWidth(),
            mc.mouseHandler.ypos()
                    * mc.getWindow().getGuiScaledHeight()
                    / mc.getWindow().getScreenHeight()
        };
    }

    static DepositState.@Nullable Entry deposit(@Nullable Integer node) {
        if (node == null) {
            return null;
        }
        for (DepositState.Entry e : StrategosClient.model().deposits()) {
            if (e.node() == node) {
                return e;
            }
        }
        return null;
    }

    static BuildingState.@Nullable Entry entry(@Nullable Integer handle) {
        if (handle == null) {
            return null;
        }
        for (BuildingState.Entry e : StrategosClient.model().buildings()) {
            if (e.handle() == handle) {
                return e;
            }
        }
        return null;
    }
}
