package dev.strategos.mod.client.debug;

import dev.strategos.core.protocol.DebugLayer;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/**
 * The overlay's text panel (PATH-10), a GUI layer above the others: the match, its units, the path queue, latency p95
 * and compute time, the path counts and each phase timer's 95th percentile, from the feed's panel. Hidden while the
 * overlay is off, the panel layer is not drawn, the F3 screen is open or the GUI is hidden.
 */
final class OverlayPanel implements LayeredDraw.Layer {
    private static final int MARGIN = 4;

    private final ClientDebugState state;

    OverlayPanel(ClientDebugState state) {
        this.state = state;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!state.enabled()
                || !state.draws(DebugLayer.PANEL)
                || mc.options.hideGui
                || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        List<String> lines = state.panelLines();
        if (lines.isEmpty()) {
            return;
        }
        Font font = mc.font;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int step = font.lineHeight + 1;
        graphics.fill(MARGIN - 2, MARGIN - 2, MARGIN + width + 2, MARGIN + lines.size() * step + 1, 0x90000000);
        int y = MARGIN;
        for (String line : lines) {
            graphics.drawString(font, line, MARGIN, y, 0xFFE0E0E0, false);
            y += step;
        }
    }
}
