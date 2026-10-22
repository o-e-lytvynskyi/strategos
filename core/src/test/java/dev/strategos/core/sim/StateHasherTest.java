package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class StateHasherTest {

    /** Two units of faction 0, one walking, after 5 ticks. */
    private static SimHarness walking(MatchId id) {
        SimHarness h = SimHarness.builder().id(id).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 2, 2.0);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units[0]), new Vec2d(20, 20)));
        h.runTicks(5);
        return h;
    }

    @Test
    void equalStatesHashEqual() {
        assertThat(walking(new MatchId(1)).stateHash())
                .isEqualTo(walking(new MatchId(1)).stateHash());
    }

    @Test
    void theMatchIdIsLeftOut() {
        assertThat(walking(new MatchId(1)).stateHash())
                .isEqualTo(walking(new MatchId(99)).stateHash());
    }

    @Test
    void aOneBitPositionChangeChangesTheHash() {
        SimHarness h = walking(new MatchId(1));
        long before = h.stateHash();
        MotionStore motion = KernelAccess.state(h.core()).motion();
        motion.set(0, Math.nextUp(motion.x(0)), motion.y(0), motion.z(0), motion.yaw(0));
        assertThat(h.stateHash()).isNotEqualTo(before);
    }

    @Test
    void aYawChangeChangesTheHash() {
        SimHarness h = walking(new MatchId(1));
        long before = h.stateHash();
        MotionStore motion = KernelAccess.state(h.core()).motion();
        motion.set(1, motion.x(1), motion.y(1), motion.z(1), motion.yaw(1) + 1f);
        assertThat(h.stateHash()).isNotEqualTo(before);
    }

    @Test
    void aStateChangeChangesTheHash() {
        SimHarness h = walking(new MatchId(1));
        long before = h.stateHash();
        KernelAccess.state(h.core()).ai().set(1, UnitAi.WALKING);
        assertThat(h.stateHash()).isNotEqualTo(before);
    }

    @Test
    void aDataWordChangesTheHashAndZeroWordsHashAsBefore() {
        SimHarness h = walking(new MatchId(1));
        long before = h.stateHash();
        AiStateStore ai = KernelAccess.state(h.core()).ai();
        ai.setData(0, 0, 1);
        long second = h.stateHash();
        ai.setData(0, 1, 0);
        long first = h.stateHash();
        ai.setData(0, 0, 0);

        assertThat(second).isNotEqualTo(before).isNotEqualTo(first);
        assertThat(first).isNotEqualTo(before);
        assertThat(h.stateHash()).as("words back at 0").isEqualTo(before);
    }

    @Test
    void aPendingCommandChangesTheHash() {
        SimHarness h = walking(new MatchId(1));
        long before = h.stateHash();
        KernelAccess.state(h.core())
                .pendingCommands()
                .addLast(new CommandEnvelope(
                        PlayerSlot.ADMIN, 77, h.tick(), Move.replace(UnitSet.of(Handle.of(1, 1)), new Vec2d(1, 1))));
        assertThat(h.stateHash()).isNotEqualTo(before);
    }

    @Test
    void theTickChangesTheHash() {
        SimHarness h = walking(new MatchId(1));
        SimHarness idle = SimHarness.builder().build();
        long first = idle.stateHash();
        idle.step();
        assertThat(idle.stateHash()).isNotEqualTo(first);
        assertThat(h.stateHash()).isNotEqualTo(first);
    }
}
