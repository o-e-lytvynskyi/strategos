package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.mod.client.StrategosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

/**
 * The HUD's hover panels: the resource bar's, the command card's and the hovered unit's tooltips at the cursor, and
 * the first-person inspection under the crosshair. Split out of {@link RtsHud} in M6 T1. Client thread only.
 */
final class HudTooltips {
    private final HudPaint paint;
    private final HudTheme theme;
    private final HudCardPresentation cardPresentation;

    HudTooltips(HudPaint paint, HudCardPresentation cardPresentation) {
        this.paint = paint;
        this.theme = paint.theme;
        this.cardPresentation = cardPresentation;
    }

    /** The bar's, the card button's and the unit's tooltips, each when there is one and the HUD shows. */
    void tooltips(
            GuiGraphics g,
            boolean shown,
            CommandCard.@Nullable Button b,
            @Nullable String bar,
            RtsHud.@Nullable Tooltip t) {
        Minecraft mc = Minecraft.getInstance();
        if (shown && bar != null) {
            Font font = mc.font;
            int pad = theme.padding();
            double[] gui = HudCardPresentation.cursorGui(mc);
            HudRect r = RtsHud.layout().tooltip(font.width(bar) + 2 * pad, font.lineHeight + 2 * pad, gui[0], gui[1]);
            paint.panel(g, r);
            g.drawString(font, bar, r.x() + pad, r.y() + pad + 1, theme.text(), false);
        }
        if (shown && b != null) {
            Font font = mc.font;
            String text = cardPresentation.describe(b, StrategosClient.templates());
            int pad = theme.padding();
            double[] gui = HudCardPresentation.cursorGui(mc);
            HudRect r = RtsHud.layout().tooltip(font.width(text) + 2 * pad, font.lineHeight + 2 * pad, gui[0], gui[1]);
            paint.panel(g, r);
            g.drawString(font, text, r.x() + pad, r.y() + pad + 1, b.affordable() ? theme.text() : 0xFFFF8080, false);
        }
        if (shown && t != null) {
            Font font = mc.font;
            int pad = theme.padding();
            int swatch = font.lineHeight - 2;
            int w = swatch + 3 + font.width(t.name()) + 2 * pad;
            int h = font.lineHeight + 2 * pad;
            // the cursor in GUI pixels, as Minecraft turns window points into a screen's mouse position
            double x = mc.mouseHandler.xpos()
                    * mc.getWindow().getGuiScaledWidth()
                    / mc.getWindow().getScreenWidth();
            double y = mc.mouseHandler.ypos()
                    * mc.getWindow().getGuiScaledHeight()
                    / mc.getWindow().getScreenHeight();
            HudRect r = RtsHud.layout().tooltip(w, h, x, y);
            paint.panel(g, r);
            g.fill(
                    r.x() + pad,
                    r.y() + pad + 1,
                    r.x() + pad + swatch,
                    r.y() + pad + 1 + swatch,
                    theme.faction(t.faction()));
            g.drawString(font, t.name(), r.x() + pad + swatch + 3, r.y() + pad + 1, theme.text(), false);
        }
    }

    /** Under the first-person crosshair: a panel with the unit's name, faction swatch and what is known of it. */
    void inspect(GuiGraphics g, RtsHud.@Nullable Inspect i, boolean rtsActive) {
        Minecraft mc = Minecraft.getInstance();
        if (i == null || mc.options.hideGui || rtsActive) {
            return;
        }
        Font font = mc.font;
        int pad = theme.padding();
        int swatch = font.lineHeight - 2;
        int w = Math.max(swatch + 3 + font.width(i.name()), font.width(i.detail())) + 2 * pad;
        int h = 2 * font.lineHeight + 2 * pad + 1;
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 8;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 8;
        HudRect r = new HudRect(x, y, w, h);
        paint.panel(g, r);
        g.fill(
                r.x() + pad,
                r.y() + pad + 1,
                r.x() + pad + swatch,
                r.y() + pad + 1 + swatch,
                theme.faction(i.faction()));
        g.drawString(font, i.name(), r.x() + pad + swatch + 3, r.y() + pad + 1, theme.text(), false);
        g.drawString(font, i.detail(), r.x() + pad, r.y() + pad + 2 + font.lineHeight, theme.text(), false);
    }
}
