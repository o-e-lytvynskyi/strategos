package dev.strategos.gametest;

import dev.strategos.mod.platform.Ids;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * Map-scale GameTests force chunks only through this controller, so the batch-end check sees every ticket a test left
 * behind (QA-04.6). Registered on the GameTest server only; server thread only.
 */
public final class GameTestTickets {
    public static final TicketController CONTROLLER = new TicketController(Ids.id("gametest_tickets"));

    private static final UUID OWNER =
            UUID.nameUUIDFromBytes("strategos:gametest_tickets".getBytes(StandardCharsets.UTF_8));
    private static final Map<ResourceKey<Level>, TreeSet<Long>> HELD = new HashMap<>();

    private GameTestTickets() {}

    /** Loads the chunk and keeps it loaded until {@link #release}. */
    public static void force(ServerLevel level, ChunkPos chunk) {
        CONTROLLER.forceChunk(level, OWNER, chunk.x, chunk.z, true, false);
        HELD.computeIfAbsent(level.dimension(), k -> new TreeSet<>()).add(chunk.toLong());
    }

    public static void release(ServerLevel level, ChunkPos chunk) {
        CONTROLLER.forceChunk(level, OWNER, chunk.x, chunk.z, false, false);
        TreeSet<Long> held = HELD.get(level.dimension());
        if (held != null) {
            held.remove(chunk.toLong());
        }
    }

    /** The chunks this controller still forces in the level, in a fixed order. */
    public static List<ChunkPos> held(ServerLevel level) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (long key : HELD.getOrDefault(level.dimension(), new TreeSet<>())) {
            chunks.add(new ChunkPos(key));
        }
        return chunks;
    }

    public static void releaseAll(ServerLevel level) {
        for (ChunkPos chunk : held(level)) {
            release(level, chunk);
        }
    }
}
