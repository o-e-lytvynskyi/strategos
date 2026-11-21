package dev.strategos.mod.server.commander;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommanderGuardTest {
    @Test
    void finiteCoordinatesKeepTheirExactInteriorOrNearestBoundary() {
        assertThat(CommanderGuard.bounded(21.25, -32, 96)).isEqualTo(21.25);
        assertThat(CommanderGuard.bounded(-32, -32, 96)).isEqualTo(-32);
        assertThat(CommanderGuard.bounded(96, -32, 96)).isEqualTo(96);
        assertThat(CommanderGuard.bounded(-100, -32, 96)).isEqualTo(-32);
        assertThat(CommanderGuard.bounded(100, -32, 96)).isEqualTo(96);
    }

    @Test
    void everyNonfiniteCoordinateHasAFiniteInteriorRecovery() {
        for (double value : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            double corrected = CommanderGuard.bounded(value, -300, -100);
            assertThat(corrected).isFinite().isStrictlyBetween(-300.0, -100.0);
            assertThat(CommanderGuard.bounded(corrected, -300, -100)).isEqualTo(corrected);
        }
    }
}
