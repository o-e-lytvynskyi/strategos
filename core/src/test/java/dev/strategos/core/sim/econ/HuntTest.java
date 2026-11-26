package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CarcassGone;
import dev.strategos.core.sim.CarcassLeft;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * ECO-10 in L1: a horseman kills a wild boar, which leaves a carcass of the data's 150 meat where it fell, and gathers
 * it at the data's 5 food a second, 20 to a trip, to the civic centre: the total yield is the meat and the time from
 * the kill to the last delivery is the gathering time plus the trips (1); a worker cannot hunt, only clear what is
 * left, at its slow rate (2); a wild animal has no meat to slaughter while it lives (3).
 */
class HuntTest {
    private static final TemplateId BOAR = new TemplateId("strategos:unit/wild_boar");
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final long MEAT = Amount.of(150);

    private static EconScene scene() {
        return EconScene.flat(64, 64).build();
    }

    /**
     * A civic centre of faction 0 a few blocks from carcass {@code node}, which lies where the boar fell after its
     * flight; the distance from the node to the centre's middle.
     */
    private static double dropsiteBeside(EconScene e, int node) {
        int x = Math.clamp((long) e.s.nodes().anchorX(node) - 14, 1, 50);
        int z = Math.clamp((long) e.s.nodes().anchorZ(node) - 5, 1, 50);
        e.civicCentre(0, x, z);
        return Math.hypot(e.s.nodes().anchorX(node) - (x + 5), e.s.nodes().anchorZ(node) - (z + 5));
    }

    @Test
    void aKillLeavesTheDataMeatAndTheHorsemanDeliversItAtTheDataRate() {
        EconScene e = scene();
        int boar = e.units(BOAR, e.s.gaia(), new Vec2d(34.5, 30.5), 1, 0)[0];
        assertThat(Livestock.nodeOf(e.s, boar)).as("no meat while it lives").isZero();
        int rider = e.units(HORSEMAN, 0, new Vec2d(30.5, 30.5), 1, 0)[0];
        e.h.submit(EconScene.P0, Attack.replace(UnitSet.of(rider), boar));
        int node = 0;
        long killed = -1;
        for (int t = 0; t < 600 && node == 0; t++) {
            TickOutputs out = e.h.step();
            for (var ev : out.events()) {
                if (ev instanceof CarcassLeft left && left.handle() == boar) {
                    node = left.node();
                    killed = e.h.tick();
                }
            }
        }
        assertThat(node).as("the boar was killed").isPositive();
        double near = dropsiteBeside(e, node);
        assertThat(e.s.nodes().kind(node)).isEqualTo(MaterialKind.CARCASS);
        assertThat(e.s.nodes().amount(node)).as("the template's meat").isEqualTo(MEAT);
        assertThat(e.s.nodes().subtype(node)).isEqualTo(subtype("hunt"));
        e.h.runTicks(10);
        assertThat(e.s.orders().kind(e.index(rider)))
                .as("the hunter gathers it")
                .isEqualTo(OrderKind.GATHER);
        assertThat(e.s.orders().target(e.index(rider))).isEqualTo(node);
        long delivered = -1;
        for (int t = 0; t < 3_000 && delivered < 0; t++) {
            e.h.step();
            if (e.gathered(0, EconScene.FOOD) >= MEAT) {
                delivered = e.h.tick();
            }
        }
        assertThat(delivered).as("all of it delivered").isPositive();
        assertThat(e.gathered(0, EconScene.FOOD)).as("the meat, no more").isEqualTo(MEAT);
        assertThat(e.s.nodes().exhausted(node)).isTrue();
        long ticks = delivered - killed;
        assertThat(ticks)
                .as("at 5 a second the meat takes 30 s (600 ticks) of gathering, and its eight trips a little more")
                .isBetween(600L, 600L + 8 * (long) (2 * near / 0.34 + 40));
        e.h.assertInvariants();
    }

