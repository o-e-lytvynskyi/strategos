package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Resign;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * MATCH-01.2: under the civic-centre rule, the match ends the very tick the last enemy centre falls, its builder's
 * faction the winner, and a save after the end restores it ended; a faction that resigns loses the same way; with no
 * victory rule nothing ends.
 */
class VictoryTest {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");

    /** Two centres on a flat 96 × 64, under {@code victory}. */
    static final class Scene {
        final SimHarness h;
        final MatchState s;

        Scene(PlayRules.Victory victory) {
            h = SimHarness.builder()
                    .size(96, 64)
                    .yBand(0, 31, 1)
                    .rules(MatchRules.DEFAULTS.withPlay(new PlayRules(victory, false, false)))
                    .build();
            h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 4, 4, 0));
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(1), 70, 40, 0));
            h.step();
            h.step();
            s = KernelAccess.state(h.core());
        }

        int centreOf(int faction) {
            for (int handle : s.buildings().handles()) {
                if (s.buildings().faction(handle) == faction) {
                    return handle;
                }
            }
            throw new AssertionError("no centre of faction " + faction);
        }
    }

    final Scene scene = new Scene(PlayRules.Victory.CIVIC_CENTRE);
    final SimHarness h = scene.h;
    final MatchState s = scene.s;

    int centreOf(int faction) {
        return scene.centreOf(faction);
    }

    @Test
    void theLastCivicCentreEndsTheMatchWithin20Ticks() {
        assertThat(s.outcome().held(0) && s.outcome().held(1)).isTrue();
        h.runTicks(40);
        assertThat(h.events(MatchEnded.class))
                .as("two centres stand: on it goes")
                .isEmpty();
        s.queueDamage(Damage.lethal(centreOf(1), Handle.NONE));
        long fell = -1;
        long ended = -1;
        for (int t = 0; t < 20 && ended < 0; t++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof BuildingDestroyed) {
                    fell = out.tick();
                }
                if (e instanceof MatchEnded) {
                    ended = out.tick();
                }
            }
        }
        assertThat(fell).isPositive();
        assertThat(ended - fell).as("ended within 20 ticks of the fall").isBetween(0L, 20L);
        assertThat(h.events(FactionDefeated.class))
                .containsExactly(new FactionDefeated(new FactionSlot(1), FactionDefeated.CENTRE_LOST));
        MatchEnded end = h.events(MatchEnded.class).get(0);
        assertThat(end.winner()).isZero();
        assertThat(end.ticks()).isEqualTo(ended);
        h.runTicks(40);
        assertThat(h.events(MatchEnded.class)).as("it ends once").hasSize(1);

        SimHarness back = SimHarness.restore(h.core().snapshot(), s.templates());
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        MatchState restored = KernelAccess.state(back.core());
        assertThat(restored.outcome().ended()).isTrue();
        assertThat(restored.outcome().winner()).isZero();
    }

    @Test
    void aFactionThatResignsLoses() {
        h.submit(new PlayerSlot(0), new Resign(new FactionSlot(1)));
        h.step();
        assertThat(h.results().get(h.results().size() - 1).firstReason())
                .as("only its own")
                .isEqualTo(RejectReason.NOT_PERMITTED);
        h.submit(new PlayerSlot(1), new Resign(new FactionSlot(1)));
        h.step();
        assertThat(h.events(FactionDefeated.class))
                .containsExactly(new FactionDefeated(new FactionSlot(1), FactionDefeated.RESIGNED));
        assertThat(h.events(MatchEnded.class))
                .singleElement()
                .extracting(MatchEnded::winner)
                .isEqualTo(0);
        assertThat(s.buildings().alive(centreOf(1)))
                .as("its centre still stands")
                .isTrue();
    }

    @Test
    void withoutAVictoryRuleNothingEnds() {
        Scene none = new Scene(PlayRules.Victory.NONE);
        none.s.queueDamage(Damage.lethal(none.centreOf(1), Handle.NONE));
        none.h.runTicks(40);
        assertThat(none.h.events(MatchEnded.class)).isEmpty();
        assertThat(none.s.outcome().empty()).as("and hashes as before").isTrue();
        none.h.submit(new PlayerSlot(0), new Resign(new FactionSlot(0)));
        none.h.step();
        assertThat(none.h.results().get(none.h.results().size() - 1).firstReason())
                .isEqualTo(RejectReason.UNSUPPORTED);
    }
}
