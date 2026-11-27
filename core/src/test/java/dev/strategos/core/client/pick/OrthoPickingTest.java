package dev.strategos.core.client.pick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * CAM-06.1: in the orthographic view the cursor picks exactly the ground point the matrix projects onto it, at any
 * pose, and the perspective view's picking is unchanged.
 */
class OrthoPickingTest {
    private static final double WIDTH = 1280;
    private static final double HEIGHT = 720;
    private static final double FAR = 1000;
    private static final double FOV = 55;

    @Test
    void theCursorPicksTheGroundPointThatProjectsOntoIt() {
        for (double pitch : new double[] {35, 55, 70}) {
            for (double yaw : new double[] {0, 37, 90, 200}) {
                for (double height : new double[] {8, 24, 64}) {
                    View view = view(true, pitch, yaw, height);
                    assertThat(view.orthographic()).isTrue();
                    for (double nx = 0.05; nx < 1; nx += 0.15) {
                        for (double ny = 0.05; ny < 1; ny += 0.15) {
                            double x = nx * WIDTH;
                            double y = ny * HEIGHT;
                            Picking.Ray ray = Picking.ray(view, x, y);
                            double t = (64 - ray.oy()) / ray.dy(); // the ground plane
                            double[] ground = ray.at(t);

                            double[] back = Picking.project(view, ground[0], ground[1], ground[2]);

                            assertThat(back).as("in front").isNotNull();
                            assertThat(back[0]).isCloseTo(x, within(1e-6));
                            assertThat(back[1]).isCloseTo(y, within(1e-6));
                        }
                    }
                }
            }
        }
    }

    @Test
    void theRaysAreParallelToTheViewDirection() {
        View view = view(true, 55, 0, 24);

        Picking.Ray a = Picking.ray(view, 10, 20);
        Picking.Ray b = Picking.ray(view, 1200, 700);

        assertThat(a.dx()).isCloseTo(b.dx(), within(1e-9));
        assertThat(a.dy()).isCloseTo(b.dy(), within(1e-9));
        assertThat(a.dz()).isCloseTo(b.dz(), within(1e-9));
        assertThat(a.dy()).isCloseTo(-Math.sin(Math.toRadians(55)), within(1e-9));
    }

    @Test
    void theCentreRayHitsTheFocusAndTheSizeFollowsTheHeight() {
        View view = view(true, 55, 0, 24);

        Picking.Ray centre = Picking.ray(view, WIDTH / 2, HEIGHT / 2);
        double[] ground = centre.at((64 - centre.oy()) / centre.dy());

        assertThat(ground[0]).isCloseTo(100, within(1e-6));
        assertThat(ground[2]).isCloseTo(100, within(1e-6));
        assertThat(Orthographic.halfHeight(48, 55, FOV))
                .isCloseTo(2 * Orthographic.halfHeight(24, 55, FOV), within(1e-9));
    }

    @Test
    void perspectivePickingIsUnchanged() {
        View view = view(false, 55, 0, 24);

        assertThat(view.orthographic()).isFalse();
        double[] t = {100.5, 64, 120.5};
        double[] window = Picking.project(view, t[0], t[1], t[2]);
        assertThat(window).isNotNull();
        Picking.Ray ray = Picking.ray(view, window[0], window[1]);
        double s = ((t[0] - ray.ox()) * ray.dx() + (t[1] - ray.oy()) * ray.dy() + (t[2] - ray.oz()) * ray.dz());
        double[] q = ray.at(s);
        assertThat(Math.hypot(Math.hypot(q[0] - t[0], q[1] - t[1]), q[2] - t[2]))
                .isLessThan(1e-6);
    }

    /** The eye {@code height} above the ground (y 64) at the focus (100, 100), looking along {@code yaw}, pitched down. */
    private static View view(boolean orthographic, double pitch, double yawDegrees, double height) {
        double aspect = WIDTH / HEIGHT;
        double[] projection;
        if (orthographic) {
            projection = Orthographic.matrix(height, pitch, FOV, aspect, FAR);
        } else {
            double f = 1 / Math.tan(Math.toRadians(FOV) / 2);
            double near = 0.05;
            projection = new double[] {
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
                (FAR + near) / (near - FAR),
                -1,
                0,
                0,
                2 * FAR * near / (near - FAR),
                0
            };
        }
        double p = Math.toRadians(pitch);
        double yaw = Math.toRadians(yawDegrees);
        // Minecraft's view: rotate by pitch about X, then by yaw + 180° about Y
        double cy = Math.cos(yaw + Math.PI);
        double sy = Math.sin(yaw + Math.PI);
        double[] turn = {cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1};
        double c = Math.cos(p);
        double s = Math.sin(p);
        double[] tilt = {1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1};
        // the eye sits back from the focus along the view, at the given height
        double back = height / Math.tan(p);
        double eyeX = 100 + Math.sin(yaw) * back;
        double eyeZ = 100 - Math.cos(yaw) * back;
        return new View(projection, Mat4.mul(tilt, turn), eyeX, 64 + height, eyeZ, WIDTH, HEIGHT);
    }
}
