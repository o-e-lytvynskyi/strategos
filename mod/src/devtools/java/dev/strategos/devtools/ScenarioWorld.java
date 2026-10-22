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

/** Server-side helpers of the scenarios, on the integrated server's thread. */
final class ScenarioWorld {
    private ScenarioWorld() {}

    static ServerPlayer player(IntegratedServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    /** A match of {@code size} x {@code size} centred on the player, with {@code count} units at its centre. */
    static MatchRuntime matchAtPlayer(IntegratedServer server, int size, int count, boolean persistent) {
        ServerPlayer player = player(server);
        ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        MatchRuntime match = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, feet, size, size),
                        MatchOptions.generated(feet.getY()).withPersistent(persistent));
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

    /** The camera: the player in spectator mode at a fixed pose. */
    static void camera(IntegratedServer server, double x, double y, double z, float yaw, float pitch) {
        String name = player(server).getGameProfile().getName();
        run(server, "gamemode spectator " + name);
        run(server, String.format(Locale.ROOT, "tp %s %.3f %.3f %.3f %.1f %.1f", name, x, y, z, yaw, pitch));
    }

    static void run(IntegratedServer server, String command) {
        server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
