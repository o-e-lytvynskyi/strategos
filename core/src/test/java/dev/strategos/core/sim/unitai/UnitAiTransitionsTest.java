package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnitAiTransitionsTest {
    private final SimHarness h = SimHarness.builder().build();
    private final int unit = h.spawn(new FactionSlot(0), new Vec2d(4, 8), 1, 0.0)[0];

    private List<UnitStateChanged> after(Runnable action) {
        int before = h.stateChanges().size();
        action.run();
        List<UnitStateChanged> all = h.stateChanges();
        return all.subList(before, all.size());
    }

    private void send(Command command) {
        h.submit(PlayerSlot.ADMIN, command);
        h.step();
    }

    @Test
    void anIdleUnitStartsWalkingOnAMoveWithOneEvent() {
        assertThat(after(() -> send(Move.replace(UnitSet.of(unit), new Vec2d(20, 8)))))
                .containsExactly(new UnitStateChanged(unit, UnitState.IDLE, UnitState.WALKING));
    }

    @Test
    void aWalkingUnitKeepsWalkingOnAnotherMoveWithNoEvent() {
        send(Move.replace(UnitSet.of(unit), new Vec2d(20, 8)));
        assertThat(after(() -> send(Move.replace(UnitSet.of(unit), new Vec2d(20, 20)))))
                .isEmpty();
        assertThat(h.queries().unit(unit).state()).isEqualTo(UnitState.WALKING);
    }

    @Test
    void arrivalMakesItIdleWithOneEvent() {
        send(Move.replace(UnitSet.of(unit), new Vec2d(5, 8)));
        assertThat(after(() -> h.runTicks(10)))
                .containsExactly(new UnitStateChanged(unit, UnitState.WALKING, UnitState.IDLE));
        assertThat(h.queries().unit(unit).target()).isNull();
    }

    @Test
    void aWalkingUnitStopsWithOneEvent() {
        send(Move.replace(UnitSet.of(unit), new Vec2d(20, 8)));
        assertThat(after(() -> send(new Stop(UnitSet.of(unit)))))
                .containsExactly(new UnitStateChanged(unit, UnitState.WALKING, UnitState.IDLE));
    }

    @Test
    void anIdleUnitStoppedMakesNoEvent() {
        assertThat(after(() -> send(new Stop(UnitSet.of(unit))))).isEmpty();
        assertThat(h.queries().unit(unit).state()).isEqualTo(UnitState.IDLE);
    }
}
