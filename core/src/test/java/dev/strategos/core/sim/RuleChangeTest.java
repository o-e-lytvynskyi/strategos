package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * MATCH-01.3: a rule change is a command, journaled with the tick it applied in; a replay of the journal applies it at
 * the same tick and ends at the same hash, which differs from the run without it. Only an admin changes rules, and an
 * unknown key or a value out of range is refused.
 */
class RuleChangeTest {
    @Test
    void journaledAndReplayedToTheSameHash() {
        SimHarness h = SimHarness.builder().build();
        SimHarness plain = SimHarness.builder().build();
        for (SimHarness run : new SimHarness[] {h, plain}) {
            run.spawn(new FactionSlot(0), new Vec2d(8, 8), 4, 2.0);
            run.runTicks(10);
        }
        h.submit(PlayerSlot.ADMIN, new RuleChange("populationMax", "40"));
        h.submit(PlayerSlot.ADMIN, new RuleChange("victory", "civic_centre"));
        h.submit(PlayerSlot.ADMIN, new RuleChange("revealMap", "true"));
        h.step();
        plain.step();
        assertThat(h.results().subList(h.results().size() - 3, h.results().size()))
                .extracting(CommandResult::outcome)
                .containsOnly(CommandOutcome.ACCEPTED);
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.config().rules().economy().populationMax()).isEqualTo(40);
        assertThat(s.config().rules().play()).isEqualTo(new PlayRules(PlayRules.Victory.CIVIC_CENTRE, true, false));
        assertThat(s.population().cap(0))
                .as("no housing yet: the cap is the base, under the new maximum")
                .isZero();
        h.runTicks(30);
        plain.runTicks(30);
        assertThat(h.stateHash()).as("the rules are part of the state").isNotEqualTo(plain.stateHash());

        ReplayResult replay = ReplayDriver.replay(h.core().journal(), TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());

        h.submit(new PlayerSlot(0), new RuleChange("populationMax", "200"));
        h.submit(PlayerSlot.ADMIN, new RuleChange("populationMax", "0"));
        h.submit(PlayerSlot.ADMIN, new RuleChange("fog", "on"));
        h.submit(PlayerSlot.ADMIN, new RuleChange("victory", "conquest"));
        h.step();
        assertThat(h.results().subList(h.results().size() - 4, h.results().size()))
                .extracting(CommandResult::firstReason)
                .as("in the tick's canonical order")
                .containsExactlyInAnyOrder(
                        RejectReason.NOT_PERMITTED,
                        RejectReason.INVALID_ARGUMENT,
                        RejectReason.INVALID_ARGUMENT,
                        RejectReason.INVALID_ARGUMENT);
        assertThat(s.config().rules().economy().populationMax()).isEqualTo(40);
    }
}
