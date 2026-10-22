package dev.strategos.mod.server.body;

import dev.strategos.core.sim.Agent;
import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The bodies the core does not own that a match's units steer around (PATH-06, spec §6.7). Each tick, the players in
 * the match space that are neither spectators nor members of the match, whose avatars are observers that units pass
 * through (D-08), and the vanilla mobs there become the tick's {@link AgentUpdate}: by entity id, at most {@value
 * AgentUpdate#MAX_AGENTS}, each with its velocity since the last tick (none on its first) and a radius of half its
 * width within {@value #MIN_RADIUS}–{@value #MAX_RADIUS}. A tick with none has no update.
 */
public final class AgentTracker {
    static final float MIN_RADIUS = 0.3f;
    static final float MAX_RADIUS = 0.6f;

    private final MatchSpace space;
    private Map<Integer, double[]> lastAt = new HashMap<>();

    public AgentTracker(MatchSpace space) {
        this.space = space;
    }

    /** This tick's agents in {@code level}, which holds the space; {@code members} are the match's players. */
    public @Nullable AgentUpdate track(ServerLevel level, Collection<UUID> members) {
        List<Entity> found = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()
                    && player.isAlive()
                    && !members.contains(player.getUUID())
                    && space.contains(player.blockPosition())) {
                found.add(player);
            }
        }
        AABB box = new AABB(
                space.origin().getX(),
                space.minY(),
                space.origin().getZ(),
                space.origin().getX() + space.sizeX(),
                space.maxY() + 1,
                space.origin().getZ() + space.sizeZ());
        found.addAll(level.getEntitiesOfClass(Mob.class, box, Entity::isAlive));
        found.sort(Comparator.comparingInt(Entity::getId));
        Map<Integer, double[]> seen = new HashMap<>();
        List<Agent> agents = new ArrayList<>();
        for (Entity e : found) {
            if (agents.size() == AgentUpdate.MAX_AGENTS) {
                break;
            }
            double x = space.localX(e.getX());
            double z = space.localZ(e.getZ());
            double[] before = lastAt.get(e.getId());
            float vx = before == null ? 0 : (float) (x - before[0]);
            float vz = before == null ? 0 : (float) (z - before[1]);
            float radius = Mth.clamp(e.getBbWidth() / 2, MIN_RADIUS, MAX_RADIUS);
            agents.add(new Agent(e.getId(), (float) x, (float) e.getY(), (float) z, vx, vz, radius));
            seen.put(e.getId(), new double[] {x, z});
        }
        lastAt = seen;
        return agents.isEmpty() ? null : new AgentUpdate(agents);
    }
}
