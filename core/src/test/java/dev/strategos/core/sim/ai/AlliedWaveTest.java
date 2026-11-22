package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

class AlliedWaveTest {
    private static final FactionSlot FIRST = new FactionSlot(0);
    private static final FactionSlot SECOND = new FactionSlot(1);
    private static final FactionSlot OPPONENT = new FactionSlot(2);

    @Test
    void alliedWavesCloseTheScheduleGapWithoutChangingTheReplay() {
        var allies = scene(true);
        long[] launched = launches(allies, true);
        assertThat(launched[0]).isBetween(700L, 1_920L);
        assertThat(launched[1]).isGreaterThanOrEqualTo(2_300);
        assertThat(Math.abs(launched[0] - launched[1])).isLessThanOrEqualTo(1_200);
        var core = allies.core();
        var replay = ReplayDriver.replay(core.journal(), core.queries().templates());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(core.stateHash());

        long[] opponents = launches(scene(false), false);
        assertThat(opponents[0]).isBetween(700L, 740L);
        assertThat(2_300 - opponents[0]).isGreaterThan(1_200);
    }

    private static long[] launches(SimHarness match, boolean requireBoth) {
        long[] result = {-1, -1};
        var slots = KernelAccess.state(match.core()).aiSlots();
        for (int tick = 0; tick < 2_500; tick++) {
            match.step();
            for (int faction = 0; faction <= 1; faction++) {
                var player = slots.get(faction);
                if (result[faction] < 0 && player != null && player.waves() == 1) {
                    result[faction] = match.core().queries().tick();
                }
            }
            if (result[0] > 0 && (!requireBoth || result[1] > 0)) break;
        }
        assertThat(result[0]).as("first AI launched").isPositive();
        if (requireBoth) assertThat(result[1]).as("second AI launched").isPositive();
        return result;
    }

    private static SimHarness scene(boolean allied) {
        var match = SimHarness.builder()
                .size(128, 128)
                .factions(3)
                .rules(MatchRules.DEFAULTS.withPlay(HeadlessMatch.EXPLORED))
                .yBand(0, 8, 1)
                .build();
        match.publishAll(VoxelFixtures.flat(128, 128, 8, 1));
        var teams = DiplomacyState.identity(3);
        if (allied) teams = teams.withTeam(SECOND, 0);
        match.schedule(1, new DiplomacySetup(teams.lockTeams().snapshot()));
        match.step();
        match.submit(PlayerSlot.ADMIN, new SpawnBuilding(FairAiTest.CENTRE, FIRST, 8, 8, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnBuilding(FairAiTest.CENTRE, SECOND, 55, 8, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnBuilding(FairAiTest.CENTRE, OPPONENT, 89, 91, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, FIRST, new Vec2d(13, 17), 2, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, SECOND, new Vec2d(60, 17), 2, 0));
        var script = FairAiTest.waveScript();
        match.submit(PlayerSlot.ADMIN, new SetAi(FIRST, "early", script.replace("\"first\":1", "\"first\":5")));
        match.submit(PlayerSlot.ADMIN, new SetAi(SECOND, "late", script.replace("\"first\":1", "\"first\":85")));
        return match;
    }
}
