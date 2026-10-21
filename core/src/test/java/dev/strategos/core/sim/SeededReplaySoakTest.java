package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.core.testkit.ReplayRunner.ReplayReport;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** CORE-05.1: for every seed of m0-200, 10,000 ticks replay to the live hash at every checkpoint; run by :core:soak. */
@Tag("soak")
class SeededReplaySoakTest {
    static LongStream seeds() {
        return Seeds.set("m0-200");
    }

    @ParameterizedTest(name = "seed={0}")
    @MethodSource("seeds")
    void tenThousandTicksReplayToTheLiveHash(long seed) {
        SimHarness h = SeededRuns.run(seed, 10_000);

        ReplayReport report = ReplayRunner.check(h.core().journal(), TemplateSet.builtin(), h.stateHash());

        assertThat(report.ok())
                .withFailMessage("seed=%d: %s", seed, report.message())
                .isTrue();
        assertThat(h.core().journal().checkpointTicks())
                .withFailMessage("seed=%d: not one checkpoint every 20 ticks", seed)
                .hasSize(500);
    }
}
