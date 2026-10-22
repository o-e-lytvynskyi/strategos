package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.AiStateStore;
import dev.strategos.core.sim.Stagger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A hierarchical state machine for units (UNIT-02, ADR-0014, 04 §7.5): a tree of {@link StateDef}s, checked and
 * resolved when built, and per match a {@link Machine} that runs it over the units' {@link AiStateStore}.
 *
 * <ul>
 *   <li>A unit is always in a leaf: a transition to a state with children goes on through initial children to a leaf.
 *   <li>A message goes to the unit's leaf; a state without a handler for its class passes it to its parent, and one no
 *       state on the way handles is dropped.
 *   <li>A transition requested by a handler, a think or an enter hook runs once that returns, never inside it: the
 *       states from the leaf up to the lowest one the unit stays in are left, innermost first, then the states down to
 *       the new leaf are entered, outermost first. A transition to a state the unit is in leaves and re-enters it. An
 *       enter hook's request runs once its own transition is complete; more than {@value #MAX_CHAIN} in a row fail, so
 *       a cycle of enter hooks cannot spin.
 *   <li>A state may request only the transitions it declares, one at a time; a leave hook may request none.
 *   <li>The two data words are the leaf's: every transition clears them after the leave hooks, so enter hooks start from
 *       0.
 *   <li>A unit thinks once every think period of its leaf, staggered by its index ({@link Stagger}): the leaf's think,
 *       or its nearest ancestor's, runs as a handler does.
 *   <li>A new unit is placed in the initial leaf and a restored one in its saved leaf, with no hooks: a unit's state is
 *       plain data.
 *   <li>A message dispatched while the machine runs (a hook's or handler's own doing) waits until the running one is
 *       done, transitions included, so hooks and handlers never nest.
 * </ul>
 *
 * @param <H> what the hooks and handlers act on: the match's state, or a test's host
 */
public final class Hfsm<H> {
    /** The most transitions one message, think or chain of enter hooks may cause in a row. */
    public static final int MAX_CHAIN = 8;

    private static final int NONE = -1;

    /** Handles a message of class {@code M} for one unit. */
    @FunctionalInterface
    public interface Handler<H, M extends AiMessage> {
        void handle(Context<H> ctx, M message);
    }

    /** An enter, leave or think hook. */
    @FunctionalInterface
    public interface Hook<H> {
        void run(Context<H> ctx);
    }

    /** Told of every transition once its enter hooks have run, with the leaf the unit left and the one it is in. */
    @FunctionalInterface
    public interface ChangeListener<H> {
        void changed(H host, int handle, StateId from, StateId to);
    }

    private final List<StateDef<H>> defs;
    private final ChangeListener<H> listener;
    private final int[] parent;
    /** Per state, the states from the root down to it. */
    private final int[][] path;

    private final int[] initialLeaf;
    private final boolean[] isLeaf;
    private final int[][] declared;
    private final int[] thinkPeriod;
    /** Per state, the state whose think runs for it, or NONE. */
    private final int[] thinkOwner;

    private final List<Class<? extends AiMessage>> types;
    /** Per (state, type), the state whose handler runs, or NONE; and that handler's place in its state. */
    private final int[] handlerOwner;

    private final int[] handlerSlot;
    /** State index by code, NONE where no state has the code. */
    private final int[] byCode;

    private final boolean thinks;

