package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.TechDef;
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
            case RESEARCH -> research(templates, b.template());
            case ABILITY -> ability(templates, b.template());
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
        TechDef tech = b.kind() == CommandCard.Kind.RESEARCH ? templates.techByIndex(b.template()) : null;
        if (tech != null) { // UI-13: the time, then what it changes
            text.append(" · ").append(plain(tech.time())).append(" s");
            for (String effect : CommandCard.techEffects(tech)) {
                text.append(" · ").append(effect);
            }
        }
        String reason = b.reason();
        if (reason != null) {
            text.append(" — ").append(reason);
        }
        return text.toString();
    }

    /** An ability button's tooltip: its name, what it does, how long it rests (UNIT-10). */
    private static String ability(TemplateSet templates, int index) {
        if (index < 0 || index >= templates.abilities().size()) {
            return "?";
        }
        AbilityDef def = templates.abilities().get(index);
        String text = Component.translatable(def.nameKey()).getString();
        String about = Component.translatable(def.descriptionKey()).getString();
        if (!about.equals(def.descriptionKey())) {
            text += " · " + about;
        }
        return def.cooldown() > 0 ? text + " · rests " + plain(def.cooldown() / 20.0) + " s" : text;
    }

    private static String research(TemplateSet templates, int techIndex) {
        TechDef tech = templates.techByIndex(techIndex);
        return "Research "
                + (tech == null ? "?" : Component.translatable(tech.name()).getString());
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

    /** An ability button's icon: its item, or the first letter of its name. */
    void abilityIcon(GuiGraphics g, Font font, TemplateSet templates, int index, HudRect cell) {
        AbilityDef def = templates.abilities().get(index);
        if (!def.icon().isEmpty()) {
            paint.item(g, def.icon(), cell);
            return;
        }
        String letter = Component.translatable(def.nameKey()).getString().substring(0, 1);
        g.drawString(
                font,
                letter,
                cell.x() + (cell.width() - font.width(letter)) / 2,
                cell.y() + (cell.height() - font.lineHeight) / 2 + 1,
                theme.text(),
                false);
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
