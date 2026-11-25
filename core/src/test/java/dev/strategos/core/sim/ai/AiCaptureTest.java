package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Capture;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-08 in L1 (M9 §2.12): the mop-up captures an enemy barracks it can take half again faster than it regenerates, and
 * burns what it cannot; a squad too small to capture burns it.
 */
class AiCaptureTest {
    private static BuildingView barracks(AiView view) {
        return view.enemyBuildings().stream()
                .filter(b -> b.template().equals(BattleBotTest.BARRACKS))
                .findFirst()
                .orElseThrow();
    }

    private static List<UnitView> army(SimHarness h, int n) {
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 56.5, 56.5, n);
        return BattleBotTest.units(h, 0).stream()
                .filter(u -> u.template().equals(BattleBotTest.SPEAR))
                .toList();
    }

    private static SimHarness scene() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(BattleBotTest.BARRACKS, new FactionSlot(1), 60, 60, 0));
        h.step();
        return h;
    }

    @Test
    void twentyInfantryCaptureAnUndefendedBarracks() {
        SimHarness h = scene();
        List<UnitView> army = army(h, 20);
        AiView view = new AiView(h.core().queries(), 0, new AiBudget(AiBudget.CAP));
        int target = barracks(view).handle();
        List<Command> sent = new ArrayList<>();
        assertThat(MopUp.run(view, new AiBudget(AiBudget.CAP), sent::add, Base.of(view), army))
                .isTrue();
        assertThat(sent)
                .as("a capture order on the barracks, never a burn of it")
                .anyMatch(c -> c instanceof Capture cap && cap.building() == target)
                .noneMatch(c -> c instanceof Attack a && a.target() == target);

        for (Command c : sent) {
            h.submit(PlayerSlot.ADMIN, c);
        }
        for (int k = 0; k < 40 && KernelAccess.state(h.core()).buildings().faction(target) != 0; k++) {
            h.runTicks(100);
        }
        assertThat(KernelAccess.state(h.core()).buildings().faction(target))
                .as("the barracks ends AI-owned")
                .isZero();
    }

    @Test
    void aSquadTooSmallToBeatTheRegenerationBurns() {
        SimHarness h = scene();
        List<UnitView> army = army(h, 4); // 10 a second against a regeneration of 10: no 50 % to spare
        AiView view = new AiView(h.core().queries(), 0, new AiBudget(AiBudget.CAP));
        int target = barracks(view).handle();
        List<Command> sent = new ArrayList<>();
        MopUp.run(view, new AiBudget(AiBudget.CAP), sent::add, Base.of(view), army);
        assertThat(sent)
                .anyMatch(c -> c instanceof Attack a && a.target() == target)
                .noneMatch(c -> c instanceof Capture);
    }

    @Test
    void aHouseIsBurntNotCaptured() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(
                        new dev.strategos.core.shared.TemplateId("strategos:building/house"),
                        new FactionSlot(1),
                        60,
                        60,
                        0));
        h.step();
        List<UnitView> army = army(h, 20);
        AiView view = new AiView(h.core().queries(), 0, new AiBudget(AiBudget.CAP));
        List<Command> sent = new ArrayList<>();
        MopUp.run(view, new AiBudget(AiBudget.CAP), sent::add, Base.of(view), army);
        assertThat(sent).isNotEmpty().noneMatch(c -> c instanceof Capture);
    }
}