    private Hfsm(List<StateDef<H>> states, ChangeListener<H> listener) {
        this.defs = List.copyOf(states);
        this.listener = listener;
        int n = defs.size();
        if (n == 0) {
            throw new IllegalArgumentException("a machine needs at least one state");
        }
        int maxCode = 0;
        for (int i = 0; i < n; i++) {
            StateId id = id(i);
            for (int j = 0; j < i; j++) {
                if (id(j).code() == id.code() || id(j).name().equals(id.name())) {
                    throw new IllegalArgumentException("states " + id(j) + " and " + id + " share a code or a name");
                }
            }
            maxCode = Math.max(maxCode, id.code());
        }
        this.byCode = new int[maxCode + 1];
        Arrays.fill(byCode, NONE);
        for (int i = 0; i < n; i++) {
            byCode[id(i).code()] = i;
        }

        this.parent = new int[n];
        int root = NONE;
        for (int i = 0; i < n; i++) {
            StateId p = defs.get(i).parent();
            if (p == null) {
                if (root != NONE) {
                    throw new IllegalArgumentException("two roots: " + id(root) + " and " + id(i));
                }
                root = i;
                parent[i] = NONE;
            } else {
                parent[i] = known(p, "state " + id(i) + " names parent " + p);
            }
        }
        if (root == NONE) {
            throw new IllegalArgumentException("no root: every state names a parent");
        }
        this.path = new int[n][];
        for (int i = 0; i < n; i++) {
            int depth = 0;
            for (int s = i; parent[s] != NONE; s = parent[s]) {
                if (++depth >= n) {
                    throw new IllegalArgumentException("state " + id(i) + " lies on a cycle of parents");
                }
            }
            path[i] = new int[depth + 1];
            for (int s = i, k = depth; s != NONE; s = parent[s], k--) {
                path[i][k] = s;
            }
        }

        this.isLeaf = new boolean[n];
        Arrays.fill(isLeaf, true);
        for (int i = 0; i < n; i++) {
            if (parent[i] != NONE) {
                isLeaf[parent[i]] = false;
            }
        }
        int[] initialChild = new int[n];
        for (int i = 0; i < n; i++) {
            StateId initial = defs.get(i).initial();
            if (isLeaf[i]) {
                if (initial != null) {
                    throw new IllegalArgumentException("leaf " + id(i) + " names an initial child, " + initial);
                }
                if (id(i).activity() == null) {
                    throw new IllegalArgumentException("leaf " + id(i) + " reports no activity");
                }
                initialChild[i] = NONE;
            } else {
                if (initial == null) {
                    throw new IllegalArgumentException("state " + id(i) + " has children but no initial child");
                }
                initialChild[i] = known(initial, "state " + id(i) + " names initial child " + initial);
                if (parent[initialChild[i]] != i) {
                    throw new IllegalArgumentException(
                            "the initial child " + initial + " of " + id(i) + " is not its child");
                }
            }
        }
        this.initialLeaf = new int[n];
        for (int i = 0; i < n; i++) {
            int s = i;
            while (initialChild[s] != NONE) {
                s = initialChild[s];
            }
            initialLeaf[i] = s;
        }

        this.declared = new int[n][];
        this.thinkPeriod = new int[n];
        this.thinkOwner = new int[n];
        boolean anyThink = false;
        for (int i = 0; i < n; i++) {
            StateDef<H> def = defs.get(i);
            List<StateId> targets = def.transitions();
            declared[i] = new int[targets.size()];
            for (int k = 0; k < targets.size(); k++) {
                declared[i][k] =
                        known(targets.get(k), "state " + id(i) + " declares a transition to " + targets.get(k));
            }
            int[] down = path[i];
            int period = 1;
            int owner = NONE;
            for (int s : down) {
                if (defs.get(s).thinkPeriod() > 0) {
                    period = defs.get(s).thinkPeriod();
                }
                if (defs.get(s).think() != null) {
                    owner = s;
                }
            }
            thinkPeriod[i] = period;
            thinkOwner[i] = owner;
            if (owner != NONE) {
                anyThink = true;
            }
        }
        this.thinks = anyThink;

        List<Class<? extends AiMessage>> handled = new ArrayList<>();
        for (StateDef<H> def : defs) {
            for (Class<? extends AiMessage> type : def.types()) {
                if (!handled.contains(type)) {
                    handled.add(type);
                }
            }
        }
        this.types = List.copyOf(handled);
        int t = types.size();
        this.handlerOwner = new int[n * t];
        this.handlerSlot = new int[n * t];
        Arrays.fill(handlerOwner, NONE);
        for (int i = 0; i < n; i++) {
            for (int s : path[i]) { // root first, so the nearest state's handler is written last
                List<Class<? extends AiMessage>> own = defs.get(s).types();
                for (int k = 0; k < own.size(); k++) {
                    int type = types.indexOf(own.get(k));
                    handlerOwner[i * t + type] = s;
                    handlerSlot[i * t + type] = k;
                }
            }
        }
    }

