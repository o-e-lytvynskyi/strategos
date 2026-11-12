package dev.strategos.core.client.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** CMB-04: the arrow's path joins the two ends, peaks at a twentieth of the way's length and points along the way. */
class ShotArcTest {
    private static final double EPS = 1e-9;

    @Test
    void theArcStartsAtTheReleaseAndEndsAtTheLanding() {
        ShotArc arc = new ShotArc(1, 5, 2, 41, 3, 32);
        double[] p = new double[3];
        arc.position(0, p);
        assertThat(p).containsExactly(1, 5, 2);
        arc.position(1, p);
        assertThat(p).containsExactly(41, 3, 32);
        arc.position(-3, p);
        assertThat(p).containsExactly(1, 5, 2); // clamped
    }

    @Test
    void theApexLiftsATwentiethOfTheHorizontalDistance() {
        ShotArc arc = new ShotArc(0, 4, 0, 30, 4, 40); // 50 blocks sideways, level
        double[] p = new double[3];
        arc.position(0.5, p);
        assertThat(p[0]).isCloseTo(15, within(EPS));
        assertThat(p[2]).isCloseTo(20, within(EPS));
        assertThat(p[1]).isCloseTo(4 + 0.05 * 50, within(EPS));
    }

    @Test
    void aShotThatDoesNotTravelSidewaysIsNotLifted() {
        ShotArc up = new ShotArc(3, 1, 3, 3, 9, 3);
        double[] p = new double[3];
        up.position(0.5, p);
        assertThat(p).containsExactly(3, 5, 3);
        ShotArc same = new ShotArc(3, 1, 3, 3, 1, 3);
        same.position(0.5, p);
        assertThat(p).containsExactly(3, 1, 3);
        same.tangent(0.5, p);
        assertThat(p).containsExactly(0, -1, 0);
    }

    @Test
    void theTangentRisesThenFallsAlongTheWay() {
        ShotArc arc = new ShotArc(0, 0, 0, 100, 0, 0); // apex 5
        double[] t = new double[3];
        arc.tangent(0, t);
        assertThat(t[0]).isCloseTo(100 / Math.hypot(100, 20), within(EPS));
        assertThat(t[1]).isCloseTo(20 / Math.hypot(100, 20), within(EPS));
        arc.tangent(0.5, t);
        assertThat(t).containsExactly(1, 0, 0);
        arc.tangent(1, t);
        assertThat(t[0]).isCloseTo(100 / Math.hypot(100, 20), within(EPS));
        assertThat(t[1]).isCloseTo(-20 / Math.hypot(100, 20), within(EPS));
        assertThat(Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2])).isCloseTo(1, within(EPS));
    }
}
