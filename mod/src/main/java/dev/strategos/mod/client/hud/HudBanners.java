package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.EconomyNotices;
import dev.strategos.core.client.hud.GroupBar;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.MatchResults;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.mod.client.StrategosClient;
import java.util.List;
import java.util.Locale;
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
     * The end screen (MATCH-01, M6 §2.3): once the match is over, a panel with the title, per-faction statistics
     * table, the phase timeline, and action buttons. It stays until the player leaves the match.
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
        int titleColor = end.faction() == own ? theme.accent() : theme.text();
        String winner = end.faction() < 0 ? "Nobody won" : "Faction " + (end.faction() + 1) + " wins";
        long seconds = end.ticks() / 20;
        String time = "After " + seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);

        MatchStateMsg state = StrategosClient.state();
        MatchResults results = state != null ? state.results() : null;
        int factions = results != null ? results.factions().size() : 0;

        int colW = 80;
        int labelW = 100;
        int tableW = labelW + colW * Math.max(factions, 1);
        int pw = Math.max(tableW + 24, Math.max(font.width(winner), font.width(time)) + 40);
        int headerH = 3 * font.lineHeight + 24;
        int statsH = factions > 0 ? (5 * (font.lineHeight + 2)) + font.lineHeight + 6 : 0;
        int buttonH = font.lineHeight + 12;
        int ph = headerH + statsH + buttonH + 8;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int px = (screenW - pw) / 2;
        int py = (screenH - ph) / 3;
        paint.panel(g, new HudRect(px, py, pw, ph));

        g.pose().pushPose();
        g.pose().translate(px + pw / 2.0, py + 6, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, title, -font.width(title) / 2, 0, titleColor, true);
        g.pose().popPose();

        int line = py + 8 + 2 * font.lineHeight;
        if (end.faction() >= 0) {
            g.fill(
                    px + (pw - font.width(winner)) / 2 - 10,
                    line,
                    px + (pw - font.width(winner)) / 2 - 3,
                    line + 7,
                    theme.faction(end.faction()));
        }
        g.drawString(font, winner, px + (pw - font.width(winner)) / 2, line, theme.text(), false);
        g.drawString(font, time, px + (pw - font.width(time)) / 2, line + font.lineHeight + 2, theme.dimText(), false);

        if (results != null && factions > 0) {
            int tableX = px + (pw - tableW) / 2;
            int tableY = py + headerH;
            String[] headers = new String[factions];
            for (int f = 0; f < factions; f++) {
                headers[f] = "F" + (f + 1);
            }
            int hdrY = tableY;
            g.drawString(font, "", tableX, hdrY, theme.dimText(), false);
            for (int f = 0; f < factions; f++) {
                int cx = tableX + labelW + colW * f + (colW - font.width(headers[f])) / 2;
                g.fill(cx - 2, hdrY, cx - 2 + 4, hdrY + font.lineHeight, theme.faction(f));
                g.drawString(font, headers[f], cx + 5, hdrY, theme.text(), false);
            }
            String[] labels = {"Units trained", "Units lost", "Buildings built", "Buildings lost"};
            for (int row = 0; row < 4; row++) {
                int ry = hdrY + (row + 1) * (font.lineHeight + 2);
                g.drawString(font, labels[row], tableX, ry, theme.dimText(), false);
                for (int f = 0; f < factions; f++) {
                    MatchResults.FactionStats fs = results.factions().get(f);
                    int val = switch (row) {
                        case 0 -> fs.unitsTrained();
                        case 1 -> fs.unitsLost();
                        case 2 -> fs.buildingsBuilt();
                        default -> fs.buildingsLost();
                    };
                    String s = String.valueOf(val);
                    int cx = tableX + labelW + colW * f + (colW - font.width(s)) / 2;
                    g.drawString(font, s, cx, ry, theme.text(), false);
                }
            }
            int phaseY = hdrY + 5 * (font.lineHeight + 2);
            g.drawString(font, "Phase timeline", tableX, phaseY, theme.dimText(), false);
            for (int f = 0; f < factions; f++) {
                MatchResults.FactionStats fs = results.factions().get(f);
                StringBuilder sb = new StringBuilder();
                for (int p = 0; p < fs.phaseTicks().size(); p++) {
                    long pt = fs.phaseTicks().get(p);
                    if (pt >= 0) {
                        if (!sb.isEmpty()) {
                            sb.append(" ");
                        }
                        long ps = pt / 20;
                        sb.append(ps / 60).append(":").append(String.format(Locale.ROOT, "%02d", ps % 60));
                    }
                }
                String phases = sb.isEmpty() ? "-" : sb.toString();
                int cx = tableX + labelW + colW * f + (colW - font.width(phases)) / 2;
                g.drawString(font, phases, cx, phaseY, theme.dimText(), false);
            }
        }

        int btnY = py + ph - buttonH - 4;
        String btnTitle = "Title screen";
        int btnW = font.width(btnTitle) + 12;
        int btnX = px + (pw - btnW) / 2;
        paint.panel(g, new HudRect(btnX, btnY, btnW, font.lineHeight + 8));
        g.drawString(font, btnTitle, btnX + 6, btnY + 4, theme.text(), false);
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
