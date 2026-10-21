package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.HotPath;
import java.util.ArrayDeque;

/**
 * The hash of a match's whole state, in a fixed order: the tick; the seed and the bounds; per index up to the high-water
 * mark, its generation and liveness, and for live units the template index, faction, x, y, z, yaw, state and order;
 * then every pending command. The match id is left out, so the L1 twin of a GameTest match agrees. The unit loop
 * mixes a plain {@code long}, so a hash without pending commands allocates nothing.
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
}
