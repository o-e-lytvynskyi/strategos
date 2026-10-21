package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.FakeBodies.Pose;
import java.util.stream.LongStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** ORD-01.3 in L1: for the seeds of skeleton-20, the walking skeleton arrives exactly at tick 81 with the plain trace. */
class WalkingSkeletonTest {
    static LongStream seeds() {
        return Seeds.set("skeleton-20");
    }

    @ParameterizedTest(name = "seed={0}")
    @MethodSource("seeds")
    void walksExactlyOntoItsTargetAtTick81(long seed) {
        Scenario scenario = Scenarios.walkingSkeleton(seed);
        SimHarness h = SimHarness.forScenario(scenario);

        ScenarioReport report = h.play(scenario);

        assertThat(report.failure()).as("seed=%d", seed).isNull();
        assertThat(report.ticks()).as("seed=%d", seed).isEqualTo(81);
        int unit = h.queries().unitHandles()[0];
        UnitView view = h.queries().unit(unit);
        assertThat(view).isNotNull();
        assertThat(view.state()).isEqualTo(UnitState.IDLE);
        assertThat(view.x()).as("seed=%d", seed).isEqualTo(4.5 + Math.floorMod(seed, 5) + 20);
        assertThat(view.z()).as("seed=%d", seed).isEqualTo(8.5 + 2 * Math.floorMod(seed, 7));
        assertThat(h.stateChanges())
                .extracting(c -> c.from() + "->" + c.to())
                .as("seed=%d", seed)
                .containsExactly("IDLE->WALKING", "WALKING->IDLE");
        Pose body = h.bodies().pose(unit);
        assertThat(body).as("the body").isNotNull();
        assertThat(body.x()).isEqualTo(view.x());
        assertThat(body.z()).isEqualTo(view.z());
        assertThat(body.tick()).as("the last frame that moved the body").isEqualTo(81);
    }
}
