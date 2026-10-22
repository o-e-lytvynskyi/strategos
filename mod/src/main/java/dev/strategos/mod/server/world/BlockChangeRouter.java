package dev.strategos.mod.server.world;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Where block changes go (04 §9.7, PATH-15): every change of a server level's loaded chunk, from {@code
 * LevelChunkMixin}. Client levels and Strategos's own writes ({@link OwnWrite}) are ignored, the core updates its own
 * overlays for those; a change from another thread is handed to the server thread. One region lookup finds the match
 * whose space holds the block, and only a change of the block's nav class dirties a page there.
 */
public final class BlockChangeRouter {
    private BlockChangeRouter() {}

    public static void onChanged(LevelChunk chunk, BlockPos pos, BlockState previous, BlockState state) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        StrategosServer s = StrategosServer.getOrNull();
        if (s == null) {
            return;
        }
        if (!s.server().isSameThread()) {
            BlockPos at = pos.immutable();
            s.server().execute(() -> route(s, level, at, previous, state));
            return;
        }
        route(s, level, pos, previous, state);
    }

    /** The server thread's part: own writes skipped, one lookup, and the page dirtied when the class changed. */
    public static void route(
            StrategosServer s, ServerLevel level, BlockPos pos, BlockState previous, BlockState state) {
        if (s.writer().ownWrite().active()) {
            return;
        }
        MatchRuntime rt = s.matches().at(level, pos);
        if (rt == null) {
            return;
        }
        BlockClassTable table = s.blockClasses();
        if (table.classOf(previous) != table.classOf(state)) {
            rt.pages().changed(pos, rt.tick());
        }
    }
}
