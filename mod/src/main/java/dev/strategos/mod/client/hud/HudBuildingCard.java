package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.hud.PhasePanel;
import dev.strategos.core.client.hud.SelectionPanel;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.shared.QueueEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * What the HUD draws about an own building: its entry in the selection panel (state, queue, a field's crop) and the
 * next phase's checklist above the command card (UI-13). Split out of {@link RtsHud} in M6 T1. Client thread only.
 */
final class HudBuildingCard {
    private final HudPaint paint;
    private final HudTheme theme;
    private final HudCardPresentation cardPresentation;

    HudBuildingCard(HudPaint paint, HudCardPresentation cardPresentation) {
        this.paint = paint;
        this.theme = paint.theme;
        this.cardPresentation = cardPresentation;
    }

    /** One building: its name and state, then its queue with the head's progress, or a field's crop. */
    void building(GuiGraphics g, Font font, TemplateSet templates, HudRect r, int pad, BuildingState.Entry b) {
        cardPresentation.icon(g, font, templates, b.template(), SelectionPanel.icon(r, pad));
        paint.text(g, font, r, 0, HudCardPresentation.name(templates, b.template()), theme.faction(b.faction()));
        String state = switch (b.state()) {
            case 0 -> "Foundation";
            case 1 -> "Building " + b.progress() / 10 + " %";
            default -> Math.round(100.0 * b.health() / UnitHud.WHOLE) + " % HP";
        };
        paint.text(g, font, r, 1, b.rally() ? state + " · rally set" : state, theme.text());
        if (b.fieldLeft() >= 0) {
            String crop = b.fieldRipening() > 0
                    ? "Ripening, " + (b.fieldRipening() + 19) / 20 + " s"
                    : b.fieldLeft() > 0 ? b.fieldLeft() / 1000 + " food left" : "Empty: reseed it";
            paint.text(g, font, r, 2, crop, theme.dimText());
            return;
        }
        for (int k = 0; k < b.queue().size(); k++) {
            HudRect cell = SelectionPanel.queueCell(r, pad, k);
            g.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), theme.border());
            entryIcon(g, font, templates, b.queue().get(k), cell);
            if (b.batch(k) > 1) { // a batch (PROD-02): its count in the corner
                String n = "×" + b.batch(k);
                g.drawString(
                        font, n, cell.right() - font.width(n), cell.bottom() - font.lineHeight + 1, theme.text(), true);
            }
            if (k == 0) {
                int w = (int) Math.round(cell.width() * Math.min(1000, b.trained()) / 1000.0);
                g.fill(cell.x(), cell.bottom() - 2, cell.x() + w, cell.bottom(), theme.accent());
            }
        }
    }

    /** UI-13: the next phase's leaves above the card, met ones green, the rest with their amounts. */
    void phasePanel(GuiGraphics g, Font font, HudRect card, @Nullable PhasePanel p) {
        if (p == null) {
            return;
        }
        int line = font.lineHeight + 2;
        int height = (p.rows().size() + 1) * line + 2 * theme.padding();
        HudRect r = new HudRect(card.x(), card.y() - height - 2, card.width(), height);
        paint.panel(g, r);
        int x = r.x() + theme.padding();
        int y = r.y() + theme.padding();
        g.drawString(
                font,
                Component.translatable(p.phase().name()).getString() + " — "
                        + (p.rows().isEmpty() ? "ready" : "needs"),
                x,
                y,
                0xFFFFD040,
                false);
        for (PhasePanel.Row row : p.rows()) {
            y += line;
            String text = (row.met() ? "✔ " : "✘ ")
                    + row.label()
                    + (row.need() > 1 ? " " + row.have() + "/" + row.need() : "");
            g.drawString(font, text, x, y, row.met() ? 0xFF80FF80 : 0xFFFF8080, false);
        }
    }

    /** A queue entry's icon: a unit's via {@link HudCardPresentation}, or for a technology (TECH-01) its own item. */
    private void entryIcon(GuiGraphics g, Font font, TemplateSet templates, int entry, HudRect cell) {
        if (!QueueEntry.isTech(entry)) {
            cardPresentation.icon(g, font, templates, entry, cell);
            return;
        }
        TechDef tech = templates.techByIndex(QueueEntry.techIndex(entry));
        paint.item(g, tech == null ? "minecraft:barrier" : tech.icon(), cell);
    }
}
