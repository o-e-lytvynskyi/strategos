package dev.strategos.core.sim.unitai;

import dev.strategos.core.sim.unitai.Hfsm.Handler;
import dev.strategos.core.sim.unitai.Hfsm.Hook;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One state of a {@link Hfsm} (UNIT-02): its place in the tree (its parent, and for a state with children the child it
 * enters first), its enter, leave and think hooks, its typed message handlers, the transitions its handlers and hooks
 * may request, and its think period. A state without a handler for a message, or without a think, uses its nearest
 * ancestor's, and one without a think period its parent's (the root's is every tick); enter and leave hooks are the
 * state's own.
 *
 * @param <H> what the hooks and handlers act on: the match's state, or a test's host
 */
public final class StateDef<H> {
    private final StateId id;
    private final @Nullable StateId parent;
    private final @Nullable StateId initial;
    private final int thinkPeriod;
    private final @Nullable Hook<H> enter;
    private final @Nullable Hook<H> leave;
    private final @Nullable Hook<H> think;
    private final List<Class<? extends AiMessage>> types;
    private final List<Handler<H, ?>> handlers;
    private final List<StateId> transitions;

    private StateDef(Builder<H> b) {
        this.id = b.id;
        this.parent = b.parent;
        this.initial = b.initial;
        this.thinkPeriod = b.thinkPeriod;
        this.enter = b.enter;
        this.leave = b.leave;
        this.think = b.think;
        this.types = List.copyOf(b.types);
        this.handlers = List.copyOf(b.handlers);
        this.transitions = List.copyOf(b.transitions);
    }

    /** The root of a tree: the state every other one lies under. */
    public static <H> Builder<H> root(StateId id) {
        return new Builder<>(id, null);
    }

    public static <H> Builder<H> child(StateId id, StateId parent) {
        return new Builder<>(id, parent);
    }

    public StateId id() {
        return id;
    }

    @Nullable StateId parent() {
        return parent;
    }

    @Nullable StateId initial() {
        return initial;
    }

    /** The state's own think period in ticks, 0 when it has its parent's. */
    int thinkPeriod() {
        return thinkPeriod;
    }

    @Nullable Hook<H> enter() {
        return enter;
    }

    @Nullable Hook<H> leave() {
        return leave;
    }

    @Nullable Hook<H> think() {
        return think;
    }

    /** The message classes the state handles itself, in the order its handlers were given. */
    List<Class<? extends AiMessage>> types() {
        return types;
    }

    /** The handler of {@code types().get(k)}. */
    Handler<H, ?> handler(int k) {
        return handlers.get(k);
    }

    /** The states its handlers, think and enter hook may request a transition to. */
    List<StateId> transitions() {
        return transitions;
    }

    public static final class Builder<H> {
        private final StateId id;
        private final @Nullable StateId parent;
        private @Nullable StateId initial;
        private int thinkPeriod;
        private @Nullable Hook<H> enter;
        private @Nullable Hook<H> leave;
        private @Nullable Hook<H> think;
        private final List<Class<? extends AiMessage>> types = new ArrayList<>();
        private final List<Handler<H, ?>> handlers = new ArrayList<>();
        private final List<StateId> transitions = new ArrayList<>();

        private Builder(StateId id, @Nullable StateId parent) {
            this.id = id;
            this.parent = parent;
        }

        /** The child a transition to this state goes on to; required of a state with children. */
        public Builder<H> initial(StateId child) {
            this.initial = child;
            return this;
        }

        /** How often a unit in this state, or in a child without a period of its own, thinks. */
        public Builder<H> thinkEvery(int ticks) {
            if (ticks < 1) {
                throw new IllegalArgumentException("state " + id + ": a think period of " + ticks + " ticks");
            }
            this.thinkPeriod = ticks;
            return this;
        }

        public Builder<H> think(Hook<H> hook) {
            this.think = hook;
            return this;
        }

        public Builder<H> enter(Hook<H> hook) {
            this.enter = hook;
            return this;
        }

        /** A leave hook; it may not request a transition. */
        public Builder<H> leave(Hook<H> hook) {
            this.leave = hook;
            return this;
        }

        /** Handles messages of exactly class {@code type}; one handler per class and state. */
        public <M extends AiMessage> Builder<H> on(Class<M> type, Handler<H, ? super M> handler) {
            if (types.contains(type)) {
                throw new IllegalArgumentException("state " + id + " handles " + type.getSimpleName() + " twice");
            }
            types.add(type);
            handlers.add(handler);
            return this;
        }

        /** Declares states this one may request a transition to; {@link HfsmCheck} follows them. */
        public Builder<H> transitions(StateId... targets) {
            for (StateId target : targets) {
                if (!transitions.contains(target)) {
                    transitions.add(target);
                }
            }
            return this;
        }

        public StateDef<H> build() {
            return new StateDef<>(this);
        }
    }
}
