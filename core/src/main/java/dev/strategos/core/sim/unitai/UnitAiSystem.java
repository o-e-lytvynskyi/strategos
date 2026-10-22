package dev.strategos.core.sim.unitai;

import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.BusMessage;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PathDelivered;
import dev.strategos.core.sim.PathInvalidated;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.Stuck;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * P3: every unit's state machine (UNIT-02), on the tree of {@link UnitAi}. A bus message that names a unit reaches the
 * unit's machine as it is published, in the phase that publishes it, so a unit answers within the tick, as in M0: an
 * order in {@code COMMANDS}, an arrival in {@code MOVEMENT}. In {@code UNIT_AI} itself every unit whose state thinks
 * and whose turn it is thinks, in index order; no M1 state thinks, so the pass ends at once. Only the messages some
 * state handles are subscribed to, and a tree that handles a message no route here delivers is refused.
 */
public final class UnitAiSystem implements MatchSystem {
    private final Hfsm.Machine<MatchState> machine;

    /** A bus message that names one unit, and how to read the unit's handle from it. */
    private record Route<M extends BusMessage & AiMessage>(Class<M> type, ToIntFunction<M> handle) {}

    public UnitAiSystem(MatchState state) {
        this(state, UnitAi.tree());
    }

    /** Runs {@code tree} instead of the M1 tree: for tests of the routing. */
    public UnitAiSystem(MatchState state, Hfsm<MatchState> tree) {
        List<Route<?>> routes = List.of(
                new Route<>(OrderReceived.class, OrderReceived::handle),
                new Route<>(StopOrdered.class, StopOrdered::handle),
                new Route<>(OrderFinished.class, OrderFinished::handle),
                new Route<>(PathDelivered.class, PathDelivered::handle),
                new Route<>(PathInvalidated.class, PathInvalidated::handle),
                new Route<>(Arrived.class, Arrived::handle),
                new Route<>(Stuck.class, Stuck::handle));
        for (Class<? extends AiMessage> type : tree.messageTypes()) {
            if (routes.stream().noneMatch(route -> route.type() == type)) {
                throw new IllegalArgumentException(
                        "the unit tree handles " + type.getSimpleName() + ", which no route delivers to a unit");
            }
        }
        this.machine = tree.machine(state, state.ai());
        for (Route<?> route : routes) {
            if (tree.handles(route.type())) {
                subscribe(state, route);
            }
        }
    }

    private <M extends BusMessage & AiMessage> void subscribe(MatchState state, Route<M> route) {
        ToIntFunction<M> handle = route.handle();
        state.bus().subscribe(route.type(), m -> machine.dispatch(handle.applyAsInt(m), m));
    }

    @Override
    public Phase phase() {
        return Phase.UNIT_AI;
    }

    @Override
    public void run(MatchState state) {
        if (!machine.thinks()) {
            return;
        }
        EntityStore entities = state.entities();
        long tick = state.tick();
        int highWater = entities.highWater();
        for (int i = 0; i < highWater; i++) {
            if (entities.aliveAt(i)) {
                machine.think(tick, entities.handleAt(i));
            }
        }
    }
}
