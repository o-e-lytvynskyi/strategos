package dev.strategos.core.client.hud;

import java.util.List;

/**
 * Where the HUD's parts go (UI-02, 04 §13.5), in GUI pixels for a window of {@code screenWidth} × {@code screenHeight}
 * framebuffer pixels at GUI scale {@code guiScale}, the GUI size rounded up as Minecraft's window rounds it. Everything
 * lies inside the safe area, a margin in from the window's edges, and no two parts overlap. The bottom row holds the
 * minimap on the left, the command card on the right and the selection panel between them, centred and capped in width,
 * with the control-group bar above it; the top row holds the resource bar on the left (the safe width less 100, or 45 %
 * of it where that is more, at most 300) and notices on the right. Drawing and hit-testing use the same rectangles.
 */
public record HudLayout(
        int guiWidth,
        int guiHeight,
        HudRect safe,
        HudRect resourceBar,
        HudRect notices,
        HudRect minimap,
        HudRect groupBar,
        HudRect selectionPanel,
        HudRect commandCard) {
    public static final int MARGIN = 4;
    public static final int GAP = 4;
    public static final int BAR_HEIGHT = 12;
    static final int ROW_MIN = 48;
    static final int ROW_MAX = 120;
    static final int SELECTION_MAX = 360;
    static final int RESOURCES_MAX = 300;

    public static HudLayout compute(int screenWidth, int screenHeight, int guiScale) {
        if (screenWidth <= 0 || screenHeight <= 0 || guiScale <= 0) {
            throw new IllegalArgumentException(screenWidth + " x " + screenHeight + " at scale " + guiScale);
        }
        int w = ceilDiv(screenWidth, guiScale);
        int h = ceilDiv(screenHeight, guiScale);
        HudRect safe = new HudRect(MARGIN, MARGIN, w - 2 * MARGIN, h - 2 * MARGIN);
        int row = clamp(Math.round(h * 0.24f), ROW_MIN, ROW_MAX);
        int bottom = safe.bottom() - row;
        HudRect minimap = new HudRect(safe.x(), bottom, row, row);
        int cardWidth = Math.round(row * 1.6f);
        HudRect card = new HudRect(safe.right() - cardWidth, bottom, cardWidth, row);
        int between = card.x() - GAP - (minimap.right() + GAP);
        int selectionWidth = Math.min(between, SELECTION_MAX);
        int selectionHeight = row - BAR_HEIGHT - GAP;
        int selectionX = minimap.right() + GAP + (between - selectionWidth) / 2;
        HudRect selection = new HudRect(selectionX, safe.bottom() - selectionHeight, selectionWidth, selectionHeight);
        HudRect groups = new HudRect(selectionX, selection.y() - GAP - BAR_HEIGHT, selectionWidth, BAR_HEIGHT);
        int resourceWidth = Math.min(RESOURCES_MAX, Math.max(Math.round(safe.width() * 0.45f), safe.width() - 100));
        HudRect resources = new HudRect(safe.x(), safe.y(), resourceWidth, BAR_HEIGHT);
        HudRect notices =
                new HudRect(resources.right() + GAP, safe.y(), safe.right() - resources.right() - GAP, BAR_HEIGHT);
        return new HudLayout(w, h, safe, resources, notices, minimap, groups, selection, card);
    }

    /** The parts, for checks that go over all of them. */
    public List<HudRect> parts() {
        return List.of(resourceBar, notices, minimap, groupBar, selectionPanel, commandCard);
    }

    /**
     * A tooltip of {@code width} × {@code height} beside the cursor at ({@code cursorX}, {@code cursorY}): below and to
     * the right, flipped to the other side where it would leave the safe area, and clamped inside it.
     */
    public HudRect tooltip(int width, int height, double cursorX, double cursorY) {
        int x = (int) Math.round(cursorX) + 8;
        int y = (int) Math.round(cursorY) + 8;
        if (x + width > safe.right()) {
            x = (int) Math.round(cursorX) - 8 - width;
        }
        if (y + height > safe.bottom()) {
            y = (int) Math.round(cursorY) - 8 - height;
        }
        x = clamp(x, safe.x(), Math.max(safe.x(), safe.right() - width));
        y = clamp(y, safe.y(), Math.max(safe.y(), safe.bottom() - height));
        return new HudRect(x, y, Math.min(width, safe.width()), Math.min(height, safe.height()));
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
