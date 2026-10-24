package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-08 in L1: a small iron mine holding 1,000 yields exactly 1,000 to its workers and is then exhausted, its slots
 * free (1); a small deposit has 3 work slots and a large one 6, as the sheet says (2); and its blocks turn to stone a
 * quarter at a time, at 75, 50, 25 and 0 % (the look the L3 test watches in the world).
 */
class DepositTest {
    private static final TemplateId IRON_MINE = new TemplateId("strategos:building/iron_mine");
    private static final TemplateId QUARRY_LARGE = new TemplateId("strategos:building/quarry_large");
    private static final int IRON = 3;

    private static int spawn(EconScene e, TemplateId template, int x, int z) {
        e.h.submit(PlayerSlot.ADMIN, new SpawnDeposit(template, x, z));
        e.h.step();
        for (int id = e.s.nodes().count(); id >= 1; id--) {
            if (e.s.nodes().kind(id) == MaterialKind.DEPOSIT) {
                return id;
            }
        }
        throw new AssertionError("no deposit spawned");
    }

    @Test
    void nRemainingYieldsNThenItIsExhaustedAndFreesItsSlots() {
        EconScene e = EconScene.flat(48, 48).build();
        e.civicCentre(0, 30, 30);
        int mine = spawn(e, IRON_MINE, 10, 10);
        assertThat(e.s.nodes().amount(mine)).isEqualTo(Amount.of(1_000));
        int[] workers = e.workers(0, new Vec2d(8.5, 11.5), 3, 1);
        e.gather(workers, 11, 1, 11); // its top layer: the lowest takes the ground's top block, as a building's
        List<WorldEffect> turned = new ArrayList<>();
        for (int t = 0; t < 400_000 && !e.s.nodes().exhausted(mine); t++) {
            TickOutputs out = e.h.step();
            for (OutputEvent ev : out.events()) {
                if (ev instanceof WorldEffect w && w.site() == mine && !WorldEffect.AIR.equals(w.state())) {
                    turned.add(w);
                }
            }
        }
        assertThat(e.s.nodes().exhausted(mine)).as("mined out").isTrue();
        for (int k = 0; k < 600; k++) {
            e.h.step(); // the last loads home
        }
        long carried = 0;
        for (int w : workers) {
            carried += e.s.carry().resource(e.index(w)) == IRON ? e.s.carry().amount(e.index(w)) : 0;
        }
        assertThat(e.gathered(0, IRON) + carried)
                .as("every thousandth of it, no more")
                .isEqualTo(Amount.of(1_000));
        for (int slot = 0; slot < e.s.nodes().slotCount(mine); slot++) {
            assertThat(e.s.nodes().claim(mine, slot)).as("slot %d", slot).isZero();
        }
        int blocks = e.s.nodes().blocks(mine).length;
        assertThat(turned)
                .as("every block turned, a quarter a stage")
                .hasSize(blocks)
                .allSatisfy(w -> assertThat(w.state()).isEqualTo("minecraft:stone"));
    }

    @Test
    void slotsFollowTheSize() {
        EconScene e = EconScene.flat(48, 48).build();
        int small = spawn(e, IRON_MINE, 4, 4);
        int large = spawn(e, QUARRY_LARGE, 20, 20);
        assertThat(e.s.nodes().slotCount(small)).isEqualTo(3);
        assertThat(e.s.nodes().slotCount(large)).isEqualTo(6);
        assertThat(e.s.nodes().amount(large)).isEqualTo(Amount.of(5_000));
        assertThat(e.s.footprintAt(5, 5)).as("its footprint blocks units").isTrue();
    }

    @Test
    void theStagesAreQuarters() {
        assertThat(Deposits.stage(1_000, 1_000)).isZero();
        assertThat(Deposits.stage(751, 1_000)).isZero();
        assertThat(Deposits.stage(750, 1_000)).isOne();
        assertThat(Deposits.stage(500, 1_000)).isEqualTo(2);
        assertThat(Deposits.stage(250, 1_000)).isEqualTo(3);
        assertThat(Deposits.stage(1, 1_000)).isEqualTo(3);
        assertThat(Deposits.stage(0, 1_000)).isEqualTo(4);
    }

    /** MATCH-04 with ECO-08: a restored match's deposit blocks its columns again, as the live one's does. */
    @Test
    void aRestoredDepositStillBlocksItsColumns() {
        EconScene e = EconScene.flat(48, 48).build();
        spawn(e, IRON_MINE, 20, 20);
        e.h.runTicks(4); // the page with the footprint rebuilt
        assertThat(e.s.footprintAt(21, 21)).as("live").isTrue();
        var back = dev.strategos.core.testkit.SimHarness.restore(
                e.h.core().snapshot(),
                dev.strategos.core.content.TemplateSet.builtin(),
                e.h.core().journal().segments().get(0)::slice);
        var restored = dev.strategos.core.sim.KernelAccess.state(back.core());
        assertThat(restored.footprintAt(21, 21)).as("restored").isTrue();
        assertThat(back.stateHash()).isEqualTo(e.h.stateHash());
        for (int t = 0; t < 40; t++) {
            e.h.step();
            back.step();
        }
        assertThat(back.core().queries().surfaces().count(21, 21))
                .isEqualTo(e.h.core().queries().surfaces().count(21, 21));
        assertThat(back.stateHash()).isEqualTo(e.h.stateHash());
    }
}
