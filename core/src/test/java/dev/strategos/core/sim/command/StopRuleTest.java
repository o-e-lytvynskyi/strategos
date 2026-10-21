package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class StopRuleTest {
    private static final PlayerSlot PLAYER_0 = new PlayerSlot(0);

    private final SimHarness h = SimHarness.builder().build();
    private final int unit = h.spawn(new FactionSlot(0), new Vec2d(4, 8), 1, 0.0)[0];

    private CommandResult send(PlayerSlot issuer, Stop stop) {
        int seq = h.submit(issuer, stop);
        h.step();
        return h.results().stream()
                .filter(r -> r.clientSeq() == seq)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aWalkingUnitStopsAndStaysPut() {
        h.submit(PLAYER_0, Move.replace(UnitSet.of(unit), new Vec2d(24, 8)));
        h.runTicks(10);
        assertThat(h.queries().unit(unit).state()).isEqualTo(UnitState.WALKING);

        CommandResult result = send(PLAYER_0, new Stop(UnitSet.of(unit)));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        UnitView stopped = h.queries().unit(unit);
        assertThat(stopped.state()).isEqualTo(UnitState.IDLE);
        assertThat(stopped.target()).isNull();
        h.runTicks(20);
        assertThat(h.queries().unit(unit).x()).isEqualTo(stopped.x());
        assertThat(h.queries().unit(unit).z()).isEqualTo(stopped.z());
    }

    @Test
    void anotherPlayersStopIsRefusedAndTheUnitWalksOn() {
        h.submit(PLAYER_0, Move.replace(UnitSet.of(unit), new Vec2d(24, 8)));
        h.runTicks(5);
        UnitView before = h.queries().unit(unit);
        int changes = h.stateChanges().size();

        CommandResult refused = send(new PlayerSlot(1), new Stop(UnitSet.of(unit)));

        assertThat(refused.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(refused.firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        assertThat(refused.accepted()).isZero();
        assertThat(refused.rejected()).isEqualTo(1);
        UnitView after = h.queries().unit(unit);
        assertThat(after.state()).isEqualTo(UnitState.WALKING);
        assertThat(after.target()).isEqualTo(new Vec2d(24, 8));
        assertThat(after.x()).as("it walked on in the same tick").isGreaterThan(before.x());
        assertThat(h.stateChanges()).hasSize(changes);
        h.step();
        assertThat(h.queries().unit(unit).x()).isGreaterThan(after.x());

        assertThat(send(PlayerSlot.ADMIN, new Stop(UnitSet.of(unit))).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(h.queries().unit(unit).state()).isEqualTo(UnitState.IDLE);
        assertThat(h.queries().unit(unit).target()).isNull();
    }

    @Test
    void aNeverIssuedHandleAndAStaleOneGetTheSameUnknownTargetAndStopNothing() {
        int stale = unit;
        KernelAccess.state(h.core()).entities().destroy(stale);
        int reused = h.spawn(new FactionSlot(0), new Vec2d(4, 8), 1, 0.0)[0];
        assertThat(Handle.index(reused)).isEqualTo(Handle.index(stale));
        h.submit(PLAYER_0, Move.replace(UnitSet.of(reused), new Vec2d(24, 8)));
        h.step();
        assertThat(h.queries().unit(reused).state()).isEqualTo(UnitState.WALKING);

        CommandResult dead = send(PLAYER_0, new Stop(UnitSet.of(stale)));
        CommandResult missing = send(PLAYER_0, new Stop(UnitSet.of(Handle.of(500, 1))));

        for (CommandResult r : List.of(dead, missing)) {
            assertThat(r.outcome()).isEqualTo(CommandOutcome.REJECTED);
            assertThat(r.firstReason()).isEqualTo(RejectReason.UNKNOWN_TARGET);
        }
        assertThat(List.of(dead.outcome(), dead.accepted(), dead.rejected(), dead.firstReason()))
                .isEqualTo(List.of(missing.outcome(), missing.accepted(), missing.rejected(), missing.firstReason()));
        UnitView walking = h.queries().unit(reused);
        assertThat(walking.state())
                .as("the unit now at the stale index walks on")
                .isEqualTo(UnitState.WALKING);
        assertThat(walking.target()).isEqualTo(new Vec2d(24, 8));
    }

    @Test
    void anEmptySetIsRefusedWhole() {
        CommandResult result = send(PLAYER_0, new Stop(UnitSet.of()));

        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(result.accepted()).isZero();
        assertThat(result.rejected()).isZero();
    }
}
