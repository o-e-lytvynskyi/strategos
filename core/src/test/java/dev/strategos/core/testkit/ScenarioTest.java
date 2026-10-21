package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** QA-02: every shared scenario passes in L1 and reports the hash of the tick it stopped at. */
class ScenarioTest {
    static Stream<String> ids() {
        return Scenarios.all().stream().map(Scenario::id);
    }

    /** A fresh instance: a scenario remembers its own run. */
    private static Scenario fresh(String id) {
        return Scenarios.all().stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ids")
    void passesAndReportsTheHashOfItsLastTick(String id) {
        Scenario scenario = fresh(id);
        SimHarness h = SimHarness.forScenario(scenario);

        ScenarioReport report = h.play(scenario);

        assertThat(report.failure()).as(id).isNull();
        assertThat(report.passed()).isTrue();
        assertThat(report.id()).isEqualTo(id);
        assertThat(report.ticks()).isEqualTo(h.tick()).isLessThan(scenario.timeoutTicks());
        assertThat(report.finalHash()).isEqualTo(h.stateHash());
        assertThat(SimHarness.run(fresh(id))).as("a second run").isEqualTo(report);
    }

    @Test
    void idsAreUniqueLowerSnakeCase() {
        List<String> ids = ids().toList();
        assertThat(ids).containsExactly("walking_skeleton", "out_of_bounds", "stop_mid_walk");
        assertThat(ids).allMatch(id -> id.matches("[a-z][a-z0-9]*(_[a-z0-9]+)*"));
    }

    @Test
    void aFailedVerificationIsReportedWithSeedAndTick() {
        ScenarioReport report = SimHarness.run(new Probe(3, 50) {
            @Override
            public void verify(ScenarioContext ctx) {
                throw new ScenarioFailure("the probe says no");
            }
        });

        assertThat(report.passed()).isFalse();
        assertThat(report.ticks()).isEqualTo(3);
        assertThat(report.failure()).isEqualTo("seed=" + SimHarness.DEFAULT_SEED + " tick=3: the probe says no");
    }

    @Test
    void aScenarioThatNeverFinishesTimesOut() {
        ScenarioReport report = SimHarness.run(new Probe(Long.MAX_VALUE, 25));

        assertThat(report.passed()).isFalse();
        assertThat(report.ticks()).isEqualTo(25);
        assertThat(report.failure())
                .startsWith("seed=" + SimHarness.DEFAULT_SEED + " tick=25: ")
                .contains("timed out");
    }

    /** Done at a fixed tick; an empty match. */
    private static class Probe implements Scenario {
        private final long doneAt;
        private final int timeout;

        Probe(long doneAt, int timeout) {
            this.doneAt = doneAt;
            this.timeout = timeout;
        }

        @Override
        public String id() {
            return "probe";
        }

        @Override
        public int sizeX() {
            return 16;
        }

        @Override
        public int sizeZ() {
            return 16;
        }

        @Override
        public long seed() {
            return SimHarness.DEFAULT_SEED;
        }

        @Override
        public int timeoutTicks() {
            return timeout;
        }

        @Override
        public void setup(ScenarioContext ctx) {}

        @Override
        public boolean isDone(ScenarioContext ctx) {
            return ctx.tick() >= doneAt;
        }

        @Override
        public void verify(ScenarioContext ctx) {}
    }
}
