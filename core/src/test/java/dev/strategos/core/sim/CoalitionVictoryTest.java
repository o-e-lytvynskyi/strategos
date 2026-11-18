package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** T7.2: all defeats are evaluated before choosing an ordered surviving coalition, once. */
class CoalitionVictoryTest {
    private record Scene(SimHarness h, MatchState state) {
        void eliminate(int... factions) {
            for (int faction : factions) {
                for (int building : state.buildings().handles()) {
                    if (state.buildings().faction(building) == faction) {
                        state.queueDamage(Damage.lethal(building, Handle.NONE));
                    }
                }
                for (int i = 0; i < state.entities().highWater(); i++) {
                    if (state.entities().aliveAt(i) && state.identity().faction(i) == faction) {
                        state.queueDamage(Damage.lethal(state.entities().handleAt(i), Handle.NONE));
                    }
                }
            }
            h.runTicks(20);
        }
    }

    private static Scene scene(PlayRules.Victory rule, boolean locked) {
        SimHarness h = SimHarness.builder()
                .size(96, 96)
                .yBand(0, 31, 1)
                .factions(4)
                .rules(MatchRules.DEFAULTS.withPlay(new PlayRules(rule, false, false, false)))
                .build();
        h.publishAll(VoxelFixtures.flat(96, 96, 32, 1));
        DiplomacyState teams =
                DiplomacyState.identity(4).withTeam(new FactionSlot(1), 0).withTeam(new FactionSlot(3), 2);
        h.schedule(1, new DiplomacySetup((locked ? teams.lockTeams() : teams).snapshot()));
        for (int f = 0; f < 4; f++) {
            int x = 4 + (f % 2) * 56;
            int z = 4 + (f / 2) * 56;
            h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(new TemplateId("strategos:building/civic_centre"), new FactionSlot(f), x, z, 0));
        }
        h.runTicks(20);
        for (int f = 0; f < 4; f++) {
            h.spawn(new FactionSlot(f), new Vec2d(18 + (f % 2) * 56, 18 + (f / 2) * 56), 1, 0);
        }
        h.runTicks(20);
        assertThat(h.events(MatchEnded.class)).isEmpty();
        return new Scene(h, KernelAccess.state(h.core()));
    }

    @ParameterizedTest
    @EnumSource(
            value = PlayRules.Victory.class,
            names = {"CIVIC_CENTRE", "CONQUEST"})
    void survivingAlliesWinTogetherAndTheTerminalResultSurvivesRestore(PlayRules.Victory rule) {
        Scene s = scene(rule, true);
        s.eliminate(2, 3);
        assertThat(s.state().outcome().winners()).containsExactly(0, 1);
        assertThat(s.h().events(MatchEnded.class)).singleElement().satisfies(end -> {
            assertThat(end.winners()).containsExactly(0, 1);
            assertThat(end.ticks()).isEqualTo(s.state().outcome().endedAt());
        });
        assertThat(java.util.Objects.requireNonNull(s.h().core().queries().matchResults())
                        .winners())
                .containsExactly(0, 1);
        SimHarness back = SimHarness.restore(s.h().core().snapshot(), s.state().templates());
        assertThat(back.stateHash()).isEqualTo(s.h().stateHash());
        assertThat(KernelAccess.state(back.core()).outcome().winners()).containsExactly(0, 1);
        back.runTicks(40);
        s.h().runTicks(40);
        assertThat(back.events(MatchEnded.class)).isEmpty();
        assertThat(s.h().events(MatchEnded.class)).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = PlayRules.Victory.class,
            names = {"CIVIC_CENTRE", "CONQUEST"})
    void bothCoalitionsRemainingDoNotEndAndDefeatedAlliesAreNotWinners(PlayRules.Victory rule) {
        Scene s = scene(rule, true);
        s.eliminate(0);
        assertThat(s.h().events(MatchEnded.class)).isEmpty();
        s.eliminate(2, 3);
        assertThat(s.state().outcome().winners()).containsExactly(1);
        assertThat(s.h().events(MatchEnded.class)).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = PlayRules.Victory.class,
            names = {"CIVIC_CENTRE", "CONQUEST"})
    void simultaneousAnnihilationIsADrawWithoutATransientWinner(PlayRules.Victory rule) {
        Scene s = scene(rule, true);
        s.eliminate(0, 1, 2, 3);
        assertThat(s.state().outcome().ended()).isTrue();
        assertThat(s.state().outcome().winners()).isEmpty();
        assertThat(s.h().events(MatchEnded.class)).singleElement().satisfies(end -> {
            assertThat(end.winners()).isEmpty();
            assertThat(end.winner()).isEqualTo(Outcome.NONE);
        });
        assertThat(s.h().events(FactionDefeated.class)).hasSize(4);
    }

    @ParameterizedTest
    @EnumSource(
            value = PlayRules.Victory.class,
            names = {"CIVIC_CENTRE", "CONQUEST"})
    void unlockedLegacyMatchesKeepIndividualVictory(PlayRules.Victory rule) {
        Scene s = scene(rule, false);
        s.eliminate(2, 3);
        assertThat(s.h().events(MatchEnded.class)).isEmpty();
        s.eliminate(1);
        assertThat(s.state().outcome().winners()).containsExactly(0);
    }
}
