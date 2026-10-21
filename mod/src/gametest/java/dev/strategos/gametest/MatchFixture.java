package dev.strategos.gametest;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.match.MatchSpace;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** Matches for GameTests: in the test's own arena, with the floor on top of the arena's floor layer. */
public final class MatchFixture {
    private MatchFixture() {}

    /** A match over the whole test bounds. */
    public static MatchRuntime create(GameTestHelper h, MatchOptions options) {
        ServerLevel level = h.getLevel();
        return StrategosServer.get().matches().create(level, MatchSpace.fromBounds(level.dimension(), h.getBounds()),
                options);
    }

    /** A match over part of the test bounds, {@code offsetX}, {@code offsetZ} from its corner. */
    public static MatchRuntime create(
            GameTestHelper h, int offsetX, int offsetZ, int sizeX, int sizeZ, MatchOptions options) {
        ServerLevel level = h.getLevel();
        MatchSpace space = MatchSpace.fromBounds(level.dimension(), h.getBounds(), offsetX, offsetZ, sizeX, sizeZ);
        return StrategosServer.get().matches().create(level, space, options);
    }

    /** An unsaved match with this seed; the arena's floor layer is the bounds' lowest, so units stand one higher. */
    public static MatchOptions options(long seed, GameTestHelper h) {
        return MatchOptions.gameTest(seed, Math.floor(h.getBounds().minY) + 1);
    }

    /** Disposes every GameTest match of the level: a batch's cleanup. */
    public static void disposeAll(ServerLevel level) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server != null) {
            server.matches().disposeAll(level, MatchSource.GAMETEST);
        }
    }
}
