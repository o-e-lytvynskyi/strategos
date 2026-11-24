package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-08 in L1 (M9 §2.12): an own tower attacked by enemy infantry, with room, gets the own infantry near it garrisoned
 * by the defence, once in 200 ticks; and an attacked house its nearby workers.
 */
class AiGarrisonTowerTest {
    private static final TemplateId TOWER = new TemplateId("strategos:building/tower");

    private static int building(SimHarness h, TemplateId template, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(0), x, z, 0));
        h.step();
        int[] handles = KernelAccess.state(h.core()).buildings().handles();
        return handles[handles.length - 1];
    }

    private static int garrisoned(SimHarness h, int building) {
        return KernelAccess.state(h.core()).garrison().count(building);
    }

    private static void raid(SimHarness h, int building, TemplateId raider) {
        BattleBotTest.spawn(h, raider, 1, 36.5, 36.5, 3);
        int[] enemies = BattleBotTest.units(h, 1).stream()
                .filter(u -> u.template().equals(raider))
                .mapToInt(UnitView::handle)
                .toArray();
        h.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(enemies), building));
    }

    @Test
    void anAttackedTowerGetsItsNearbyInfantryGarrisoned() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        int tower = building(h, TOWER, 30, 30);
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 26.5, 26.5, 4);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "defence", DefenceTest.QUIET));
        raid(h, tower, BattleBotTest.SPEAR);
        h.runTicks(300);
        assertThat(garrisoned(h, tower)).as("own infantry went into the tower").isPositive();
        MatchState s = KernelAccess.state(h.core());
        assertThat(BattleBotTest.bot(h).memory().get(Slots.GARRISONED))
                .as("the tick of the garrisoning is kept")
                .isPositive();
        assertThat(s.garrison().count(tower)).isLessThanOrEqualTo(5);
    }

    @Test
    void aTowerNobodyAttacksIsLeftAlone() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        int tower = building(h, TOWER, 30, 30);
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 26.5, 26.5, 4);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "defence", DefenceTest.QUIET));
        h.runTicks(300);
        assertThat(garrisoned(h, tower)).isZero();
    }

    @Test
    void anAttackedHouseGetsItsNearbyWorkersGarrisoned() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        int house = building(h, BuildManager.HOUSE, 30, 30);
        BattleBotTest.spawn(h, TemplateSet.WORKER, 0, 26.5, 26.5, 3);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "defence", DefenceTest.QUIET));
        raid(h, house, BattleBotTest.SPEAR);
        h.runTicks(300);
        List<UnitView> workers = BattleBotTest.units(h, 0).stream()
                .filter(u -> u.template().equals(TemplateSet.WORKER))
                .toList();
        assertThat(garrisoned(h, house)).as("workers went into the house").isPositive();
        assertThat(workers).isNotEmpty();
    }
}
