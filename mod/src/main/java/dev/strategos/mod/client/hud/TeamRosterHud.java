package dev.strategos.mod.client.hud;

import dev.strategos.core.client.hud.HudLayout;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.mod.client.StrategosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Draws the public roster and its relationship to the viewer above the minimap. */
final class TeamRosterHud {
    private static final int ROW_HEIGHT = 12;
    private static final int COLUMN_WIDTH = 168;

    void draw(GuiGraphics gui, HudLayout layout, HudPaint paint) {
        var join = StrategosClient.match();
        var view = StrategosClient.presentation();
        if (join == null || view == null || view.participants().size() < 2) return;
        var players = view.participants();
        var font = Minecraft.getInstance().font;
        int bottom = layout.minimap().y() - HudLayout.GAP;
        int top = layout.resourceBar().bottom() + HudLayout.GAP;
        int rows = Math.min(players.size(), (bottom - top - 4) / ROW_HEIGHT);
        if (rows < 1) return;
        int columns = (players.size() + rows - 1) / rows;
        int width = Math.min(COLUMN_WIDTH, layout.safe().width() / columns);
        int x = layout.minimap().x();
        int y = bottom - rows * ROW_HEIGHT - 4;
        paint.panel(gui, new HudRect(x, y, width * columns, bottom - y));

        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            int left = x + (i / rows) * width;
            int line = y + 3 + (i % rows) * ROW_HEIGHT;
            int colour = StrategosClient.colour(player.faction());
            String team = Component.translatable("strategos.hud.team_number", player.team() + 1)
                    .getString();
            String relation = Component.translatable(
                            join.slot().isAdmin()
                                    ? "strategos.hud.team_participant"
                                    : join.alliedFaction(player.faction())
                                            ? "strategos.hud.team_ally"
                                            : "strategos.hud.team_opponent")
                    .getString();
            String state = Component.translatable(
                            player.connected() ? "strategos.hud.team_online" : "strategos.hud.team_offline")
                    .getString();
            String details = font.plainSubstrByWidth(team + " " + relation + " " + state, Math.max(0, width - 21));
            int detailX = left + width - 4 - font.width(details);
            String name = font.plainSubstrByWidth(player.name(), Math.max(0, detailX - left - 15));
            gui.fill(left + 4, line + 1, left + 9, line + 7, colour);
            gui.drawString(font, name, left + 12, line, 0xFFF1F1F1, false);
            gui.drawString(font, details, detailX, line, player.connected() ? 0xFFD3E2E8 : 0xFF9FA5B0, false);
        }
    }
}