    @Test
    void aWorkerClearsAKillAtItsSlowRateButCannotHuntAliveOne() {
        EconScene e = scene();
        int boar = e.units(BOAR, e.s.gaia(), new Vec2d(30.5, 30.5), 1, 0)[0];
        int worker = e.workers(0, new Vec2d(26.5, 30.5), 1, 0)[0];
        e.h.submit(EconScene.P0, Attack.replace(UnitSet.of(worker), boar));
        e.h.step();
        assertThat(e.s.orders().size(e.index(worker)))
                .as("no order to slaughter game")
                .isZero();
        assertThat(Livestock.nodeOf(e.s, boar)).isZero();
        int rider = e.units(HORSEMAN, 0, new Vec2d(30.5, 33.5), 1, 0)[0];
        e.h.submit(EconScene.P0, Attack.replace(UnitSet.of(rider), boar));
        int node = 0;
        for (int t = 0; t < 1_200 && node == 0; t++) {
            for (var ev : e.h.step().events()) {
                if (ev instanceof CarcassLeft left) {
                    node = left.node();
                }
            }
        }
        assertThat(node).isPositive();
        e.h.submit(EconScene.P0, Move.replace(UnitSet.of(rider), new Vec2d(2.5, 62.5))); // it leaves the carcass
        dropsiteBeside(e, node);
        long block = e.s.nodes().blocks(node)[0];
        e.h.submit(
                EconScene.P0,
                Gather.replace(UnitSet.of(worker), NodeStore.x(block), NodeStore.y(block), NodeStore.z(block)));
        long start = e.h.tick();
        boolean gone = false;
        for (int t = 0; t < 6_000 && !gone; t++) {
            for (var ev : e.h.step().events()) {
                gone |= ev instanceof CarcassGone;
            }
        }
        assertThat(gone).isTrue();
        assertThat(e.h.tick() - start)
                .as("one food a second: 150 food take 150 s (3,000 ticks) and the trips")
                .isGreaterThanOrEqualTo(3_000);
    }

    @Test
    void aHuntSavedMidWayRestoresToTheLiveHashAndReplaysToIt() {
        EconScene e = scene();
        int boar = e.units(BOAR, e.s.gaia(), new Vec2d(34.5, 30.5), 1, 0)[0];
        int rider = e.units(HORSEMAN, 0, new Vec2d(30.5, 30.5), 1, 0)[0];
        e.h.submit(EconScene.P0, Attack.replace(UnitSet.of(rider), boar));
        assertThat(e.h.runUntil(() -> !e.h.events(CarcassLeft.class).isEmpty(), 600))
                .isTrue();
        int node = e.h.events(CarcassLeft.class).get(0).node();
        dropsiteBeside(e, node);
        e.h.runTicks(150); // a trip or two in
        assertThat(e.s.treasury().gathered(0, EconScene.FOOD)).isPositive();
        // a restored match has no terrain here (the mod rebuilds it from slices), so only the state is compared
        SimHarness resumed = SimHarness.restore(
                MatchSnapshotCodec.decode(
                                new Dynamic<>(
                                        JsonOps.INSTANCE,
                                        MatchSnapshotCodec.encode(
                                                JsonOps.INSTANCE, e.h.core().snapshot())),
                                Migrations.production())
                        .getOrThrow(),
                TemplateSet.builtin());
        assertThat(resumed.stateHash())
                .as("the carcass and the hunt's state are saved")
                .isEqualTo(e.h.stateHash());
        MatchState back = KernelAccess.state(resumed.core());
        assertThat(back.nodes().amount(node)).isEqualTo(e.s.nodes().amount(node));
        assertThat(back.nodes().subtype(node)).isEqualTo(e.s.nodes().subtype(node));
        assertThat(back.nodes().kind(node)).isEqualTo(MaterialKind.CARCASS);
        e.h.runTicks(300);
        var replay = e.h.replay();
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(e.h.stateHash());
        e.h.assertInvariants();
    }

    private static int subtype(String name) {
        return dev.strategos.core.sim.econ.EconomyRules.DEFAULTS
                .resources()
                .get(0)
                .subtypes()
                .indexOf(name);
    }
}
