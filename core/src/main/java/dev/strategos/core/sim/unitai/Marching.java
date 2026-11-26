package dev.strategos.core.sim.unitai;

import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.PlaceMoved;
import dev.strategos.core.sim.Stagger;
import dev.strategos.core.sim.formation.FormationController;
import dev.strategos.core.sim.nav.LineOfWalk;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.Hfsm.Context;
import org.jspecify.annotations.Nullable;

/**
 * What the FORMATION_MEMBER states do (ORD-06): keep the place the formation gives the unit, as a follower keeps its
 * place behind a leader ({@link Following}). The place the order holds is where the unit's slot will be a moment from
 * now ({@link FormationController#placeX}), set again when it has moved {@value #PUSH} blocks (at once, once the formation stands or waits), so the unit walks on
 * toward it, by the movement systems and their avoidance as any walker does; one that reaches it early stands and
 * waits for the next, which is how a faster member keeps to the formation's speed. Nothing is decided here that the
 * formation's system has not: whether the unit still has a place, who the formation's members are, where they stand.
 * A marching formation walks only, so its members stay in FORMATION_MARCHING (standing on their places when they are early, which
 * keeps them from flickering between walking and idle) and do not answer blows or look for enemies; one that stands holds its
 * shape and fights as units do, each leaving the formation by the fight it takes up.
 */
final class Marching {
    /** How far a place moves before the order's is set again, in blocks. */
    static final double PUSH = 0.9;

    /** How far a place moves before the order's is set again while the formation stands or waits, in blocks. */
    static final double SETTLE = 0.05;

    /** How far a standing member may be pushed off its place before it walks back, in blocks. */
    static final double REWALK = 0.4;

    /** How far nearer its place a unit must come, in sixteenths of a block, to count as progress while it stands. */
    static final int PROGRESS = 4;

    /** How many ticks a member may stay off its standing formation's place without getting nearer it. */
    static final int STALL = 200;

    /** Enemies are looked for this often by a member of a formation that stands, in ticks. */
    static final int WATCH = 10;

    private Marching() {}

