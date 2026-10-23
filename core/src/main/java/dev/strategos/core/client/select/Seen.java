package dev.strategos.core.client.select;

/**
 * A unit the client sees: its handle, faction, template, and where its feet and head project in window points (NaN
 * when behind the camera).
 */
public record Seen(int handle, int faction, int template, double feetX, double feetY, double headX, double headY) {
    /** Whether its feet or its head project inside the rectangle; a unit half off the screen counts. */
    public boolean inside(double x0, double y0, double x1, double y1) {
        return in(feetX, feetY, x0, y0, x1, y1) || in(headX, headY, x0, y0, x1, y1);
    }

    private static boolean in(double x, double y, double x0, double y0, double x1, double y1) {
        return x >= Math.min(x0, x1) && x <= Math.max(x0, x1) && y >= Math.min(y0, y1) && y <= Math.max(y0, y1);
    }
}
