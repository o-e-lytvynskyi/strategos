package dev.strategos.mod.server.fog;

import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.vision.VisionSystem;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Makes vanilla's tracking ask the fog gate again (FOW-07): every {@value #EVERY} ticks, at offset {@value #OFFSET},
 * each seated player whose faction's visibility changed since the last refresh gets {@code ChunkMap#move} through
 * {@code ServerChunkCache#move}, which re-runs {@code broadcastToPlayer} for the entities around the player.
 */
public final class TrackerRefresher {
    static final int EVERY = 5;
    static final int OFFSET = 1;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final Map<Long, Integer> seen = new HashMap<>();

    public TrackerRefresher(MinecraftServer server, Supplier<MatchRegistry> matches) {
        this.server = server;
        this.matches = matches;
    }

    static boolean due(int tick) {
        return tick % EVERY == OFFSET;
    }

    public void tick() {
        int now = server.getTickCount();
        if (!due(now)) {
            return;
        }
        MatchRegistry registry = matches.get();
        seen.keySet().removeIf(key -> registry.get(new MatchId((int) (key >> 8))) == null);
        for (MatchRuntime rt : registry.all()) {
            MatchCore core = rt.core();
            if (core == null || core.vision() == null || rt.lifecycle() != MatchLifecycle.RUNNING) {
                continue;
            }
            VisionSystem vision = core.vision();
            int factions = core.queries().config().factions();
            boolean[] changed = new boolean[factions];
            for (int f = 0; f < factions; f++) {
                long key = ((long) rt.id().value() << 8) | f;
                int version = vision.visibility(f).version();
                Integer last = seen.put(key, version);
                changed[f] = last == null || last != version;
            }
            for (UUID member : rt.roster().members()) {
                var slot = rt.roster().slotOf(member);
                if (slot == null || slot.value() < 0 || slot.value() >= factions || !changed[slot.value()]) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                if (player != null) {
                    player.serverLevel().getChunkSource().move(player);
                }
            }
        }
    }
}
