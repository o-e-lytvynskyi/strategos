package dev.strategos.core.client.pick;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** CAM-07.2: the crosshair's ray picks the body it points at, the nearest of two in line, and none behind a wall. */
class CrosshairPickTest {
    private static CrosshairPick.Body body(int handle, double x, double z) {
        return new CrosshairPick.Body(handle, x - 0.3, 64, z - 0.3, x + 0.3, 65.8, z + 0.3);
    }

    @Test
    void theRayPicksTheBodyInViewAndTheNearestInLine() {
        // standing at (0, 64, 0) facing +X (yaw -90), looking a little down at chest height ten blocks off
        Picking.Ray ray = CrosshairPick.ray(0, 65.62, 0, -90, Math.toDegrees(Math.atan2(0.62 - 0.9, 10)) * -1);
        List<CrosshairPick.Body> bodies = List.of(body(7, 10, 0), body(9, 14, 0), body(5, 10, 3));
        assertThat(CrosshairPick.pick(ray, bodies, Double.POSITIVE_INFINITY)).isEqualTo(7);
        assertThat(CrosshairPick.pick(ray, bodies.subList(1, 3), Double.POSITIVE_INFINITY))
                .as("the farther one in line, once the nearer is gone")
                .isEqualTo(9);
        assertThat(CrosshairPick.pick(ray, bodies, 6))
                .as("a block at 6 hides them")
                .isZero();
        Picking.Ray aside = CrosshairPick.ray(0, 65.62, 0, -60, 0);
        assertThat(CrosshairPick.pick(aside, bodies, Double.POSITIVE_INFINITY))
                .as("looking aside")
                .isZero();
    }

    @Test
    void yawAndPitchPointAsMinecraftsDo() {
        Picking.Ray south = CrosshairPick.ray(0, 0, 0, 0, 0);
        assertThat(south.dz()).isCloseTo(1, org.assertj.core.data.Offset.offset(1e-9));
        Picking.Ray west = CrosshairPick.ray(0, 0, 0, 90, 0);
        assertThat(west.dx()).isCloseTo(-1, org.assertj.core.data.Offset.offset(1e-9));
        Picking.Ray down = CrosshairPick.ray(0, 0, 0, 0, 90);
        assertThat(down.dy()).isCloseTo(-1, org.assertj.core.data.Offset.offset(1e-9));
    }
}
