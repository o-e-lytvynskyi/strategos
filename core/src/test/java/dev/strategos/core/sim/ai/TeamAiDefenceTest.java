package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class TeamAiDefenceTest {
    private static final FactionSlot OWN = new FactionSlot(0);
    private static final Vec2d THREAT = new Vec2d(56, 18);

    @Test
    void anIdleArmyReachesAnObservedAlliedBaseThreatWithinThirtySeconds() {
        var harness = scene(HeadlessMatch.EXPLORED);
        int soldier = harness.core().queries().unitHandlesOf(OWN)[0];
        List<Command> commands = think(harness);
        assertThat(commands).hasSize(1);
        assertThat(commands.getFirst()).isInstanceOfSatisfying(AttackMove.class, order -> {
            assertThat(order.units().contains(soldier)).isTrue();
            assertThat(DetMath.length(
                            order.target().x() - THREAT.x(), order.target().z() - THREAT.z()))
                    .isLessThan(2);
        });
        harness.submit(PlayerSlot.ADMIN, commands.getFirst());
        for (int tick = 1; tick <= 600; tick++) {
            harness.step();
            var unit = Objects.requireNonNull(harness.core().queries().unit(soldier));
            if (DetMath.length(unit.x() - THREAT.x(), unit.z() - THREAT.z()) <= 8) return;
        }
        throw new AssertionError("idle army did not reach the allied threat within 600 ticks");
    }

    @Test
    void activeArmyAndUnobservedThreatDoNotCauseAssistanceOrders() {
        var active = scene(HeadlessMatch.EXPLORED);
        int soldier = active.core().queries().unitHandlesOf(OWN)[0];
        active.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(soldier), new Vec2d(14, 45)));
        active.step();
        assertThat(active.core().queries().orders(soldier)).isNotEmpty();
        assertThat(think(active)).isEmpty();

        var hidden = scene(HeadlessMatch.FOGGED);
        var view = new AiView(hidden.core().queries(), 0, new AiBudget(AiBudget.CAP));
        assertThat(view.enemyUnits()).isEmpty();
        assertThat(think(hidden)).isEmpty();
    }

    private static List<Command> think(SimHarness harness) {
        var budget = new AiBudget(AiBudget.CAP);
        var result = new ArrayList<Command>();
        var script = StrategyScript.parse(FairAiTest.waveScript());
        new MilitaryManager(script, new AiMemory())
                .think(new AiView(harness.core().queries(), 0, budget), budget, result::add);
        return result;
    }

    private static SimHarness scene(PlayRules play) {
        var harness = SimHarness.builder()
                .size(80, 80)
                .factions(3)
                .rules(MatchRules.DEFAULTS.withPlay(play))
                .yBand(0, 8, 1)
                .build();
        harness.publishAll(VoxelFixtures.flat(80, 80, 8, 1));
        var ally = new FactionSlot(1);
        harness.schedule(
                1,
                new DiplomacySetup(
                        DiplomacyState.identity(3).withTeam(ally, 0).lockTeams().snapshot()));
        harness.step();
        harness.submit(PlayerSlot.ADMIN, new SpawnBuilding(FairAiTest.CENTRE, OWN, 8, 8, 0));
        harness.submit(PlayerSlot.ADMIN, new SpawnBuilding(FairAiTest.CENTRE, ally, 50, 8, 0));
        harness.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, OWN, new Vec2d(12, 16), 1, 0));
        harness.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(2), THREAT, 1, 0));
        harness.step();
        harness.step();
        return harness;
    }
}
