package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MATCH-16.3: the rule is journaled and the night derived, so a match that switches day and night on mid-match replays
 * to the same hash, a match restored in the middle of the night (its stats start empty) steps to the live hashes, and
 * a match with the rule off never differs from one that never heard of it.
 */
class NightReplayTest {
    private static final int NIGHT = DayClock.NIGHT_FROM - DayClock.START;
    private static final int DAWN = DayClock.DAWN_FROM - DayClock.START;

    private static SimHarness harness() {
        return SimHarness.builder().size(96, 96).factions(2).build();
    }

    @Test
    void aRuleChangedMidMatchReplaysAndRestoresToTheLiveHash() {
        SimHarness live = harness();
        SimHarness plain = harness();
        int[] walkers = null;
        for (SimHarness run : List.of(live, plain)) {
            walkers = run.spawn(new FactionSlot(0), new Vec2d(8, 8), 6, 2.0);
            run.spawn(new FactionSlot(1), new Vec2d(60, 60), 6, 2.0);
            run.runTicks(3_000);
        }
        live.submit(PlayerSlot.ADMIN, new RuleChange("dayNight", "true"));
        live.runTicks(NIGHT + 200 - (int) live.tick());
        plain.runTicks(NIGHT + 200 - (int) plain.tick());
        MatchState s = KernelAccess.state(live.core());
        assertThat(s.config().rules().play().dayNight()).isTrue();
        assertThat(NightFactor.active(s)).isTrue();
        assertThat(live.stateHash()).as("the rule is part of the state").isNotEqualTo(plain.stateHash());

        SimHarness restored = SimHarness.restore(roundTrip(live.core().snapshot()), TemplateSet.builtin());
        MatchState r = KernelAccess.state(restored.core());
        assertThat(restored.stateHash()).isEqualTo(live.stateHash());
        assertThat(r.stats().vision(0))
                .as("the night is derived again at the restored tick")
                .isEqualTo(s.stats().vision(0))
                .isLessThan(12);
        for (int t = 0; t < DAWN - NIGHT; t++) { // through the dawn
            if (t % 400 == 0) { // the walkers wander on, so the explored cells keep moving
                Move move =
                        new Move(UnitSet.of(walkers), new Vec2d(10 + t / 20.0, 10 + t / 20.0), QueueMode.REPLACE, 0);
                live.submit(PlayerSlot.ADMIN, move);
                restored.submit(PlayerSlot.ADMIN, move);
            }
            live.step();
            restored.step();
            assertThat(restored.stateHash())
                    .withFailMessage("the restored match diverged at tick %d", live.tick())
                    .isEqualTo(live.stateHash());
        }
        assertThat(r.stats().vision(0)).as("the dawn comes to both").isEqualTo(12);

        ReplayResult replay = ReplayDriver.replay(live.core().journal(), TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(live.stateHash());
    }

    @Test
    void theRuleOffHashesAsBefore() {
        SimHarness a = harness();
        SimHarness b = harness();
        for (SimHarness run : List.of(a, b)) {
            run.spawn(new FactionSlot(0), new Vec2d(8, 8), 3, 2.0);
        }
        a.submit(PlayerSlot.ADMIN, new RuleChange("dayNight", "false"));
        a.runTicks(NIGHT + 50);
        b.runTicks(NIGHT + 50);
        assertThat(a.stateHash()).as("off is the default: nothing is hashed").isEqualTo(b.stateHash());
    }

    private static MatchSnapshot roundTrip(MatchSnapshot saved) {
        return MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, saved)),
                        Migrations.production())
                .getOrThrow();
    }
}
