package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.BellRung;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-04.2 in L1: a base attacked at its workers commits its soldiers within ten seconds of the first blow, rings the
 * bell once two workers have been hit within five, and sounds the all clear twenty seconds after the last enemy within
 * 32 blocks is gone; a soldier below 30 per cent health walks home instead of joining; a building the enemy
 * destroyed is placed again once the stock covers it.
 */
class DefenceTest {
    /** A bot with no economy and no wave of its own: only its defence acts. */
    static final String QUIET = "{\"maxWorkers\":0,\"ratio\":{},\"build\":[],\"housingMargin\":0,\"soldierQueue\":0,"
            + "\"attack\":{\"first\":100000,\"every\":100000,\"min\":4,\"muster\":24,\"announce\":30},\"sites\":[]}";

    private static boolean fights(SimHarness h, int handle) {
        for (OrderQueues.Order o : h.core().queries().orders(handle)) {
            if (o.kind() == OrderKind.ATTACK || o.kind() == OrderKind.ATTACK_MOVE || o.kind() == OrderKind.ENGAGE) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> handles(SimHarness h, int faction, String id) {
        List<Integer> out = new ArrayList<>();
        for (int handle : h.core().queries().unitHandlesOf(new FactionSlot(faction))) {
            UnitView u = h.core().queries().unit(handle);
            if (u != null && u.template().value().equals(id)) {
                out.add(handle);
            }
        }
        return out;
    }

    @Test
    void defendersWithinTenSecondsAndTheBell() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 40, 10, 6); // soldiers away from the workers' corner
        BattleBotTest.spawn(h, TemplateSet.WORKER, 0, 12, 24, 4);
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 1, 14, 56, 3);
        List<Integer> workers = handles(h, 0, TemplateSet.WORKER.value());
        List<Integer> raiders = handles(h, 1, BattleBotTest.SPEAR.value());
        List<Integer> soldiers = handles(h, 0, BattleBotTest.SPEAR.value());
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "defence", QUIET));
        for (int k = 0; k < 3; k++) { // each raider goes for a worker of its own
            h.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(raiders.get(k)), workers.get(k)));
        }
        long firstBlow = -1;
        long bell = -1;
        long committed = -1;
        long cleared = -1;
        long lastEnemy = -1;
        while (h.tick() < 3_000) {
            TickOutputs out = h.step();
            for (var e : out.events()) {
                if (e instanceof UnitHurt hurt && workers.contains(hurt.handle()) && firstBlow < 0) {
                    firstBlow = h.tick();
                }
                if (e instanceof BellRung && bell < 0) {
                    bell = h.tick();
                }
            }
            if (firstBlow >= 0
                    && committed < 0
                    && soldiers.stream().filter(s -> fights(h, s)).count() >= 4) {
                committed = h.tick();
            }
            if (!handles(h, 1, BattleBotTest.SPEAR.value()).isEmpty()) {
                lastEnemy = h.tick();
            } else if (bell >= 0 && cleared < 0 && BattleBotTest.bot(h).memory().get(Slots.BELL) == 0) {
                cleared = h.tick();
            }
        }
        assertThat(firstBlow).as("the raiders reached the workers").isPositive();
        assertThat(committed - firstBlow)
                .as("ticks from the first blow to the committed soldiers")
                .isBetween(0L, 200L);
        assertThat(bell).as("the bell rang").isPositive();
        assertThat(bell - firstBlow).as("rung within the attack").isLessThanOrEqualTo(200L);
        assertThat(handles(h, 1, BattleBotTest.SPEAR.value()))
                .as("the raiders fell")
                .isEmpty();
        assertThat(cleared).as("the all clear sounded").isPositive();
        assertThat(cleared - lastEnemy)
                .as("twenty seconds after the last enemy")
                .isBetween(380L, 460L);
        assertThat(KernelAccess.state(h.core()).aiSlots().get(0)).isNotNull();
    }

    @Test
    void aWoundedSoldierWalksHomeAndTheRestFight() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 24, 24, 3);
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 1, 40, 40, 3);
        List<Integer> mine = handles(h, 0, BattleBotTest.SPEAR.value());
        int hurt = mine.get(0);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "defence", QUIET));
        KernelAccess.state(h.core())
                .health()
                .setDamage(dev.strategos.core.shared.Handle.index(hurt), 0.75 * maxHealth(h, hurt));
        h.submit(
                PlayerSlot.ADMIN,
                Attack.replace(
                        UnitSet.of(handles(h, 1, BattleBotTest.SPEAR.value()).get(0)), hurt));
        h.runTicks(260);
        UnitView u = h.core().queries().unit(hurt);
        assertThat(u).as("the wounded soldier lives").isNotNull();
        assertThat(Math.hypot(u.x() - 8, u.z() - 8)).as("it went home").isLessThan(14);
    }

    @Test
    void aLostBuildingIsPlacedAgainOnItsSiteOnceAffordable() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        BattleBotTest.spawn(h, TemplateSet.WORKER, 0, 12, 24, 4);
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(BattleBotTest.BARRACKS, new FactionSlot(0), 30, 20, 0));
        h.step();
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, 1_000_000));
        }
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "rebuild", QUIET));
        var state = KernelAccess.state(h.core());
        int lost = state.buildings().handles()[state.buildings().handles().length - 1];
        var at = state.footprintOf(lost);
        h.damage(new ExternalDamage(lost, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
        assertThat(h.step().events())
                .as("the barracks fell")
                .anyMatch(e -> e instanceof BuildingDestroyed d && d.handle() == lost);
        h.runTicks(600);
        assertThat(h.core().queries().buildings())
                .as("the same barracks stands again on its corner")
                .anyMatch(b -> b.template().equals(BattleBotTest.BARRACKS)
                        && b.handle() != lost
                        && b.footprint().anchorX() == at.anchorX()
                        && b.footprint().anchorZ() == at.anchorZ());
    }

    private static double maxHealth(SimHarness h, int handle) {
        return h.core().queries().maxHitPoints(handle);
    }
}
