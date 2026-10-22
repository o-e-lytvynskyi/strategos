package dev.strategos.core.sim.unitai;

import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.OrderStore;
import dev.strategos.core.sim.PathInvalidated;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The UnitAI tree of M1 (UNIT-02, 04 §7.5, spec §5.9). Every unit starts IDLE.
 *
 * <pre>
 * INDIVIDUAL                   OrderReceived: the state of the order's kind, unless the unit is in it
 *                              StopOrdered: IDLE
 *   IDLE      thinks every 10  StopOrdered: nothing to stop
 *   WALKING   thinks every 5   Arrived: the order is done; Stuck at its last rung: the order failed
 *                              PathInvalidated: ask for a path again once allowed
 * </pre>
 *
 * IDLE and WALKING keep M0's codes, 0 and 1, so a match hashes as in M0, and M0's names, so M0's saves load. Neither has
 * a think yet: everything they do answers a message, and their think periods wait for the stance scan (M2). A message
 * no state handles, such as an arrival while idle, is dropped. Later cards add their subtrees under INDIVIDUAL, with
 * codes from 3 on: COMBAT (M2), then GATHER and BUILD (M3), each entered through {@link #stateFor} from its order kind.
 */
public final class UnitAi {
    public static final StateId IDLE = new StateId(0, "idle", UnitState.IDLE);
    public static final StateId WALKING = new StateId(1, "walking", UnitState.WALKING);
    public static final StateId INDIVIDUAL = new StateId(2, "individual", null);

    private UnitAi() {}

    /** A new tree; each match runs its own. */
    public static Hfsm<MatchState> tree() {
        return Hfsm.of(
                List.of(
                        StateDef.<MatchState>root(INDIVIDUAL)
                                .initial(IDLE)
                                .on(OrderReceived.class, (ctx, m) -> startOrder(ctx))
                                .on(StopOrdered.class, (ctx, m) -> ctx.go(IDLE))
                                .transitions(IDLE, WALKING)
                                .build(),
                        StateDef.<MatchState>child(IDLE, INDIVIDUAL)
                                .thinkEvery(10)
                                .on(StopOrdered.class, (ctx, m) -> {}) // nothing to stop
                                .build(),
                        StateDef.<MatchState>child(WALKING, INDIVIDUAL)
                                .thinkEvery(5)
                                .on(Arrived.class, (ctx, m) -> finishOrder(ctx, false))
                                .on(Stuck.class, (ctx, m) -> {
                                    if (m.rung() == Stuck.GIVE_UP) {
                                        finishOrder(ctx, true);
                                    }
                                })
                                .on(
                                        PathInvalidated.class,
                                        (ctx, m) -> ctx.host().paths().setRepath(ctx.unit(), true))
                                .transitions(IDLE)
                                .build()),
                UnitAi::changed);
    }

    /** The tree's leaves, the states a unit can be in and a save can name. */
    public static List<StateId> leaves() {
        return List.of(IDLE, WALKING);
    }

    /** The leaf saved as {@code name}, or null for any other name. */
    public static @Nullable StateId leaf(String name) {
        for (StateId leaf : leaves()) {
            if (leaf.name().equals(name)) {
                return leaf;
            }
        }
        return null;
    }

    /** The leaf stored as {@code code}. */
    public static StateId leaf(int code) {
        for (StateId leaf : leaves()) {
            if (leaf.code() == code) {
                return leaf;
            }
        }
        throw new IllegalArgumentException("no unit state has code " + code);
    }

    /** The state that carries out an order of {@code kind} ({@link OrderStore}). */
    static StateId stateFor(int kind) {
        if (kind == OrderStore.MOVE) {
            return WALKING;
        }
        throw new IllegalStateException("no unit state carries out order kind " + kind);
    }

    /** A new current order: the unit enters the state of its kind, or carries on in it. */
    private static void startOrder(Context<MatchState> ctx) {
        StateId target = stateFor(ctx.host().orders().kind(ctx.unit()));
        if (!ctx.in(target)) {
            ctx.go(target);
        }
    }

    /**
     * The current order is over (ADR-0014's {@code finishOrder}): with M0's single order slot the unit drops it, posts
     * {@link OrderFinished} and goes IDLE; ORD-02's queue starts the next order instead (T18).
     */
    static void finishOrder(Context<MatchState> ctx, boolean failed) {
        MatchState state = ctx.host();
        state.orders().clear(ctx.unit());
        state.bus().publish(new OrderFinished(ctx.handle(), failed));
        ctx.go(IDLE);
    }

    /** A transition between top-level states is the trace event ORD-01.3 asks for; one inside a top-level state is not. */
    private static void changed(MatchState state, int handle, StateId from, StateId to) {
        UnitState was = from.activity();
        UnitState now = to.activity();
        if (was != null && now != null && was != now) {
            state.outputs().add(new UnitStateChanged(handle, was, now));
        }
    }
}
