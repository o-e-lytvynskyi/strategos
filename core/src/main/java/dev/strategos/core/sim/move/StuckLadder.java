package dev.strategos.core.sim.move;

import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.PathInvalidated;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.Spiral;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.spatial.UnitFilter;
import java.util.function.IntConsumer;

/**
 * The stuck ladder (PATH-07, 04 §9.5, spec §5.11): what a walking unit does as its count of ticks without progress, the
 * stuck monitor's, climbs.
 *
 * <ol>
 *   <li>{@value #REPATH}: it asks for a new path;
 *   <li>{@value #STEP}: it walks, for up to {@value #STEP_TICKS} ticks, to the nearest free spot within {@value
 *       #STEP_RANGE} columns: the centre of a column of the match with a passable surface of the unit's component
 *       within two blocks of its height, and no other unit within {@value #FREE};
 *   <li>{@value #GHOST}: it walks its path {@value #GHOST_TICKS} ticks with avoidance off;
 *   <li>{@value #NUDGE}: it is displaced along its path, over walkable surfaces, to the first free spot from one block
 *       to {@value #NUDGE_RANGE} ahead, in steps of half a block;
 *   <li>{@value #FAIL}: its order fails, and its faction hears a STUCK notice.
 * </ol>
 *
 * A new route is no progress, nor is the nudge's displacement, so a unit that stays stuck climbs to the end; coming
 * nearer its aim or passing a waypoint on its own feet starts it again from the bottom. Every rung is posted as {@link
 * Stuck} and counted per match, and a unit's rung follows from its count ({@link #rung}). No rung takes a unit out of
 * the match space, and none teleports; footprints arrive with buildings (BLD-03).
 */
public final class StuckLadder {
    static final int REPATH = 40;
    static final int STEP = 100;
    static final int GHOST = 200;
    static final int NUDGE = 400;
    static final int FAIL = 600;
    static final int STEP_TICKS = 40;
    static final int GHOST_TICKS = 60;
    static final int STEP_RANGE = 3;
    static final double FREE = 0.6;
    static final double NUDGE_RANGE = 4;

    /** A detour ends this near its spot. */
    static final double THERE = 0.1;

    /** The nudge looks along the path in steps of this length. */
    private static final double NUDGE_STEP = 0.5;

    /** How near a displacement must end to its point to go on along the next leg. */
    private static final double ON_LEG = 1e-6;

    private final int[] spiral = Spiral.of(STEP_RANGE);
    private final IntConsumer others = this::other;
    private int self;
    private int near;

    StuckLadder() {}

    /** The rung a count of {@code stuck} ticks without progress has reached: 0 below the first, then 1 to 5. */
    public static int rung(int stuck) {
        if (stuck >= FAIL) {
            return Stuck.GIVE_UP;
        }
        if (stuck >= NUDGE) {
            return 4;
        }
        if (stuck >= GHOST) {
            return 3;
        }
        if (stuck >= STEP) {
            return 2;
        }
        return stuck >= REPATH ? 1 : 0;
    }

    /** Counts unit {@code i}'s detour and ghost walk down after its stride; a detour ends at its spot. */
    static void countDown(MotionStore motion, int i) {
        int detour = motion.detourTicks(i);
        if (detour > 0) {
            double dx = motion.detourX(i) - motion.x(i);
            double dz = motion.detourZ(i) - motion.z(i);
            int left = DetMath.length(dx, dz) <= THERE ? 0 : detour - 1;
            motion.setDetour(i, motion.detourX(i), motion.detourZ(i), left);
        }
        if (motion.ghostTicks(i) > 0) {
            motion.setGhostTicks(i, motion.ghostTicks(i) - 1);
        }
    }

    /** The rung unit {@code i}, which walks and just counted a tick, reached, if its count is one's threshold. */
    void climb(MatchState s, int i, int handle) {
        int rung = switch (s.motion().stuck(i)) {
            case REPATH -> 1;
            case STEP -> 2;
            case GHOST -> 3;
            case NUDGE -> 4;
            case FAIL -> Stuck.GIVE_UP;
            default -> 0;
        };
        if (rung == 0) {
            return;
        }
        s.countRung(rung);
        switch (rung) {
            case 1 -> {
                if (!s.paths().repath(i)) {
                    s.bus().publish(new PathInvalidated(handle));
                }
            }
            case 2 -> step(s, i);
            case 3 -> s.motion().setGhostTicks(i, GHOST_TICKS);
            case 4 -> nudge(s, i, handle);
            default ->
                s.outputs()
                        .add(new Notice(
                                new FactionSlot(s.identity().faction(i)),
                                NoticeKind.STUCK,
                                handle,
                                "no progress for " + FAIL + " ticks; the order failed"));
        }
        s.bus().publish(new Stuck(handle, rung));
    }

