package dev.strategos.core.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DetMathTest {

    @Test
    void yawFollowsMinecraftsConvention() {
        assertThat(DetMath.yawDegrees(0, 1)).isEqualTo(0f);
        assertThat(DetMath.yawDegrees(-1, 0)).isEqualTo(90f);
        assertThat(DetMath.yawDegrees(0, -1)).isEqualTo(-180f);
        assertThat(DetMath.yawDegrees(1, 0)).isEqualTo(-90f);
    }

    @Test
    void wrapsDegreesIntoTheHalfOpenRange() {
        assertThat(DetMath.wrapDegrees(180)).isEqualTo(-180);
        assertThat(DetMath.wrapDegrees(-180)).isEqualTo(-180);
        assertThat(DetMath.wrapDegrees(540)).isEqualTo(-180);
        assertThat(DetMath.wrapDegrees(359)).isEqualTo(-1);
        assertThat(DetMath.wrapDegrees(-181)).isEqualTo(179);
    }

    @Test
    void atan2IsBitEqualToStrictMath() {
        double[] values = {-3.5, -1, -0.0, 0.0, 1e-300, 0.25, 1, 7.75, Double.MAX_VALUE};
        for (double y : values) {
            for (double x : values) {
                assertThat(Double.doubleToRawLongBits(DetMath.atan2(y, x)))
                        .as("atan2(%s, %s)", y, x)
                        .isEqualTo(Double.doubleToRawLongBits(StrictMath.atan2(y, x)));
            }
        }
    }

    @Test
    void lengthIsTheSquareRootOfTheSumOfSquares() {
        assertThat(DetMath.length(3, 4)).isEqualTo(5.0);
        assertThat(DetMath.length(1.5, -2.25)).isEqualTo(Math.sqrt(1.5 * 1.5 + 2.25 * 2.25));
        assertThat(new Vec2d(1, 1).distanceTo(new Vec2d(4, 5))).isEqualTo(5.0);
    }
}
