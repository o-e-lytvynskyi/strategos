package dev.strategos.mod.archfixture;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/** B7's violating fixtures: writing blocks around BlockWriter. */
public final class BlockWriteFixtures {
    private BlockWriteFixtures() {}

    public static final class SetBlock {
        static boolean write(Level level, Object pos, Object state) {
            return level.setBlock(pos, state, 3);
        }
    }

    public static final class SetBlockState {
        static Object write(LevelChunk chunk, Object pos, Object state) {
            return chunk.setBlockState(pos, state, false);
        }
    }
}
