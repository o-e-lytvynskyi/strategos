package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.Attacked;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PathInvalidated;
import dev.strategos.core.sim.StopOrdered;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.sim.combat.Engagement;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The UnitAI tree (UNIT-02, 04 §7.5): M1's walking, M2's melee (CMB-02) and following (CAM-07). Every unit starts
 * IDLE.
 *
 * <pre>
 * INDIVIDUAL                     OrderReceived: the state of the order's kind (an attack's afresh)
 *                                StopOrdered: IDLE
 *   IDLE               every 10  think: an enemy in sight is engaged; Attacked: hit back, or flee
 *   WALKING            every 5   Arrived: the order is done; Stuck at its last rung: the order failed
 *                                PathInvalidated: ask for a path again once allowed
 *   WALKING_AND_FIGHTING every 5 as WALKING, and think: an enemy in sight is engaged; Attacked: hit back
 *   COMBAT                       Stuck at its last rung: the attack failed; PathInvalidated: as WALKING
 *     APPROACHING      every 5   enter: a place round the target; think and Arrived: strike in reach, walk on
 *     ATTACKING        every 1   enter: the wind-up; think: strike when it is over, then every period
 *     CHASING          every 5   think and Arrived: strike in reach, else follow the target
 *   FOLLOW                       Stuck at its last rung: wait; PathInvalidated: as WALKING
 *     FOLLOW_WALKING   every 5   think: the place behind the leader, walked to; Arrived: wait there
 *     FOLLOW_WAITING   every 5   think: once the place moves, walk again
 *   GATHER                       leave: the work slot is given up; Attacked: hit back, or flee; Stuck at its last
 *                                rung: the order failed; PathInvalidated: as WALKING
 *     GATHER_APPROACHING every 5 enter: a slot at the node, walked to; think: a spent node is replaced; Arrived: work
 *     GATHERING        every 1   think: gather at the rate; full: home; the node spent: the next one
 *     GATHER_RETURNING every 5   enter: the nearest dropsite; think: one lost is replaced; Arrived: deliver, go back
 *   BUILD                        leave: the builder slot is given up; Attacked: hit back, or flee; Stuck at its last
 *                                rung: the order failed; PathInvalidated: as WALKING
 *     BUILD_APPROACHING every 5  enter: a slot round the building, walked to; think: one done or lost ends it; Arrived:
 *                                build
 *     BUILDING         every 1   enter: commit a foundation; think: the rate joins the site's work, until it is done
 *   BURN                         Attacked: hit back; Stuck at its last rung: the order failed; PathInvalidated: as
 *                                WALKING
 *     BURN_APPROACHING every 5   enter: a free place round the building, walked to; think: one gone ends it; Arrived:
 *                                burn
 *     BURNING          every 1   enter: the torch's wind-up; think: a blow of fire when it is over, then every period
 * </pre>
 *
 * Whichever of them finds its target dead takes up the nearest enemy in sight, from a new place, or else goes on with
 * its next order: finding a new target is a step, not a state, so no unit ever rests in it. Finding the next node is a
 * step too ({@link Gathering}).
 *
 * IDLE and WALKING keep M0's codes, 0 and 1, so a match hashes as in M0, and M0's names, so M0's saves load. An
 * engagement the unit takes up itself goes before its orders ({@link OrderKind#ENGAGE}), so when it ends the unit goes
 * back to what it was doing: walking and fighting on, or standing idle. Approaching and chasing report WALKING, for the
 * movement systems walk them; striking reports COMBAT. Units without a melee attack never engage; hit while idle, one
 * with a flee distance runs that far from the blow ({@link OrderKind#FLEE}, walked as a move, before its orders). A message
 * no state handles, such as an arrival while idle, is dropped. GATHER, BUILD and BURN (M3) add their subtrees under
 * INDIVIDUAL, each entered through {@link #stateFor} from its order kind; gathering's approach and way home report
 * WALKING, its work GATHERING.
 */
public final class UnitAi {
    public static final StateId IDLE = new StateId(0, "idle", UnitState.IDLE);
    public static final StateId WALKING = new StateId(1, "walking", UnitState.WALKING);
    public static final StateId INDIVIDUAL = new StateId(2, "individual", null);
    public static final StateId COMBAT = new StateId(3, "combat", null);
    public static final StateId APPROACHING = new StateId(4, "combat.approaching", UnitState.WALKING);
    public static final StateId ATTACKING = new StateId(5, "combat.attacking", UnitState.COMBAT);
    public static final StateId CHASING = new StateId(6, "combat.chasing", UnitState.WALKING);
    public static final StateId WALKING_AND_FIGHTING = new StateId(7, "walking_and_fighting", UnitState.WALKING);
    public static final StateId FOLLOW = new StateId(8, "follow", null);
    public static final StateId FOLLOW_WALKING = new StateId(9, "follow.walking", UnitState.WALKING);
    public static final StateId FOLLOW_WAITING = new StateId(10, "follow.waiting", UnitState.IDLE);
    public static final StateId GATHER = new StateId(11, "gather", null);
    public static final StateId GATHER_APPROACHING = new StateId(12, "gather.approaching", UnitState.WALKING);
    public static final StateId GATHERING = new StateId(13, "gather.gathering", UnitState.GATHERING);
    public static final StateId GATHER_RETURNING = new StateId(14, "gather.returning", UnitState.WALKING);
    public static final StateId BUILD = new StateId(15, "build", null);
    public static final StateId BUILD_APPROACHING = new StateId(16, "build.approaching", UnitState.WALKING);
    public static final StateId BUILDING = new StateId(17, "build.building", UnitState.BUILDING);
    public static final StateId BURN = new StateId(18, "burn", null);
    public static final StateId BURN_APPROACHING = new StateId(19, "burn.approaching", UnitState.WALKING);
    public static final StateId BURNING = new StateId(20, "burn.burning", UnitState.COMBAT);

    private UnitAi() {}

    /** A new tree; each match runs its own. */
    public static Hfsm<MatchState> tree() {
        Engagement engagement = new Engagement();
        return Hfsm.of(
                List.of(
                        StateDef.<MatchState>root(INDIVIDUAL)
                                .initial(IDLE)
                                .on(OrderReceived.class, (ctx, m) -> startOrder(ctx))
                                .on(StopOrdered.class, (ctx, m) -> ctx.go(IDLE))
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(IDLE, INDIVIDUAL)
                                .thinkEvery(10)
                                .think(UnitAi::engageNearest)
                                .on(StopOrdered.class, (ctx, m) -> {}) // nothing to stop
                                .on(Attacked.class, (ctx, m) -> hit(ctx, m.attacker()))
                                .build(),
                        StateDef.<MatchState>child(WALKING, INDIVIDUAL)
                                .thinkEvery(5)
                                .on(Arrived.class, (ctx, m) -> finishOrder(ctx, false))
                                .on(Stuck.class, UnitAi::giveUp)
                                .on(PathInvalidated.class, UnitAi::repath)
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(WALKING_AND_FIGHTING, INDIVIDUAL)
                                .thinkEvery(5)
                                .think(UnitAi::engageNearest)
                                .on(Arrived.class, (ctx, m) -> finishOrder(ctx, false))
                                .on(Stuck.class, UnitAi::giveUp)
                                .on(PathInvalidated.class, UnitAi::repath)
                                .on(Attacked.class, (ctx, m) -> CombatActions.retaliate(ctx, m.attacker()))
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(COMBAT, INDIVIDUAL)
                                .initial(APPROACHING)
                                .on(Stuck.class, UnitAi::giveUp)
                                .on(PathInvalidated.class, UnitAi::repath)
                                .on(Arrived.class, (ctx, m) -> {}) // striking: nowhere to walk to
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(APPROACHING, COMBAT)
                                .thinkEvery(5)
                                .enter(ctx -> CombatActions.takePlace(ctx, engagement))
                                .think(ctx -> CombatActions.close(ctx, false, engagement))
                                .on(Arrived.class, (ctx, m) -> CombatActions.arrived(ctx, engagement))
                                .transitions(next(ATTACKING, APPROACHING))
                                .build(),
                        StateDef.<MatchState>child(ATTACKING, COMBAT)
                                .thinkEvery(1)
                                .enter(CombatActions::windUp)
                                .think(CombatActions::strike)
                                .transitions(next(CHASING, APPROACHING))
                                .build(),
                        StateDef.<MatchState>child(CHASING, COMBAT)
                                .thinkEvery(5)
                                .think(ctx -> CombatActions.close(ctx, true, engagement))
                                .on(Arrived.class, (ctx, m) -> CombatActions.close(ctx, true, engagement))
                                .transitions(next(ATTACKING, APPROACHING))
                                .build(),
                        StateDef.<MatchState>child(FOLLOW, INDIVIDUAL)
                                .initial(FOLLOW_WALKING)
                                .on(Stuck.class, (ctx, m) -> {
                                    if (m.rung() == Stuck.GIVE_UP) {
                                        ctx.go(FOLLOW_WAITING); // a follower that cannot get there waits for the next
                                        // place
                                    }
                                })
                                .on(PathInvalidated.class, UnitAi::repath)
                                .transitions(next(FOLLOW_WAITING))
                                .build(),
                        StateDef.<MatchState>child(FOLLOW_WALKING, FOLLOW)
                                .thinkEvery(5)
                                .think(ctx -> Following.keepUp(ctx, true))
                                .on(Arrived.class, (ctx, m) -> ctx.go(FOLLOW_WAITING))
                                .transitions(next(FOLLOW_WAITING))
                                .build(),
                        StateDef.<MatchState>child(FOLLOW_WAITING, FOLLOW)
                                .thinkEvery(5)
                                .think(ctx -> Following.keepUp(ctx, false))
                                .on(Arrived.class, (ctx, m) -> {}) // already there
                                .transitions(next(FOLLOW_WALKING))
                                .build(),
                        StateDef.<MatchState>child(GATHER, INDIVIDUAL)
                                .initial(GATHER_APPROACHING)
                                .leave(Gathering::leave)
                                .on(Attacked.class, (ctx, m) -> hit(ctx, m.attacker()))
                                .on(Stuck.class, UnitAi::giveUp)
                                .on(PathInvalidated.class, UnitAi::repath)
                                .on(Arrived.class, (ctx, m) -> {}) // working: nowhere to walk to
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(GATHER_APPROACHING, GATHER)
                                .thinkEvery(5)
                                .enter(Gathering::approach)
                                .think(Gathering::keepApproaching)
                                .on(Arrived.class, Gathering::arrived)
                                .transitions(next(GATHER_APPROACHING, GATHERING, GATHER_RETURNING))
                                .build(),
                        StateDef.<MatchState>child(GATHERING, GATHER)
                                .thinkEvery(1)
                                .enter(Gathering::begin)
                                .think(Gathering::work)
                                .transitions(next(GATHER_APPROACHING, GATHER_RETURNING))
                                .build(),
                        StateDef.<MatchState>child(GATHER_RETURNING, GATHER)
                                .thinkEvery(5)
                                .enter(Gathering::headHome)
                                .think(Gathering::keepHeadingHome)
                                .on(Arrived.class, Gathering::delivered)
                                .transitions(next(GATHER_APPROACHING, GATHER_RETURNING))
                                .build(),
                        StateDef.<MatchState>child(BUILD, INDIVIDUAL)
                                .initial(BUILD_APPROACHING)
                                .leave(Constructing::leave)
                                .on(Attacked.class, (ctx, m) -> hit(ctx, m.attacker()))
                                .on(Stuck.class, UnitAi::giveUp)
                                .on(PathInvalidated.class, UnitAi::repath)
                                .on(Arrived.class, (ctx, m) -> {}) // building: nowhere to walk to
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(BUILD_APPROACHING, BUILD)
                                .thinkEvery(5)
                                .enter(Constructing::approach)
                                .think(Constructing::keepApproaching)
                                .on(Arrived.class, Constructing::arrived)
                                .transitions(next(BUILDING))
                                .build(),
                        StateDef.<MatchState>child(BUILDING, BUILD)
                                .thinkEvery(1)
                                .enter(Constructing::begin)
                                .think(Constructing::work)
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(BURN, INDIVIDUAL)
                                .initial(BURN_APPROACHING)
                                .on(Attacked.class, (ctx, m) -> hit(ctx, m.attacker()))
                                .on(Stuck.class, UnitAi::giveUp)
                                .on(PathInvalidated.class, UnitAi::repath)
                                .on(Arrived.class, (ctx, m) -> {}) // burning: nowhere to walk to
                                .transitions(next())
                                .build(),
                        StateDef.<MatchState>child(BURN_APPROACHING, BURN)
                                .thinkEvery(5)
                                .enter(Burning::approach)
                                .think(Burning::keepApproaching)
                                .on(Arrived.class, Burning::arrived)
                                .transitions(next(BURNING))
                                .build(),
                        StateDef.<MatchState>child(BURNING, BURN)
                                .thinkEvery(1)
                                .enter(Burning::begin)
                                .think(Burning::work)
                                .transitions(next())
                                .build()),
                UnitAi::changed);
    }

    /**
     * Where finishing an order may take a unit (every state an order's kind is carried out in, and IDLE), and then
     * {@code more}.
     */
    private static StateId[] next(StateId... more) {
        List<StateId> all =
                new ArrayList<>(List.of(IDLE, WALKING, COMBAT, WALKING_AND_FIGHTING, FOLLOW, GATHER, BUILD, BURN));
        all.addAll(List.of(more));
        return all.toArray(StateId[]::new);
    }

    /** IDLE's and WALKING_AND_FIGHTING's think: the nearest enemy in sight is engaged. */
    private static void engageNearest(Context<MatchState> ctx) {
        int enemy = CombatActions.nearestEnemy(ctx);
        if (enemy >= 0) {
            CombatActions.engage(ctx, enemy);
        }
    }

    /** Hit by {@code attacker} (a handle): a unit that can fight hits back, one that cannot runs, if it ever does. */
    static void hit(Context<MatchState> ctx, int attacker) {
        if (ctx.host().stats().canAttack(ctx.unit()) || Stances.of(ctx.host(), ctx.unit()) == Stance.PASSIVE) {
            CombatActions.retaliate(ctx, attacker);
        } else {
            Fleeing.away(ctx, attacker);
        }
    }

    private static void giveUp(Context<MatchState> ctx, Stuck m) {
        if (m.rung() == Stuck.GIVE_UP) {
            finishOrder(ctx, true);
        }
    }

    private static void repath(Context<MatchState> ctx, PathInvalidated m) {
        ctx.host().paths().setRepath(ctx.unit(), true);
    }

    /** The tree's leaves, the states a unit can be in and a save can name. */
    public static List<StateId> leaves() {
        return List.of(
                IDLE,
                WALKING,
                WALKING_AND_FIGHTING,
                APPROACHING,
                ATTACKING,
                CHASING,
                FOLLOW_WALKING,
                FOLLOW_WAITING,
                GATHER_APPROACHING,
                GATHERING,
                GATHER_RETURNING,
                BUILD_APPROACHING,
                BUILDING,
                BURN_APPROACHING,
                BURNING);
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

    /** The state that carries out an order of {@code kind} ({@link OrderKind}). */
    static StateId stateFor(int kind) {
        return switch (kind) {
            case OrderKind.MOVE, OrderKind.FLEE -> WALKING;
            case OrderKind.ATTACK, OrderKind.ENGAGE -> COMBAT;
            case OrderKind.ATTACK_MOVE -> WALKING_AND_FIGHTING;
            case OrderKind.FOLLOW -> FOLLOW;
            case OrderKind.GATHER -> GATHER;
            case OrderKind.BUILD -> BUILD;
            case OrderKind.BURN -> BURN;
            default -> throw new IllegalStateException("no unit state carries out order kind " + kind);
        };
    }

    /**
     * A new current order: the unit enters the state of its kind, or carries on in it; an attack or a follow always
     * starts afresh, from a place round its own target or behind its own leader. An order that ended before the news
     * came, a queued build its state found cancelled on entry say, leaves nothing to start.
     */
    private static void startOrder(Context<MatchState> ctx) {
        int kind = ctx.host().orders().kind(ctx.unit());
        if (kind == OrderKind.NONE) {
            return;
        }
        StateId target = stateFor(kind);
        if (!ctx.in(target) || OrderKind.targets(kind)) {
            ctx.go(target);
        }
    }

    /**
     * The current order is over (ADR-0014's {@code finishOrder}, 04 §7.4): the unit drops it and posts {@link
     * OrderFinished}; with an order queued after it, the unit carries that one out at once, in the state of its kind,
     * as if just ordered ({@link OrderReceived} asks for its path), else it goes IDLE. A failed order does not stop the
     * queue.
     */
    static void finishOrder(Context<MatchState> ctx, boolean failed) {
        MatchState state = ctx.host();
        int i = ctx.unit();
        int finished = state.orders().kind(i);
        state.orders().pop(i);
        state.bus().publish(new OrderFinished(ctx.handle(), failed));
        if (state.orders().size(i) == 0 && finished == OrderKind.ENGAGE) {
            Stances.returnHome(state, i); // a defensive unit walks back to its spot
        }
        if (state.orders().size(i) == 0) {
            ctx.go(IDLE);
            return;
        }
        int kind = state.orders().kind(i);
        StateId next = stateFor(kind);
        if (!ctx.in(next) || OrderKind.targets(kind)) {
            ctx.go(next);
        }
        state.bus().publish(new OrderReceived(ctx.handle()));
    }

    /** A transition between top-level states is the trace event ORD-01.3 asks for; one inside a top-level state is not. */
    private static void changed(MatchState state, int handle, StateId from, StateId to) {
        if (to.equals(IDLE)) {
            Stances.idled(state, Handle.index(handle));
        }
        UnitState was = from.activity();
        UnitState now = to.activity();
        if (was != null && now != null && was != now) {
            state.outputs().add(new UnitStateChanged(handle, was, now));
        }
    }
}
