package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.stream.LongStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * QA-02.2, QA-02.3: 1,000 ticks of random commands per seed of m0-200. The harness checks the invariants after every
 * tick and throws {@code seed=<n> tick=<t>: ...}; every assertion here starts its message with {@code seed=<n>}, and
 * {@code -Dstrategos.seed=<n>} reruns one seed alone.
 */
class SeededInvariantTest {
    static LongStream seeds() {
        return Seeds.set("m0-200");
    }

    @ParameterizedTest(name = "seed={0}")
    @MethodSource("seeds")
    void theInvariantsHoldAfterEveryTick(long seed) {
        SimHarness h = SeededRuns.run(seed, 1_000);

        assertThat(h.tick())
                .withFailMessage("seed=%d: the run stopped at tick %d", seed, h.tick())
                .isEqualTo(1_002);
        assertThat(h.invariantViolations())
                .withFailMessage("seed=%d: %s", seed, h.invariantViolations())
                .isEmpty();
        assertThat(h.queries().unitCount())
                .withFailMessage(
                        "seed=%d: %d units alive, not 40", seed, h.queries().unitCount())
                .isEqualTo(40);
        assertThat(h.results())
                .withFailMessage("seed=%d: the run sent no refused command", seed)
                .anyMatch(r -> r.outcome() == CommandOutcome.REJECTED);
    }
}
