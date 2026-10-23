package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import java.util.ArrayDeque;

/**
 * The hash of a match's whole state, in a fixed order: the tick; the seed and the bounds; per index up to the high-water
 * mark, its generation and liveness, and for live units the template index, faction, x, y, z, yaw, the code of its
 * machine's leaf (IDLE and WALKING keep M0's codes) and, once either is set, its two data words, then its order,
 * and, once the unit has asked for a path, the path (request, last request, repath flag, status, cursor, waypoints),
 * and, once it has touched terrain, its velocity, floor, step, stuck monitor and arrival latch, and, once hurt, the
 * damage it has taken;
 * then, once any path was requested, the next request id, the last tick a page changed and each request in flight (id, unit, due tick); then,
 * once a faction holds or spent anything, every treasury counter (ECO-01), and once the core edited its terrain, every
 * edit; then every pending command. A match that never asks for a path, such as one without terrain, hashes as in M0. The match id is
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
        OrderQueues orders = s.orders();
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
                h = Hasher.mix(h, ai.code(i));
                if (!ai.plain(i)) {
                    h = Hasher.mix(h, ai.data0(i));
                    h = Hasher.mix(h, ai.data1(i));
                }
                h = Hasher.mix(h, orders.kind(i));
                h = Hasher.mix(h, Double.doubleToLongBits(orders.targetX(i)));
                h = Hasher.mix(h, Double.doubleToLongBits(orders.targetZ(i)));
                if (paths.lastRequest(i) != PathStore.NEVER) {
                    h = mixPath(h, paths, i);
                }
                if (!motion.untouched(i)) {
                    h = mixMotion(h, motion, i);
                }
                if (orders.size(i) > 1 || orders.group(i) != 0 || orders.slot(i) != 0) {
                    h = mixQueue(h, orders, i); // only once a unit queues or groups, so older states keep their hashes
                }
                if (s.health().damage(i) != 0) { // only once a unit is hurt, so older states keep their hashes
                    h = Hasher.mix(h, Double.doubleToLongBits(s.health().damage(i)));
                }
            }
        }

        if (s.nextGroup() != MatchState.FIRST_GROUP) {
            h = Hasher.mix(h, s.nextGroup());
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
            if (!paths.waiting()
                    .isEmpty()) { // only while requests wait for admission, so older states keep their hashes
                h = Hasher.mix(h, paths.waiting().size());
                for (PathStore.Waiting w : paths.waiting()) {
                    h = Hasher.mix(h, w.id());
                    h = Hasher.mix(h, w.handle());
                    if (w.group() != 0) {
                        h = Hasher.mix(h, w.group());
                    }
                }
            }
        }

        if (!s.treasury().empty()) { // only once a faction holds or spent something, so older states keep their hashes
            h = s.treasury().mix(h);
        }
        if (!s.terrainEdits().empty()) { // only once the core edited its terrain (M3)
            h = s.terrainEdits().mix(h);
        }
        if (s.nodes().count() > 0) { // only once a match found resource nodes (ECO-02)
            h = s.nodes().mix(h);
        }
        if (!s.buildings().empty()) { // only once a building stood (BLD-01)
            h = s.buildings().mix(h);
        }
        if (!s.carry().empty()) { // only while a unit carries something (ECO-05)
            h = s.carry().mix(h, s.entities().highWater());
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

    private static long mixQueue(long h, OrderQueues orders, int i) {
        h = Hasher.mix(h, orders.size(i));
        for (int k = 0; k < orders.size(i); k++) {
            OrderQueues.Order o = orders.order(i, k);
            h = Hasher.mix(h, o.kind());
            h = Hasher.mix(h, Double.doubleToLongBits(o.x()));
            h = Hasher.mix(h, Double.doubleToLongBits(o.z()));
            h = Hasher.mix(h, o.group());
            h = Hasher.mix(h, o.slot());
            if (OrderKind.targets(o.kind())) { // only attacks and follows hold one: older queues hash as before
                h = Hasher.mix(h, o.target());
            }
        }
        return h;
    }

    private static long mixMotion(long h, MotionStore motion, int i) {
        h = Hasher.mix(h, Double.doubleToLongBits(motion.vx(i)));
        h = Hasher.mix(h, Double.doubleToLongBits(motion.vz(i)));
        h = Hasher.mix(h, motion.floor(i));
        h = Hasher.mix(h, motion.rising(i) ? 1L : 0L);
        h = Hasher.mix(h, Double.doubleToLongBits(motion.aimX(i)));
        h = Hasher.mix(h, Double.doubleToLongBits(motion.aimZ(i)));
        h = Hasher.mix(h, Double.doubleToLongBits(motion.best(i)));
        h = Hasher.mix(h, motion.stuck(i));
        h = Hasher.mix(h, motion.latched(i) ? 1L : 0L);
        h = Hasher.mix(h, Double.doubleToLongBits(motion.latchX(i)));
        h = Hasher.mix(h, Double.doubleToLongBits(motion.latchZ(i)));
        if (motion.pushed(i) != 0) {
            h = Hasher.mix(h, motion.pushed(i)); // only once avoidance pushed it, so older states keep their hashes
        }
        if (motion.detourTicks(i) != 0 || motion.ghostTicks(i) != 0) { // only on the stuck ladder's rungs
            h = Hasher.mix(h, Double.doubleToLongBits(motion.detourX(i)));
            h = Hasher.mix(h, Double.doubleToLongBits(motion.detourZ(i)));
            h = Hasher.mix(h, motion.detourTicks(i));
            h = Hasher.mix(h, motion.ghostTicks(i));
        }
        return h;
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
