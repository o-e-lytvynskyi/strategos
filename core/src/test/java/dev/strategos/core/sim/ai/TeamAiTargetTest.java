package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class TeamAiTargetTest {
    private static final StartSlot HOME = new StartSlot(8, 1, 8, 0);
    private static final StartSlot ALLY = new StartSlot(24, 1, 8, 0);
    private static final StartSlot OPPONENT = new StartSlot(64, 1, 64, 0);

    @Test
    void observedAlliesNeitherEnterEnemyListsNorBecomeTheNearestBase() {
        var harness = match();
        int[] positions = {10, 24, 37};
        for (int faction = 0; faction < positions.length; faction++) {
            var seat = new FactionSlot(faction);
            int x = positions[faction];
            harness.submit(PlayerSlot.ADMIN, new SpawnBuilding(FairAiTest.CENTRE, seat, x, 10, 0));
            harness.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, seat, new Vec2d(x, 19), 1, 0));
        }
        harness.step();
        harness.step();

        var queries = harness.core().queries();
        assertThat(queries.buildings()).extracting(BuildingView::faction).containsExactlyInAnyOrder(0, 1, 2);
        assertThat(queries.unitHandles()).hasSize(3);
        var view = new AiView(queries, 0, new AiBudget(AiBudget.CAP));
        assertThat(view.enemyUnits()).extracting(UnitView::faction).containsExactly(new FactionSlot(2));
        assertThat(view.enemyBuildings()).extracting(BuildingView::faction).containsExactly(2);
        var base = Objects.requireNonNull(Base.of(view));
        assertThat(Objects.requireNonNull(base.enemy()).faction()).isEqualTo(2);
    }

    @Test
    void fallbackAimsAtAnOpposingIndexedStartEvenWhenAnAlliedStartIsCloser() {
        var view = new AiView(match().core().queries(), 0, new AiBudget(AiBudget.CAP));
        assertThat(view.enemyBuildings()).isEmpty();
        assertThat(Base.enemyStart(view, HOME.x(), HOME.z())).isEqualTo(OPPONENT);
    }

    private static SimHarness match() {
        var harness = SimHarness.builder()
                .size(80, 80)
                .factions(3)
                .rules(MatchRules.DEFAULTS.withPlay(HeadlessMatch.EXPLORED))
                .yBand(0, 8, 1)
                .starts(List.of(HOME, ALLY, OPPONENT))
                .build();
        harness.publishAll(VoxelFixtures.flat(80, 80, 8, 1));
        var teams = DiplomacyState.identity(3).withTeam(new FactionSlot(1), 0).lockTeams();
        harness.schedule(1, new DiplomacySetup(teams.snapshot()));
        harness.step();
        return harness;
    }
}