    /** Checks and resolves the tree; a state's place in the tree, its targets and its hooks are fixed from here on. */
    public static <H> Hfsm<H> of(List<StateDef<H>> states, ChangeListener<H> onChange) {
        return new Hfsm<>(states, onChange);
    }

    /** The leaf every unit starts in: the root's initial leaf. */
    public StateId initial() {
        return id(initialLeaf[path[0][0]]);
    }

    /** Every state, in the order the tree was given. */
    public List<StateId> states() {
        List<StateId> all = new ArrayList<>(defs.size());
        for (int i = 0; i < defs.size(); i++) {
            all.add(id(i));
        }
        return all;
    }

    /** The leaves, in the order the tree was given. */
    public List<StateId> leaves() {
        List<StateId> leaves = new ArrayList<>();
        for (int i = 0; i < defs.size(); i++) {
            if (isLeaf[i]) {
                leaves.add(id(i));
            }
        }
        return leaves;
    }

    public boolean isLeaf(StateId state) {
        return isLeaf[known(state, "a leaf test of " + state)];
    }

    /** The states from the root down to {@code state}: those whose handlers and transitions apply in it. */
    public List<StateId> ancestry(StateId state) {
        List<StateId> list = new ArrayList<>();
        for (int s : path[known(state, "the ancestry of " + state)]) {
            list.add(id(s));
        }
        return list;
    }

    /** The transitions {@code state} declares itself, in the order given. */
    public List<StateId> transitions(StateId state) {
        List<StateId> list = new ArrayList<>();
        for (int s : declared[known(state, "the transitions of " + state)]) {
            list.add(id(s));
        }
        return list;
    }

    /** The leaf a transition to {@code state} ends in. */
    public StateId initialLeaf(StateId state) {
        return id(initialLeaf[known(state, "the initial leaf of " + state)]);
    }

    /** How often a unit in {@code state} thinks, in ticks. */
    public int thinkPeriod(StateId state) {
        return thinkPeriod[known(state, "the think period of " + state)];
    }

    /** Whether any state handles messages of exactly this class. */
    public boolean handles(Class<?> type) {
        return types.contains(type);
    }

    /** The message classes some state handles, in the order the tree first names them. */
    public List<Class<? extends AiMessage>> messageTypes() {
        return types;
    }

    /** Runs this tree for the units of one match, kept in {@code store}. */
    public Machine<H> machine(H host, AiStateStore store) {
        return new Machine<>(this, host, store);
    }

    private StateId id(int state) {
        return defs.get(state).id();
    }

    /** The index of a state of this tree, or an exception that names {@code what}. */
    private int known(StateId state, String what) {
        int code = state.code();
        int index = code < byCode.length ? byCode[code] : NONE;
        if (index == NONE || !id(index).equals(state)) {
            throw new IllegalArgumentException(what + ", which the machine lacks");
        }
        return index;
    }

    /** The state of a unit whose store holds {@code code}. */
    private int stateOf(int code) {
        int index = code < byCode.length ? byCode[code] : NONE;
        if (index == NONE || !isLeaf[index]) {
            throw new IllegalStateException("a unit is in state code " + code + ", which is no leaf of the machine");
        }
        return index;
    }

    /** The state both {@code from} and {@code target} lie in that a transition between them stays in, or NONE. */
    private int stay(int from, int target) {
        int[] a = path[from];
        int[] b = path[target];
        int k = 0;
        while (k < a.length && k < b.length && a[k] == b[k]) {
            k++;
        }
        if (k < b.length) {
            return b[k - 1]; // the lowest common ancestor, above the leaf the unit is in
        }
        return k >= 2 ? b[k - 2] : NONE; // the target is a state the unit is in: it is left and re-entered
    }

