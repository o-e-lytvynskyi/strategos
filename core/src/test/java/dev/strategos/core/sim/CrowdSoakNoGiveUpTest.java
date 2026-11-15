package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.SoakOrders;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * QA-12 in L1: the crowd soak's shape (two factions of 300 on the proving ground, {@link SoakOrders}) gives up no order.
 * Seeds 3 and 2 once had a group walker loop at the corner of a one-block-wide ramp at (94, 185): it passed the turn
 * half a block off its centre, its straight leg on crossed a cliff's corner, it slid along the cliff and walked back
 * to the turn, and the net-progress count gave its order up at tick 3,543 and 6,803.
 */
class CrowdSoakNoGiveUpTest {
    @Tag("slow")
    @ParameterizedTest
    @CsvSource({"3, 3700", "2, 6900"})
    void aSoakGivesUpNoOrder(long seed, int ticks) {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(seed)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        for (int f = 0; f < 2; f++) {
            StartSlot slot = ground.startSlots().get(f);
            h.spawn(new FactionSlot(f), new Vec2d(slot.x() + 0.5, slot.z() + 0.5), 300, 14);
        }
        SoakOrders orders = new SoakOrders(seed * 77, ground);
        for (int t = 0; t < ticks; t++) {
            Move move = orders.next(h.tick() + 1, h.queries());
            if (move != null) {
                h.submit(PlayerSlot.ADMIN, move);
            }
            h.step();
        }
        assertThat(h.queries().ladderRungs()[Stuck.GIVE_UP])
                .as("orders given up by seed %d", seed)
                .isZero();
    }
}
