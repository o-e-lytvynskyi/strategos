package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The server smoke's map step in L1 (M6 T23, QA-12 "no stall"): eight walkers ordered 16 blocks across the proving
 * ground are all idle within {@link ProvingGround#GROUP_SETTLE_TICKS} ticks (the smoke's wait, the same constant), on
 * every match seed. The smoke's match takes a random seed, and on some the crowd round the target settled late: on seeds
 * 914 and 1413 a walker crept a hundredth of a block a tick through mates already standing (progress to the stuck
 * monitor, so the crowd rules never came), and on 1563 and 2462 a pushed mate walked back to its latch and pushed
 * another: all four still walked past tick 160.
 */
class CrowdSettleTest {
    @ParameterizedTest
    @ValueSource(longs = {1, 90, 378, 482, 681, 914, 1060, 1413, 1563, 2462, 2695})
    void aGroupMoveSettlesWithinTheSmokesWaitOnEverySeed(long seed) {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(seed)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(24.5, 192.5), 8, 2));
        h.step();
        int[] units = h.queries().unitHandles();
        assertThat(units).hasSize(8);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(40.5, 192.5)));
        int walking = 0; // the last tick any walker still walks
        for (int t = 1; t <= ProvingGround.GROUP_SETTLE_TICKS; t++) {
            h.step();
            for (int u : units) {
                if (h.queries().unit(u).state() != UnitState.IDLE) {
                    walking = t;
                }
            }
        }
        assertThat(walking)
                .as("the last tick a walker still walks, seed %d", seed)
                .isLessThan(ProvingGround.GROUP_SETTLE_TICKS);
        for (int u : units) {
            var v = h.queries().unit(u);
            assertThat(Math.hypot(v.x() - 40.5, v.z() - 192.5))
                    .as("walker %x stands round the target", u)
                    .isLessThan(6);
        }
    }
}
