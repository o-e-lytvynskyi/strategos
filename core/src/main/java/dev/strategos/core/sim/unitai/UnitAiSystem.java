package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStateChanged;

/**
 * P3, with nothing to do per tick in M0: the transitions come from the bus. An order makes an idle unit walk; a stop or
 * an arrival makes a walking unit idle. Each change, and only a change, adds a {@code UnitStateChanged}.
 */
public final class UnitAiSystem implements MatchSystem {
    private final MatchState state;

    public UnitAiSystem(MatchState state) {
        this.state = state;
        state.bus().subscribe(OrderReceived.class, m -> change(m.handle(), UnitState.IDLE, UnitState.WALKING));
        state.bus().subscribe(StopOrdered.class, m -> change(m.handle(), UnitState.WALKING, UnitState.IDLE));
        state.bus().subscribe(Arrived.class, m -> {
            state.orders().clear(Handle.index(m.handle()));
            change(m.handle(), UnitState.WALKING, UnitState.IDLE);
        });
    }

    @Override
    public Phase phase() {
        return Phase.UNIT_AI;
    }

    @Override
    public void run(MatchState state) {}

    private void change(int handle, UnitState from, UnitState to) {
        int index = Handle.index(handle);
        if (state.ai().state(index) == from) {
            state.ai().set(index, to);
            state.outputs().add(new UnitStateChanged(handle, from, to));
        }
    }
}