    /** The formation the unit keeps a place in, or null, the order finished, when it has none. */
    private static @Nullable FormationController formation(Context<MatchState> ctx) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        OrderQueues orders = s.orders();
        FormationController c = s.play().formations().byId(orders.target(i));
        int k = orders.slot(i);
        if (c == null || k >= c.size() || c.member(k) != ctx.handle()) {
            UnitAi.finishOrder(ctx, false); // its formation is gone, or it left: nothing to keep a place in
            return null;
        }
        return c;
    }

    /**
     * Sets the order's place to the slot's, if it moved; whether it did. The path service is told only when the unit
     * walks straight at the place (in line of walk, which costs it no search) or its last request is {@value
     * PathService#REPATH_EVERY} ticks old; a unit whose way to the place is blocked keeps the path it follows, and asks
     * again when that is spent or the unit is stuck, so a formation's members search about once in that many ticks.
     */
    private static boolean follow(Context<MatchState> ctx, FormationController c) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int k = s.orders().slot(i);
        double px = c.placeX(k);
        double pz = c.placeZ(k);
        double limit = c.advancing() ? PUSH : SETTLE; // a place settles on its slot once the formation stops
        if (DetMath.length(px - s.orders().targetX(i), pz - s.orders().targetZ(i)) <= limit) {
            return false;
        }
        s.orders().setPlace(i, px, pz);
        long last = s.paths().lastRequest(i);
        if (last == PathStore.NEVER || s.tick() - last >= PathService.REPATH_EVERY || inLine(s, i, px, pz)) {
            s.bus().publish(new PlaceMoved(ctx.handle()));
        }
        return true;
    }

    /** Whether unit {@code i} can walk straight to ({@code x}, {@code z}), as its request would find. */
    private static boolean inLine(MatchState s, int i, double x, double z) {
        SurfaceGrid grid = s.surfacesFor(i);
        if (grid.version() == 0) {
            return true; // no terrain: a straight walk, and no path
        }
        MotionStore motion = s.motion();
        int node = PathService.startNode(
                grid, motion.x(i), motion.y(i), motion.z(i), s.config().bounds().minY());
        return node >= 0
                && LineOfWalk.walk(
                                grid,
                                node,
                                motion.x(i),
                                motion.z(i),
                                x,
                                z,
                                s.play().gates().passFor(grid, s.identity().faction(i), s.allies()))
                        >= 0;
    }

    /** FORMATION_MARCHING's think: stand once the formation does, and the unit is on its place. */
    static void march(Context<MatchState> ctx) {
        FormationController c = formation(ctx);
        if (c == null) {
            return;
        }
        follow(ctx, c);
        if (c.holding() && onPlace(ctx, c)) {
            ctx.go(UnitAi.FORMATION_HOLDING);
        } else if (c.holding()) {
            keepTrying(ctx, c);
        } else if (ctx.data0() != 0) {
            ctx.setData(0, 0); // it walks again: no stall to count
        }
    }

    /**
     * A formation stands and the unit is not on its place: it gets nearer, or after {@value #STALL} ticks of none (a
     * place it cannot reach, crowded or walled) it leaves the formation, as a unit with an order of its own does. The
     * words are the tick of the last progress, plus one, and the least distance reached, in sixteenths of a block.
     */
    private static void keepTrying(Context<MatchState> ctx, FormationController c) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int k = s.orders().slot(i);
        double away = DetMath.length(s.motion().x(i) - c.placeX(k), s.motion().z(i) - c.placeZ(k));
        int best = (int) Math.min(Integer.MAX_VALUE, away * 16);
        if (ctx.data0() == 0 || best < ctx.data1() - PROGRESS) {
            ctx.setData((int) s.tick() + 1, best);
        } else if (s.tick() - (ctx.data0() - 1) > STALL) {
            UnitAi.finishOrder(ctx, false);
        }
    }

    /** FORMATION_MARCHING's Arrived: the unit landed on its place; it stands only if the formation does. */
    static void arrived(Context<MatchState> ctx) {
        FormationController c = formation(ctx); // a place the formation no longer has (it shrank) ends the order
        if (c != null && c.holding() && onPlace(ctx, c)) {
            ctx.go(UnitAi.FORMATION_HOLDING);
        }
    }

    private static boolean onPlace(Context<MatchState> ctx, FormationController c) {
        MatchState s = ctx.host();
        int i = ctx.unit();
        int k = s.orders().slot(i);
        return DetMath.length(s.motion().x(i) - c.placeX(k), s.motion().z(i) - c.placeZ(k)) <= REWALK;
    }

    /**
     * FORMATION_HOLDING's think: walk again once the formation is not standing, the place moves or the unit was pushed
     * off it; fight, once it stands.
     */
    static void hold(Context<MatchState> ctx) {
        FormationController c = formation(ctx);
        if (c == null) {
            return;
        }
        MatchState s = ctx.host();
        int i = ctx.unit();
        MotionStore motion = s.motion();
        boolean pushed = DetMath.length(
                        motion.x(i) - s.orders().targetX(i),
                        motion.z(i) - s.orders().targetZ(i))
                > REWALK;
        if (follow(ctx, c) || pushed || !c.holding()) {
            ctx.go(UnitAi.FORMATION_MARCHING);
        } else if (s.orders().size(i) > 1) {
            UnitAi.finishOrder(ctx, false); // an order queued after the formation's: its turn has come
        } else if (Stagger.selects(s.tick(), i, WATCH)) {
            int enemy = CombatActions.nearestEnemy(ctx);
            if (enemy >= 0) {
                CombatActions.engage(ctx, enemy);
            }
        }
    }

    /** A blow on a member of a formation that stands: it hits back, or runs, as any unit does. */
    static void struck(Context<MatchState> ctx, int attacker) {
        FormationController c =
                ctx.host().play().formations().byId(ctx.host().orders().target(ctx.unit()));
        if (c != null && c.holding()) {
            UnitAi.hit(ctx, attacker);
        }
    }
}
