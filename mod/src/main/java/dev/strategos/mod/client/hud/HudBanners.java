package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.EconomyNotices;
import dev.strategos.core.client.hud.GroupBar;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.mod.client.StrategosClient;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The HUD's banners: the notices strip with the wave countdown and the alert (UI-10, UI-12, AI-17), the end screen
 * (MATCH-01), the pause and speed lines (MATCH-05) and the control groups' bar. Split out of {@link RtsHud} in M4
 * T1. Client thread only.
 */
final class HudBanners {
    private final HudPaint paint;
    private final HudTheme theme;

    HudBanners(HudPaint paint) {
        this.paint = paint;
        this.theme = paint.theme;
    }

    /** The newest notices, right-aligned in the notices' strip, the newest nearest the corner. */
    void notices(GuiGraphics g, List<EconomyNotices.Shown> shown, boolean alerted) {
        MatchNews coming = StrategosClient.model().wave();
        boolean waveOn =
                coming != null && coming.ticks() > StrategosClient.model().tick();
        if (shown.isEmpty() && !alerted && !waveOn) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        HudRect r = RtsHud.layout().notices();
        int x = r.right();
        MatchNews wave = StrategosClient.model().wave();
        MatchJoin joined = StrategosClient.match();
        long matchTick = StrategosClient.model().tick();
        if (wave != null && joined != null && wave.faction() != joined.slot().value() && wave.ticks() > matchTick) {
            String text = "Attack coming: " + (wave.ticks() - matchTick + 19) / 20 + " s"; // AI-17's countdown
            int w = font.width(text) + 2 * theme.padding();
            x -= w;
            paint.panel(g, new HudRect(x, r.y(), w, r.height()));
            g.drawString(font, text, x + theme.padding(), r.y() + 2, 0xFFFFB040, false);
            x -= 2;
        }
        if (alerted) { // UI-12: the newest alert first, in red
            String text = "Under attack! (J)";
            int w = font.width(text) + 2 * theme.padding();
            x -= w;
            paint.panel(g, new HudRect(x, r.y(), w, r.height()));
            g.drawString(font, text, x + theme.padding(), r.y() + 2, 0xFFFF4040, false);
            x -= 2;
        }
        for (EconomyNotices.Shown s : shown) {
            String text = switch (s.cause()) {
                case NOT_ENOUGH_RESOURCES -> "Not enough " + s.detail();
                case POPULATION_CAPPED -> "Population capped: build houses";
                case DEPOSIT_EXHAUSTED -> s.count() > 1 ? "Workers idle (" + s.count() + ")" : "A worker is idle";
            };
            int w = font.width(text) + 2 * theme.padding();
            if (x - w < r.x()) {
                break;
            }
            x -= w;
            paint.panel(g, new HudRect(x, r.y(), w, r.height()));
            g.drawString(
                    font,
                    text,
                    x + theme.padding(),
                    r.y() + 2,
                    s.cause() == EconomyNotices.Cause.DEPOSIT_EXHAUSTED ? theme.text() : 0xFFFF9060,
                    false);
            x -= 2;
        }
    }

    /**
     * The end screen (MATCH-01): once the match is over, a panel in the middle of the window says whether the
     * player's faction won, who did, and how long the match lasted. It stays until the player leaves the match.
     */
    void end(GuiGraphics g) {
        MatchNews end = StrategosClient.model().end();
        MatchJoin join = StrategosClient.match();
        Minecraft mc = Minecraft.getInstance();
        if (join != null && end == null && !mc.options.hideGui) {
            pace(g, mc);
        }
        if (end == null || join == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int own = join.slot().value();
        String title = end.faction() == own ? "Victory" : end.faction() < 0 ? "The match is over" : "Defeat";
        String winner = end.faction() < 0 ? "Nobody won" : "Faction " + (end.faction() + 1) + " wins";
        long seconds = end.ticks() / 20;
        String time = "After " + seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
        int w = Math.max(font.width(winner), font.width(time)) + 40;
        int h = 3 * font.lineHeight + 24;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = (mc.getWindow().getGuiScaledHeight() - h) / 3;
        paint.panel(g, new HudRect(x, y, w, h));
        g.pose().pushPose();
        g.pose().translate(x + w / 2.0, y + 6, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(
                font, title, -font.width(title) / 2, 0, end.faction() == own ? theme.accent() : theme.text(), true);
        g.pose().popPose();
        int line = y + 8 + 2 * font.lineHeight;
        if (end.faction() >= 0) {
            g.fill(
                    x + (w - font.width(winner)) / 2 - 10,
                    line,
                    x + (w - font.width(winner)) / 2 - 3,
                    line + 7,
                    theme.faction(end.faction()));
        }
        g.drawString(font, winner, x + (w - font.width(winner)) / 2, line, theme.text(), false);
        g.drawString(font, time, x + (w - font.width(time)) / 2, line + font.lineHeight + 2, theme.dimText(), false);
    }

    /** Paused, a banner at the top; another speed than normal, it beside the notices (MATCH-05). */
    private void pace(GuiGraphics g, Minecraft mc) {
        Font font = mc.font;
        MatchStateMsg state = StrategosClient.state();
        int w = mc.getWindow().getGuiScaledWidth();
        if (state != null && state.lifecycle() == MatchStateMsg.Lifecycle.PAUSED) {
            String text = "Paused (F9)";
            int tw = font.width(text) * 2;
            int y = RtsHud.layout().resourceBar().bottom() + 8;
            paint.panel(g, new HudRect((w - tw) / 2 - 6, y - 4, tw + 12, font.lineHeight * 2 + 6));
            g.pose().pushPose();
            g.pose().translate(w / 2.0, y, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, text, -font.width(text) / 2, 0, theme.accent(), true);
            g.pose().popPose();
        }
        float rate = mc.level == null ? 20 : mc.level.tickRateManager().tickrate();
        if (rate != 20) {
            String text = "Speed ×"
                    + (rate / 20 == MatchControl.SLOW
                            ? "0.5"
                            : rate / 20 == MatchControl.FAST ? "1.5" : String.valueOf(rate / 20));
            HudRect r = RtsHud.layout().notices();
            int tw = font.width(text) + 2 * theme.padding();
            paint.panel(g, new HudRect(r.right() - tw, r.bottom() + 2, tw, r.height()));
            g.drawString(font, text, r.right() - tw + theme.padding(), r.bottom() + 4, theme.text(), false);
        }
    }

    /** The control groups' bar (UI-03): each group's digit and size, as many as fit. */
    void groups(GuiGraphics g, GroupBar b) {
        if (!b.slots().isEmpty()) {
            Font font = Minecraft.getInstance().font;
            HudRect r = RtsHud.layout().groupBar();
            int x = r.x();
            for (GroupBar.Slot slot : b.slots()) {
                String text = slot.digit() + ": " + slot.count();
                int w = font.width(text) + 2 * theme.padding();
                if (x + w > r.right()) {
                    break;
                }
                paint.panel(g, new HudRect(x, r.y(), w, r.height()));
                g.drawString(
                        font,
                        text,
                        x + theme.padding(),
                        r.y() + (r.height() - font.lineHeight) / 2 + 1,
                        theme.text(),
                        false);
                x += w + 2;
            }
        }
    }
}
