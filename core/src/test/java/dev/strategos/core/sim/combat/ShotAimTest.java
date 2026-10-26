package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.testkit.Seeds;
import org.junit.jupiter.api.Test;

class ShotAimTest {
    @Test
    void zeroSpreadLeadsByCeiledHorizontalFlightAndFreezesHeight() {
        var aim = ShotAim.aim(Seeds.first("canary"), 7, 1, launch(20, 0, 0.25, -0.5, 3, 0));
        assertThat(aim.flightTicks()).isEqualTo(7);
        assertThat(aim.landTick()).isEqualTo(14);
        assertThat(aim.landingX()).isEqualTo(21.75);
        assertThat(aim.landingY()).isEqualTo(4);
        assertThat(aim.landingZ()).isEqualTo(-3.5);
        assertThat(ShotAim.aim(7, 0, 1, launch(0, 0, 1, 1, 3, 0)).flightTicks()).isZero();
    }

    @Test
    void gaussianOffsetsScaleLinearlyWithSpreadAndDistance() {
        long seed = Seeds.first("canary");
        var one = ShotAim.aim(seed, 7, 1, launch(20, 0, 0, 0, 3, 2.25));
        var twice = ShotAim.aim(seed, 7, 1, launch(20, 0, 0, 0, 3, 4.5));
        var farther = ShotAim.aim(seed, 7, 1, launch(40, 0, 0, 0, 3, 2.25));
        assertThat(twice.landingX() - 20).isCloseTo(2 * (one.landingX() - 20), within(1e-12));
        assertThat(twice.landingZ()).isCloseTo(2 * one.landingZ(), within(1e-12));
        assertThat(farther.landingX() - 40).isCloseTo(2 * (one.landingX() - 20), within(1e-12));
        assertThat(farther.landingZ()).isCloseTo(2 * one.landingZ(), within(1e-12));
        assertThat(ShotAim.aim(seed, 7, 1, launch(20, 0, 0, 0, 3, 2.25))).isEqualTo(one);
        assertThat(ShotAim.aim(seed, 7, 2, launch(20, 0, 0, 0, 3, 2.25))).isNotEqualTo(one);
    }

    @Test
    void rejectsInvalidArithmeticWithoutClamping() {
        for (double speed : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE})
            assertThatThrownBy(() -> ShotAim.aim(7, 1, 1, launch(20, 0, 0, 0, speed, 0)))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShotAim.aim(7, Long.MAX_VALUE, 1, launch(20, 0, 0, 0, 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShotAim.aim(7, 0, 1, launch(20, 0, Double.MAX_VALUE, 0, 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShotAim.aim(7, 0, 1, launch(Double.MAX_VALUE, 0, 0, 0, 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShotAim.aim(7, 0, Long.MAX_VALUE, launch(20, 0, 0, 0, 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static ShotStore.Launch launch(double x, double z, double vx, double vz, double speed, double spread) {
        return new ShotStore.Launch(
                0, 0, x, 4, z, vx, vz, speed, spread, Damage.of(1048577, 1048576, DamageType.PIERCE, 2));
    }
}
