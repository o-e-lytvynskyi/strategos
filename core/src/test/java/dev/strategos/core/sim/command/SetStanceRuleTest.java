package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.order.StanceStore;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/** SetStance (CMB-05): own living units take the stance and a spot where they stand; nothing else changes. */
class SetStanceRuleTest {
    private static final PlayerSlot PLAYER_0 = new PlayerSlot(0);

    private final SimHarness h = SimHarness.builder().build();
    private final int[] own = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 2, 2.0);
    private final int theirs = h.spawn(new FactionSlot(1), new Vec2d(24, 24), 1, 0.0)[0];
    private final MatchState state = KernelAccess.state(h.core());

    private CommandResult send(PlayerSlot issuer, SetStance set) {
        int seq = h.submit(issuer, set);
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void ownUnitsTakeTheStanceAndSpotWhereTheyStand() {
        long pristine = h.stateHash();
        assertThat(state.stances().empty()).isTrue();

        CommandResult result = send(PLAYER_0, new SetStance(UnitSet.of(own), Stance.DEFENSIVE));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(result.accepted()).isEqualTo(2);
        for (int unit : own) {
            int i = Handle.index(unit);
            assertThat(state.stances().explicit(i)).isEqualTo(Stance.DEFENSIVE);
            assertThat(state.stances().spotX(i)).isEqualTo(state.motion().x(i));
            assertThat(state.stances().spotZ(i)).isEqualTo(state.motion().z(i));
        }
        assertThat(h.stateHash()).isNotEqualTo(pristine);
    }

    @Test
    void anotherFactionsUnitAndADeadOneAreRefusedAndNothingIsKept() {
        CommandResult foreign = send(PLAYER_0, new SetStance(UnitSet.of(theirs), Stance.PASSIVE));
        assertThat(foreign.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(foreign.firstReason()).isEqualTo(RejectReason.NOT_OWNER);

        state.removeUnit(own[1]);
        CommandResult dead = send(PLAYER_0, new SetStance(UnitSet.of(own[1]), Stance.PASSIVE));
        assertThat(dead.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(dead.firstReason()).isEqualTo(RejectReason.UNKNOWN_TARGET);

        CommandResult none = send(PLAYER_0, new SetStance(UnitSet.of(), Stance.PASSIVE));
        assertThat(none.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);

        assertThat(state.stances().empty()).isTrue();
    }

    @Test
    void aStanceDoesNotOutliveItsUnit() {
        send(PLAYER_0, new SetStance(UnitSet.of(own[0]), Stance.STAND_GROUND));
        state.removeUnit(own[0]);
        assertThat(state.stances().empty()).as("a dead unit's row is not live").isTrue();
        int[] next = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0.0);
        assertThat(state.stances().explicit(Handle.index(next[0]))).isNull();
    }

    @Test
    void aRowDoesNotReviveWhenAHandleComesBackAround() {
        int[] live = {Handle.of(3, 1)};
        StanceStore store = new StanceStore(i -> live[0]);
        store.set(3, Stance.PASSIVE, 1, 2);
        store.clear(3); // the unit died
        live[0] = Handle.of(3, 1); // its generation wrapped to the same handle
        assertThat(store.explicit(3)).isNull();
        assertThat(store.empty()).isTrue();
    }
}
