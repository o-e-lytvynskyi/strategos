package dev.strategos.core.sim.formation;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;

/**
 * UNIT_AI, before the units' machines (ORD-06, 04 §9.4): every formation, in id order, first drops the members that
 * are gone, then walks. A member is gone when it died, went inside a building, or its current order is no longer the
 * formation's: it was given an order of its own, or was stopped. The rest take new places (and only then: places are
 * never chosen twice for the same members) and the formation keeps marching. A gather, an attack or an attack-move
 * given to a member breaks the formation up, as does an empty one: the rest walk on to where their places end, each on
 * its own.
 *
 * <p>The formation walks its route at its slowest member's speed, but waits, up to {@value #WAIT_MAX} ticks at a time,
 * while a member is farther than {@value #ASSEMBLED} blocks from its place, so the march does not leave a straggler
 * behind. Its facing turns toward the route's heading at a rate its outermost place can keep up with. At the end of a
 * route that stopped short of the goal it searches again from there; at the end of the last it holds its shape, still
 * turning until it faces the way it marched.
 */
public final class FormationSystem implements MatchSystem {
    /** The farthest a member may be from its place for the formation to walk on, in blocks. */
    static final double ASSEMBLED = 0.6;

    /** The most ticks the formation waits at a time for its members to assemble. */
    static final int WAIT_MAX = 100;

    /** A formation whose route ended this near its goal (or a replan that gained less) holds there, in blocks. */
    static final double NEAR_GOAL = 2.0;

    @Override
    public Phase phase() {
        return Phase.UNIT_AI;
    }

    @Override
    public void run(MatchState s) {
        long start = s.systemTimes().start();
        step(s);
        s.systemTimes().formation(start); // PERF-17
    }

    private static void step(MatchState s) {
        FormationStore store = s.play().formations();
        for (int f = 0; f < store.count(); f++) {
            FormationController c = store.at(f);
            if (!keep(s, c)) {
                store.removeAt(f--);
                continue;
            }
            march(s, c);
        }
    }

    /** Whether unit {@code handle} still keeps its place in {@code c}. */
    private static boolean member(MatchState s, FormationController c, int handle) {
        if (!s.entities().isAlive(handle)) {
            return false;
        }
        int i = Handle.index(handle);
        OrderQueues orders = s.orders();
        return !s.garrisoned(i) && orders.kind(i) == OrderKind.FORMATION && orders.target(i) == c.id();
    }

    /** Drops the members that are gone and places the rest anew; false when none is left, or the formation broke up. */
    private static boolean keep(MatchState s, FormationController c) {
        int n = c.size();
        int count = 0;
        for (int k = 0; k < n; k++) {
            if (member(s, c, c.member(k))) {
                count++;
            }
        }
        if (count == n) {
            return true; // the usual tick: nobody left, nothing allocated
        }
        int[] rest = new int[count];
        int at = 0;
        boolean breaks = false;
        for (int k = 0; k < n; k++) {
            int handle = c.member(k);
            if (member(s, c, handle)) {
                rest[at++] = handle;
            } else if (s.entities().isAlive(handle)) {
                int kind = s.orders().kind(Handle.index(handle));
                breaks |= kind == OrderKind.GATHER || kind == OrderKind.ATTACK || kind == OrderKind.ATTACK_MOVE;
            }
        }
        if (breaks || count == 1) { // a formation of one is no formation: the last member walks on to its place's end
            breakUp(s, c, rest);
            return false;
        }
        if (count == 0) {
            return false;
        }
        FormationLayout.assign(c, s, rest);
        for (int k = 0; k < count; k++) {
            s.orders().retarget(Handle.index(c.member(k)), c.id(), k);
        }
        return true;
    }

    /** The members left walk on, each to where its place ends, as one group order. */
    private static void breakUp(MatchState s, FormationController c, int[] rest) {
        if (rest.length == 0) {
            return;
        }
        int group = rest.length > 1 ? s.newGroup() : 0;
        for (int handle : rest) {
            int i = Handle.index(handle);
            int k = slotOf(c, handle);
            s.orders().replace(i, OrderKind.MOVE, c.endSlotX(k), c.endSlotZ(k), group, k);
            s.bus().publish(new OrderReceived(handle));
        }
    }

    private static int slotOf(FormationController c, int handle) {
        for (int k = 0; k < c.size(); k++) {
            if (c.member(k) == handle) {
                return k;
            }
        }
        throw new IllegalStateException("unit " + Handle.toString(handle) + " has no place in " + c);
    }

    private static void march(MatchState s, FormationController c) {
        double speed = Double.MAX_VALUE;
        double worst = 0;
        for (int k = 0; k < c.size(); k++) {
            int i = Handle.index(c.member(k));
            speed = Math.min(speed, s.stats().walkSpeed(i));
            worst = Math.max(
                    worst,
                    DetMath.length(s.motion().x(i) - c.slotX(k), s.motion().z(i) - c.slotZ(k)));
        }
        c.setSpeed(speed);
        boolean walks = false;
        if (!c.holding()) {
            boolean assembled = worst <= ASSEMBLED;
            if (assembled) {
                c.setWaited(0);
            }
            if (assembled || c.waited() >= WAIT_MAX) {
                walks = true;
                if (c.advance(speed)) {
                    arrive(s, c);
                }
            } else {
                c.setWaited(c.waited() + 1);
            }
        }
        c.setAdvancing(walks && !c.holding());
        if (!c.holding() || !c.aligned()) {
            c.turn(Math.max(0.01, 0.5 * speed / c.reach()));
        }
        c.refresh();
    }

    /** The route is walked to its end: search on from there if it stopped short of the goal, else hold the shape. */
    private static void arrive(MatchState s, FormationController c) {
        boolean farther =
                c.more() && DetMath.length(c.goalX() - c.anchorEndX(), c.goalZ() - c.anchorEndZ()) > NEAR_GOAL;
        if (farther) {
            int guide = nearestMember(s, c);
            int confined = Formations.confined(s, c.membersCopy()); // the most confined member's grid, as at the start
            FormationRoute.Plan plan = FormationRoute.plan(
                    s,
                    guide,
                    confined,
                    c.goalX(),
                    c.goalZ(),
                    s.play().formations().scratch());
            if (plan != null) {
                double[] route = FormationRoute.join(c.anchorEndX(), c.anchorEndZ(), plan.points());
                if (route.length > 2
                        && DetMath.length(
                                        route[route.length - 2] - c.anchorEndX(),
                                        route[route.length - 1] - c.anchorEndZ())
                                > NEAR_GOAL) {
                    c.setRoute(route, plan.more());
                    return;
                }
            }
        }
        c.setHolding(true);
    }

    private static int nearestMember(MatchState s, FormationController c) {
        int best = Handle.index(c.member(0));
        double nearest = Double.MAX_VALUE;
        for (int k = 0; k < c.size(); k++) {
            int i = Handle.index(c.member(k));
            double d =
                    DetMath.length(s.motion().x(i) - c.anchorEndX(), s.motion().z(i) - c.anchorEndZ());
            if (d < nearest) {
                nearest = d;
                best = i;
            }
        }
        return best;
    }
}
