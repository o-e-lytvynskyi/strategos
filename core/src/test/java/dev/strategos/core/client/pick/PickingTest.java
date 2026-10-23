package dev.strategos.core.client.pick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * CAM-04.1: for known matrices and cursors, the ray picks the expected block at GUI scales 1–4 and framebuffer scales
 * 1× and 2×; the cursor is normalised by the window's points, and the framebuffer's pixels would miss.
 */
class PickingTest {
    private static final double WIDTH = 1280;
    private static final double HEIGHT = 720;

    @Test
    void theRayPicksTheBlockAtEveryScale() {
        View view = view(WIDTH, HEIGHT);
        double[][] targets = {{100.5, 64, 120.5}, {93.5, 64, 110.5}, {108.5, 64, 131.5}, {100.5, 70, 118.5}};
        for (int gui = 1; gui <= 4; gui++) {
            for (int framebuffer = 1; framebuffer <= 2; framebuffer++) {
                for (double[] t : targets) {
                    double[] window = Picking.project(view, t[0], t[1], t[2]);
                    assertThat(window).as("in view").isNotNull();
                    double guiWidth = WIDTH / gui;
                    double guiHeight = HEIGHT / gui;
                    double cursorX = window[0] * guiWidth / WIDTH; // a screen's mouse event, in GUI pixels
                    double cursorY = window[1] * guiHeight / HEIGHT;
                    Picking.Ray ray = Picking.ray(
                            view,
                            Picking.toWindow(cursorX, guiWidth, WIDTH),
                            Picking.toWindow(cursorY, guiHeight, HEIGHT));
                    double along =
                            Picking.hit(ray, t[0] - 0.5, t[1] - 0.5, t[2] - 0.5, t[0] + 0.5, t[1] + 0.5, t[2] + 0.5);
                    assertThat(along)
                            .as("gui %d, framebuffer %dx, %s", gui, framebuffer, t)
                            .isPositive();
                    assertThat(distance(ray, t)).isCloseTo(0, within(1e-6));
                }
            }
        }
    }

    @Test
    void framebufferPixelsWouldMiss() {
        View points = view(WIDTH, HEIGHT);
        View pixels = view(WIDTH * 2, HEIGHT * 2); // the framebuffer of a Retina window, wrongly taken
        double[] t = {108.5, 64, 131.5};
        double[] window = Picking.project(points, t[0], t[1], t[2]);
        assertThat(window).isNotNull();

        Picking.Ray wrong = Picking.ray(pixels, window[0], window[1]);

        assertThat(distance(wrong, t)).isGreaterThan(5);
    }

    @Test
    void aRayMissesABoxBesideIt() {
        Picking.Ray ray = new Picking.Ray(0, 10, 0, 0, -1, 0);

        assertThat(Picking.hit(ray, -0.5, 0, -0.5, 0.5, 1, 0.5)).isCloseTo(9, within(1e-9));
        assertThat(Picking.hit(ray, 1, 0, 1, 2, 1, 2)).isEqualTo(-1);
    }

    /**
     * A camera at (100, 88, 100) looking south (+Z) and 55° down, FOV 55° vertical, as the rig poses it; the
     * model-view only rotates.
     */
    private static View view(double width, double height) {
        double aspect = width / height;
        double f = 1 / Math.tan(Math.toRadians(55) / 2);
        double near = 0.05;
        double far = 1000;
        double[] projection = {
            f / aspect,
            0,
            0,
            0,
            0,
            f,
            0,
            0,
            0,
            0,
            (far + near) / (near - far),
            -1,
            0,
            0,
            2 * far * near / (near - far),
            0
        };
        double pitch = Math.toRadians(55);
        // looking along +Z: a rotation of 180° about Y, then the pitch about X
        double[] yaw = {-1, 0, 0, 0, 0, 1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1};
        double c = Math.cos(pitch);
        double s = Math.sin(pitch);
        double[] tilt = {1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1};
        return new View(projection, Mat4.mul(tilt, yaw), 100, 88, 100, width, height);
    }

    private static double distance(Picking.Ray ray, double[] p) {
        double vx = p[0] - ray.ox();
        double vy = p[1] - ray.oy();
        double vz = p[2] - ray.oz();
        double t = vx * ray.dx() + vy * ray.dy() + vz * ray.dz();
        double[] q = ray.at(t);
        return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
    }
}
