package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * A unit set down after the tick's MOVEMENT phase (a unit put out of a gate, out of a building, a fresh one) leaves
 * the unit grid behind the poses until the next rebuild; a match restored from that tick's snapshot builds its grid
 * from the poses. Both must step alike: the M9 AI soak's seed 13 diverged on the first tick after a journal start
 * taken at such a tick, an archer's avoidance seeing its neighbour in one run and not in the other.
 */
class MidTickMoveRestoreTest {
    static final int SIZE = 64;

    @Test
    void aMatchRestoredAfterAMoveOutsideMovementStepsAsTheLiveOne() {
        SimHarness h = SimHarness.builder().size(SIZE, SIZE).yBand(0, 31, 1).build();
        VoxelWorld world = VoxelFixtures.flat(SIZE, SIZE, 32, 1);
        h.publishAll(world);
        h.step();
        int[] crowd = h.spawn(new FactionSlot(0), new Vec2d(30.5, 30.5), 6, 2);
        int walker = h.spawn(new FactionSlot(0), new Vec2d(20.5, 30.5), 1, 0)[0];
        h.runTicks(5);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(walker), new Vec2d(45.5, 30.5)));
        h.runTicks(20);
        MatchState s = KernelAccess.state(h.core());
        // as a system after MOVEMENT does: a unit set down beside the walker's way, the grid not rebuilt
        int put = Handle.index(crowd[0]);
        int w = Handle.index(walker);
        s.motion().set(put, s.motion().x(w) + 0.6, s.motion().y(put), s.motion().z(w) + 0.05, 0f);
        SimHarness back = SimHarness.restore(h.core().snapshot(), TemplateSet.builtin());
        back.publishAll(world);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        for (int t = 0; t < 60; t++) {
            h.step();
            back.step();
            assertThat(back.stateHash()).as("tick %d after the restore", t + 1).isEqualTo(h.stateHash());
        }
    }

    /**
     * A path asked for after the tick's request pass (a place moved in MOVEMENT, an order taken up in COMBAT) waits for
     * the next tick's pass; a match restored from that tick's snapshot must ask for it as well. The M9 AI soak's seed
     * 15 diverged so: the live archer asked for a path to its new place, the restored one, its path spent, gave the
     * attack up as settled (M9 spec §1.6 row 26).
     */
    @Test
    void aPathAskedForAfterTheRequestPassIsAskedForAfterARestore() {
        SimHarness h = SimHarness.builder().size(SIZE, SIZE).yBand(0, 31, 1).build();
        VoxelWorld world = VoxelFixtures.flat(SIZE, SIZE, 32, 1);
        h.publishAll(world);
        h.step();
        int walker = h.spawn(new FactionSlot(0), new Vec2d(10.5, 30.5), 1, 0)[0];
        h.runTicks(5);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(walker), new Vec2d(50.5, 30.5)));
        h.runTicks(30);
        MatchState s = KernelAccess.state(h.core());
        s.paths().want(Handle.index(walker)); // as MotionSystem does when it hands a unit a new place
        SimHarness back = SimHarness.restore(h.core().snapshot(), TemplateSet.builtin());
        back.publishAll(world);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        for (int t = 0; t < 60; t++) {
            h.step();
            back.step();
            assertThat(back.stateHash()).as("tick %d after the restore", t + 1).isEqualTo(h.stateHash());
        }
    }
}