    private boolean declares(int state, int target) {
        for (int s : declared[state]) {
            if (s == target) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked") // registered for exactly the class of the messages it is given
    private Handler<H, AiMessage> handler(int owner, int slot) {
        return (Handler<H, AiMessage>) defs.get(owner).handler(slot);
    }

    /**
     * A tree running for one match's units. Not reentrant across threads: a match steps on one thread, and every call
     * finishes its transitions, and the messages dispatched meanwhile, before it returns.
     */
    public static final class Machine<H> {
        private final Hfsm<H> tree;
        private final H host;
        private final AiStateStore store;
        private final Context<H> ctx;
        private final ArrayDeque<Queued> queue = new ArrayDeque<>();
        private boolean running;
        private int pending = NONE;

        private record Queued(int handle, AiMessage message) {}

        private Machine(Hfsm<H> tree, H host, AiStateStore store) {
            this.tree = tree;
            this.host = host;
            this.store = store;
            this.ctx = new Context<>(this);
        }

        /** Whether any state thinks: a match whose states never think can skip the think pass. */
        public boolean thinks() {
            return tree.thinks;
        }

        /** Delivers {@code message} to the unit {@code handle}, then runs the transition its handler requested. */
        public void dispatch(int handle, AiMessage message) {
            if (running) {
                queue.addLast(new Queued(handle, message));
                return;
            }
            running = true;
            try {
                deliver(handle, message);
                drain();
            } finally {
                stop();
            }
        }

        /** Runs the think of unit {@code handle} if its state thinks and {@code tick} is its turn. */
        public void think(long tick, int handle) {
            if (running) {
                throw new IllegalStateException("unit " + Handle.toString(handle) + " asked to think inside a handler");
            }
            int unit = Handle.index(handle);
            int state = tree.stateOf(store.code(unit));
            int owner = tree.thinkOwner[state];
            if (owner == NONE || !Stagger.selects(tick, unit, tree.thinkPeriod[state])) {
                return;
            }
            Hook<H> hook = tree.defs.get(owner).think();
            if (hook == null) {
                throw new IllegalStateException("state " + tree.id(owner) + " lost its think");
            }
            running = true;
            try {
                ctx.begin(unit, handle, owner, Context.HANDLING);
                hook.run(ctx);
                settle(unit, handle);
                drain();
            } finally {
                stop();
            }
        }

        private void deliver(int handle, AiMessage message) {
            int type = tree.types.indexOf(message.getClass());
            if (type < 0) {
                return;
            }
            int unit = Handle.index(handle);
            int state = tree.stateOf(store.code(unit));
            int k = state * tree.types.size() + type;
            int owner = tree.handlerOwner[k];
            if (owner == NONE) {
                return; // nothing on the way to the root handles it
            }
            ctx.begin(unit, handle, owner, Context.HANDLING);
            tree.handler(owner, tree.handlerSlot[k]).handle(ctx, message);
            settle(unit, handle);
        }

        private void drain() {
            Queued next;
            while ((next = queue.pollFirst()) != null) {
                deliver(next.handle(), next.message());
            }
        }

        private void stop() {
            running = false;
            pending = NONE;
            queue.clear();
            ctx.end();
        }

        /** Runs the requested transition, and any its enter hooks request in turn. */
        private void settle(int unit, int handle) {
            for (int chain = 0; pending != NONE; chain++) {
                if (chain == MAX_CHAIN) {
                    throw new IllegalStateException("unit " + Handle.toString(handle) + " made " + MAX_CHAIN
                            + " transitions in a row and asked for one to " + tree.id(pending)
                            + ": its enter hooks request transitions in a cycle");
                }
                int target = pending;
                pending = NONE;
                transition(unit, handle, tree.stateOf(store.code(unit)), target);
            }
        }

        private void transition(int unit, int handle, int from, int target) {
            int stay = tree.stay(from, target);
            ctx.mode = Context.LEAVING;
            for (int s = from; s != stay; s = tree.parent[s]) {
                Hook<H> leave = tree.defs.get(s).leave();
                if (leave != null) {
                    ctx.acting = s;
                    leave.run(ctx);
                }
            }
            int leaf = tree.initialLeaf[target];
            store.setData(unit, 0, 0);
            store.set(unit, tree.id(leaf));
            ctx.mode = Context.ENTERING;
            int[] down = tree.path[leaf];
            for (int k = stay == NONE ? 0 : tree.path[stay].length; k < down.length; k++) {
                Hook<H> enter = tree.defs.get(down[k]).enter();
                if (enter != null) {
                    ctx.acting = down[k];
                    enter.run(ctx);
                }
            }
            ctx.mode = Context.HANDLING;
            tree.listener.changed(host, handle, tree.id(from), tree.id(leaf));
        }
    }

    /**
     * What a handler or hook sees of its unit: the host, the unit, the state acting (whose handler or hook runs), the
     * unit's data words, and the one way to change state, {@link #go}. Valid only while the call runs.
     */
    public static final class Context<H> {
        private static final int OUTSIDE = 0;
        private static final int HANDLING = 1;
        private static final int ENTERING = 2;
        private static final int LEAVING = 3;

        private final Machine<H> machine;
        private int mode = OUTSIDE;
        private int unit;
        private int handle;
        private int acting;

        private Context(Machine<H> machine) {
            this.machine = machine;
        }

        private void begin(int unit, int handle, int acting, int mode) {
            this.unit = unit;
            this.handle = handle;
            this.acting = acting;
            this.mode = mode;
        }

        private void end() {
            mode = OUTSIDE;
        }

        public H host() {
            return machine.host;
        }

        public int handle() {
            return handle;
        }

        /** The unit's index: its handle's slot, by which every store keeps it. */
        public int unit() {
            return unit;
        }

        /** The state whose handler or hook runs: the unit's leaf, or the ancestor that handles for it. */
        public StateId state() {
            return machine.tree.id(acting);
        }

        /** The leaf the unit is in. */
        public StateId leaf() {
            return machine.tree.id(machine.tree.stateOf(machine.store.code(unit)));
        }

        /** Whether the unit is in {@code state}: its leaf is that state or lies under it. */
        public boolean in(StateId state) {
            int s = machine.tree.known(state, "a test for " + state);
            for (int k : machine.tree.path[machine.tree.stateOf(machine.store.code(unit))]) {
                if (k == s) {
                    return true;
                }
            }
            return false;
        }

        public int data0() {
            return machine.store.data0(unit);
        }

        public int data1() {
            return machine.store.data1(unit);
        }

        public void setData(int data0, int data1) {
            machine.store.setData(unit, data0, data1);
        }

        /**
         * Requests a transition to {@code target}, which the acting state must declare; it runs once the handler or
         * hook returns. A second request before it runs, or one from a leave hook, is a bug and throws.
         */
        public void go(StateId target) {
            Hfsm<H> tree = machine.tree;
            if (mode == OUTSIDE) {
                throw new IllegalStateException("a transition to " + target + " outside any handler or hook");
            }
            if (mode == LEAVING) {
                throw new IllegalStateException("state " + state() + " is being left and cannot request " + target);
            }
            int t = tree.known(target, "state " + state() + " requested a transition to " + target);
            if (!tree.declares(acting, t)) {
                throw new IllegalStateException(
                        "state " + state() + " requested a transition to " + target + ", which it does not declare");
            }
            if (machine.pending != NONE) {
                throw new IllegalStateException("unit " + Handle.toString(handle) + " requested transitions to "
                        + tree.id(machine.pending) + " and " + target + " at once");
            }
            machine.pending = t;
        }
    }
}
