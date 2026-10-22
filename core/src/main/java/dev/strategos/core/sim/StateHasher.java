package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.nav.PathStore;
import java.util.ArrayDeque;

/**
 * The hash of a match's whole state, in a fixed order: the tick; the seed and the bounds; per index up to the high-water
 * mark, its generation and liveness, and for live units the template index, faction, x, y, z, yaw, state and order,
 * and, once the unit has asked for a path, the path (request, last request, repath flag, status, cursor, waypoints);
 * then, once any path was requested, the next request id, the last tick a page changed and each request in flight (id, unit, due tick); then every
 * pending command. A match that never asks for a path, such as one without terrain, hashes as in M0. The match id is
 * left out, so the L1 twin of a GameTest match agrees; so are the terrain grid, which the twin rebuilds from the same
 * pages, and the results in flight, which a job may not have computed yet. The unit loop mixes a plain {@code long},
 * so a hash without pending commands or path requests in flight allocates nothing.
 */
@HotPath
public final class StateHasher {
    private StateHasher() {}

    public static long hash(MatchState s) {
        long h = Hasher.start(0);
        h = Hasher.mix(h, s.tick());
        MatchConfig config = s.config();
        MatchBounds bounds = config.bounds();
        h = Hasher.mix(h, config.seed());
        h = Hasher.mix(h, bounds.sizeX());
        h = Hasher.mix(h, bounds.sizeZ());
        h = Hasher.mix(h, bounds.minY());
        h = Hasher.mix(h, bounds.maxY());
        h = Hasher.mix(h, Double.doubleToLongBits(bounds.flatFloorY()));

        EntityStore entities = s.entities();
        IdentityStore identity = s.identity();
        MotionStore motion = s.motion();
        OrderStore orders = s.orders();
        AiStateStore ai = s.ai();
        PathStore paths = s.paths();
        int highWater = entities.highWater();
        h = Hasher.mix(h, highWater);
        for (int i = 0; i < highWater; i++) {
            h = Hasher.mix(h, entities.generationAt(i));
            boolean alive = entities.aliveAt(i);
            h = Hasher.mix(h, alive ? 1L : 0L);
            if (alive) {
                h = Hasher.mix(h, identity.templateIndex(i));
                h = Hasher.mix(h, identity.faction(i));
                h = Hasher.mix(h, Double.doubleToLongBits(motion.x(i)));
                h = Hasher.mix(h, Double.doubleToLongBits(motion.y(i)));
                h = Hasher.mix(h, Double.doubleToLongBits(motion.z(i)));
                h = Hasher.mix(h, Float.floatToIntBits(motion.yaw(i)));
                h = Hasher.mix(h, ai.state(i).code());
                h = Hasher.mix(h, orders.kind(i));
                h = Hasher.mix(h, Double.doubleToLongBits(orders.targetX(i)));
                h = Hasher.mix(h, Double.doubleToLongBits(orders.targetZ(i)));
                if (paths.lastRequest(i) != PathStore.NEVER) {
                    h = mixPath(h, paths, i);
                }
            }
        }

        if (paths.peekNextRequestId() != 1 || !paths.pending().isEmpty()) {
            h = Hasher.mix(h, paths.peekNextRequestId());
            h = Hasher.mix(h, paths.lastPageChange());
            h = Hasher.mix(h, paths.pending().size());
            if (!paths.pending().isEmpty()) {
                for (PathStore.Pending p : paths.pending()) {
                    h = Hasher.mix(h, p.request().id());
                    h = Hasher.mix(h, p.request().handle());
                    h = Hasher.mix(h, p.due());
                }
            }
        }

        ArrayDeque<CommandEnvelope> pending = s.pendingCommands();
        h = Hasher.mix(h, pending.size());
        if (!pending.isEmpty()) {
            Hasher hasher = Hasher.resume(h);
            for (CommandEnvelope envelope : pending) {
                envelope.hashInto(hasher);
            }
            h = hasher.state();
        }
        return Hasher.finish(h);
    }

    private static long mixPath(long h, PathStore paths, int i) {
        h = Hasher.mix(h, paths.request(i));
        h = Hasher.mix(h, paths.lastRequest(i));
        h = Hasher.mix(h, paths.repath(i) ? 1L : 0L);
        h = Hasher.mix(h, paths.status(i).code());
        h = Hasher.mix(h, paths.cursor(i));
        PathStore.Waypoints w = paths.path(i);
        h = Hasher.mix(h, w.count());
        h = Hasher.mix(h, w.truncated() ? 1L : 0L);
        for (int k = 0; k < w.count(); k++) {
            h = Hasher.mix(h, Double.doubleToLongBits(w.x(k)));
            h = Hasher.mix(h, Double.doubleToLongBits(w.z(k)));
            h = Hasher.mix(h, w.floor(k));
        }
        return h;
    }
}
