package dev.strategos.mod.server.world;

import dev.strategos.core.shared.MatchId;
import dev.strategos.mod.registry.ModTickets;
import dev.strategos.mod.server.match.MatchRecord;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.persist.Quarantine;
import dev.strategos.mod.server.persist.RegistryData;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An arena match's chunk tickets (04 §3.4, MAP-07.4): preparing the match forces every chunk of its space with the
 * match's own UUID as the owner (TicketController.java:67), not ticking, since entities tick on any region ticket;
 * disposing it releases them. NeoForge saves the tickets with the level, and when a level loads, {@link #validate}
 * drops those of UUIDs that name no saved match of that level (LoadingValidationCallback.java:18). GameTest matches
 * live inside their structure and place none.
 */
public final class ArenaTickets {
    private static final Logger LOG = LoggerFactory.getLogger(ArenaTickets.class);

    private ArenaTickets() {}

    /** The tickets' owner: a UUID derived from the match id, so it is the same after a restart. */
    public static UUID owner(MatchId id) {
        return UUID.nameUUIDFromBytes(("strategos:match:" + id.value()).getBytes(StandardCharsets.UTF_8));
    }

    /** Forces every chunk of {@code space}; each new chunk loads now (ForcedChunkManager.java:96). */
    public static void force(ServerLevel level, MatchId id, MatchSpace space) {
        set(level, id, space, true);
    }

    public static void release(ServerLevel level, MatchId id, MatchSpace space) {
        set(level, id, space, false);
    }

    private static void set(ServerLevel level, MatchId id, MatchSpace space, boolean add) {
        UUID owner = owner(id);
        ChunkPos min = space.minChunk();
        ChunkPos max = space.maxChunk();
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ModTickets.MATCH.forceChunk(level, owner, x, z, add, false);
            }
        }
    }

    /**
     * The validation callback, run while the server prepares its levels, before {@code ServerStartedEvent}: it reads
     * the registry blob itself and keeps only the tickets of matches it lists in this level. A quarantined registry
     * cannot say which matches live, so every ticket stays.
     */
    public static void validate(ServerLevel level, TicketHelper helper) {
        RegistryData registry = Quarantine.loadRegistry(level.getServer().overworld(), System.currentTimeMillis());
        if (registry.quarantineReason() != null) {
            LOG.warn("Match tickets kept unchecked: the registry is quarantined ({})", registry.quarantineReason());
            return;
        }
        Set<UUID> live = new HashSet<>();
        for (MatchRecord record : registry.records()) {
            if (record.space().level().equals(level.dimension())) {
                live.add(owner(record.id()));
            }
        }
        for (UUID owner : List.copyOf(helper.getEntityTickets().keySet())) {
            if (!live.contains(owner)) {
                helper.removeAllTickets(owner);
                LOG.info("Dropped the chunk tickets of {}: no saved match of {} owns them", owner, level.dimension());
            }
        }
    }
}
