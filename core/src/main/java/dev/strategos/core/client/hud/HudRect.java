package dev.strategos.core.client.hud;

/** A rectangle in GUI pixels: its left, top, width and height. */
public record HudRect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    /** Whether the two share any pixel; touching edges do not. */
    public boolean overlaps(HudRect other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    /** Whether this one lies wholly inside {@code outer}. */
    public boolean inside(HudRect outer) {
        return x >= outer.x && y >= outer.y && right() <= outer.right() && bottom() <= outer.bottom();
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }
}
