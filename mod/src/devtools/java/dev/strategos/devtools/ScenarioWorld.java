package dev.strategos.devtools;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.Locale;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/** Server-side helpers of the scenarios, on the integrated server's thread. */
final class ScenarioWorld {
    private ScenarioWorld() {}

    static ServerPlayer player(IntegratedServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    /** The scenarios' match seed: with the world's fixed seed, every run places the same units the same way. */
    static final long SEED = 42;

    /**
     * The fixed point a scenario builds around: (0, floor, 0) of the flat world, the floor from the heightmap. The
     * player spawns somewhere within the spawn radius, which differs from run to run, and a camera may already stand
     * above the floor, so nothing is placed relative to the player.
     */
    static BlockPos anchor(IntegratedServer server) {
        ServerLevel level = player(server).serverLevel();
        return new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
    }

    /** A match of {@code size} x {@code size} centred on the anchor, with {@code count} units at its centre. */
    static MatchRuntime matchAtAnchor(IntegratedServer server, int size, int count, boolean persistent) {
        ServerLevel level = player(server).serverLevel();
        BlockPos feet = anchor(server);
        MatchRuntime match = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, feet, size, size),
                        MatchOptions.generated(feet.getY())
                                .withPersistent(persistent)
                                .withSeed(SEED));
        for (int left = count; left > 0; left -= 300) {
            match.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(
                            TemplateSet.DEBUG_WALKER,
                            new FactionSlot(0),
                            new Vec2d(size / 2.0, size / 2.0),
                            Math.min(300, left),
                            Math.min(size / 4.0, 12)));
        }
        return match;
    }

    /**
     * The camera: the player in spectator mode at a fixed pose, at noon with the day and weather cycles off, so the
     * light is the same in every run, and with no command feedback in the chat.
     */
    static void camera(IntegratedServer server, double x, double y, double z, float yaw, float pitch) {
        String name = player(server).getGameProfile().getName();
        run(server, "gamerule sendCommandFeedback false");
        run(server, "gamerule doDaylightCycle false");
        run(server, "gamerule doWeatherCycle false");
        run(server, "time set 6000");
        run(server, "weather clear");
        run(server, "gamemode spectator " + name);
        run(server, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f %.1f", name, x, y, z, yaw, pitch));
    }

    /**
     * The client's render distance, sent to the server too: the server loads and sends chunks by the player's requested
     * view distance, which only the client-information packet carries, and setting the option alone sends none, so the
     * server kept the harness's 5 (M3 T30).
     */
    static void renderDistance(net.minecraft.client.Minecraft mc, int chunks) {
        mc.options.renderDistance().set(chunks);
        mc.options.broadcastOptions();
    }

    static void run(IntegratedServer server, String command) {
        server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
