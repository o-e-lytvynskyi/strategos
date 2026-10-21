package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
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
    void ownershipIsCheckedAsForMove() {
        CommandResult result = send(new PlayerSlot(1), new Stop(UnitSet.of(unit)));
        assertThat(result.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(result.firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        assertThat(send(PlayerSlot.ADMIN, new Stop(UnitSet.of(unit))).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }
}