    /** Rung 2: a short walk to the nearest free spot other than the unit's own column, if there is one. */
    private void step(MatchState s, int i) {
        MotionStore motion = s.motion();
        int component = component(s, i);
        int ox = (int) Math.floor(motion.x(i));
        int oz = (int) Math.floor(motion.z(i));
        for (int c = 2; c < spiral.length; c += 2) {
            int x = ox + spiral[c];
            int z = oz + spiral[c + 1];
            if (free(s, i, x, z, component)) {
                motion.setDetour(i, x + 0.5, z + 0.5, STEP_TICKS);
                return;
            }
        }
    }

    /**
     * Rung 4: along the path from the unit through its waypoints ahead, the first point from one block on, every half
     * block up to {@value #NUDGE_RANGE}, whose column is free; the unit is displaced to it leg by leg over walkable
     * surfaces, stopping where it is blocked, and passes the waypoints it went beyond. Its best distance follows, so
     * the nudge is no progress.
     */
    private void nudge(MatchState s, int i, int handle) {
        MotionStore motion = s.motion();
        PathStore paths = s.paths();
        PathStore.Waypoints path = paths.path(i);
        int component = component(s, i);
        int cursor = paths.cursor(i);
        double px = motion.x(i);
        double pz = motion.z(i);
        double walked = 0;
        int leg = -1;
        double along = 0;
        for (int k = cursor; k < path.count() && walked < NUDGE_RANGE && leg < 0; k++) {
            double length = DetMath.length(path.x(k) - px, path.z(k) - pz);
            for (double t = NUDGE_STEP; t <= length && walked + t <= NUDGE_RANGE; t += NUDGE_STEP) {
                double sx = px + (path.x(k) - px) * t / length;
                double sz = pz + (path.z(k) - pz) * t / length;
                if (walked + t >= 1 && free(s, i, (int) Math.floor(sx), (int) Math.floor(sz), component)) {
                    leg = k;
                    along = t;
                    break;
                }
            }
            walked += length;
            px = path.x(k);
            pz = path.z(k);
        }
        if (leg < 0) {
            return;
        }
        for (int k = cursor; k <= leg; k++) {
            double fx = motion.x(i);
            double fz = motion.z(i);
            double length = DetMath.length(path.x(k) - fx, path.z(k) - fz);
            double t = k == leg ? Math.min(along, length) : length;
            double ex = length == 0 ? fx : fx + (path.x(k) - fx) * t / length;
            double ez = length == 0 ? fz : fz + (path.z(k) - fz) * t / length;
            MotionSystem.impulse(s, handle, ex - fx, ez - fz);
            if (DetMath.length(ex - motion.x(i), ez - motion.z(i)) > ON_LEG) {
                break; // blocked: the rest of the way starts from a place the unit is not at
            }
            if (k < leg) {
                paths.advance(i); // went beyond that waypoint
            }
        }
        double d = DetMath.length(motion.aimX(i) - motion.x(i), motion.aimZ(i) - motion.z(i));
        motion.setProgress(i, motion.aimX(i), motion.aimZ(i), d, motion.stuck(i));
    }

    /** The component of the surface unit {@code i} stands on; -1 when it stands on none. */
    private static int component(MatchState s, int i) {
        MotionStore motion = s.motion();
        SurfaceGrid grid = s.surfacesFor(i);
        int home = PathService.startNode(
                grid, motion.x(i), motion.y(i), motion.z(i), s.config().bounds().minY());
        return home < 0 ? -1 : grid.component(home);
    }

    /**
     * Whether column (x, z) is a free spot for unit {@code i}: inside the match, with a passable surface of {@code
     * component} (any, for -1) within two blocks of the unit's height, and no other unit within {@value #FREE} of its
     * centre.
     */
    private boolean free(MatchState s, int i, int x, int z, int component) {
        SurfaceGrid grid = s.surfacesFor(i);
        if (!grid.inside(x, z)) {
            return false;
        }
        double y = s.motion().y(i);
        int minY = s.config().bounds().minY();
        int count = grid.count(x, z);
        boolean surface = false;
        for (int layer = 0; layer < count && !surface; layer++) {
            int at = grid.surface(x, z, layer);
            surface = Surface.passable(at)
                    && (component < 0 || grid.component(grid.node(x, z, layer)) == component)
                    && Math.abs(minY + Surface.floor(at) / 16.0 - y) < 2;
        }
        if (!surface) {
            return false;
        }
        self = i;
        near = 0;
        s.spatial().forEachInRadius(x + 0.5, z + 0.5, FREE, UnitFilter.ANY, others);
        return near == 0;
    }

    private void other(int unit) {
        if (unit != self) {
            near++;
        }
    }
}
