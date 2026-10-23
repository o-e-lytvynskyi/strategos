package dev.strategos.mod.server.commander;

import dev.strategos.core.protocol.CameraJump;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * The commanders (CAM-03, ADR-0017, 04 §13.2): a player seated in a match becomes a spectator whose body is the RTS
 * camera, its own game mode and place kept in its persistent data, so they come back when it leaves the match, even
 * after a restart. A {@code camera_jump} is checked here (a commander only, at most {@value #JUMPS_PER_SECOND} a
 * second, inside the match space plus {@value #REACH} blocks) and teleports at the start of the next tick, before the
 * connections tick, which spike S9 found draws no "moved too quickly". After a chunk reaches a commander, the next
 * tick re-runs the chunk map's pairing for it once, so bodies in chunks sent after a jump pair at once even when the
 * camera stands still (sending a chunk pairs nothing, PlayerChunkSender.java:76). Server thread only.
 */
public final class CommanderService {
    static final int JUMPS_PER_SECOND = 4;
    static final int REACH = 32;
    private static final String SAVED = "strategos.commander";

    private final Map<UUID, CameraJump> due = new HashMap<>();
    private final Map<UUID, List<Long>> recentJumps = new HashMap<>();
    private final Set<UUID> sent = new HashSet<>();
    private long jumps;
    private long refused;

    /** Makes {@code player} the commander of {@code rt}: a spectator above the space's centre, its own state kept. */
    public void enter(ServerPlayer player, MatchRuntime rt) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SAVED)) {
            CompoundTag saved = new CompoundTag();
            saved.putInt("mode", player.gameMode.getGameModeForPlayer().getId());
            saved.putString("level", player.serverLevel().dimension().location().toString());
            saved.putDouble("x", player.getX());
            saved.putDouble("y", player.getY());
            saved.putDouble("z", player.getZ());
            data.put(SAVED, saved);
        }
        player.setGameMode(GameType.SPECTATOR);
        MatchSpace space = rt.space();
        double x = space.origin().getX() + space.sizeX() / 2.0;
        double z = space.origin().getZ() + space.sizeZ() / 2.0;
        player.connection.teleport(x, space.origin().getY() + 24, z, 0, 55);
    }

    /** Gives {@code player} back the game mode and place it had before it commanded; nothing if it never did. */
    public void leave(MinecraftServer server, ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SAVED)) {
            return;
        }
        CompoundTag saved = data.getCompound(SAVED);
        data.remove(SAVED);
        due.remove(player.getUUID());
        player.setGameMode(GameType.byId(saved.getInt("mode")));
        ResourceLocation id = ResourceLocation.tryParse(saved.getString("level"));
        ServerLevel level =
                id == null ? server.overworld() : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        player.teleportTo(
                level == null ? server.overworld() : level,
                saved.getDouble("x"),
                saved.getDouble("y"),
                saved.getDouble("z"),
                player.getYRot(),
                player.getXRot());
    }

    /** Whether {@code player} commands now: it entered and has not left. */
    public boolean commands(ServerPlayer player) {
        return player.getPersistentData().contains(SAVED) && player.isSpectator();
    }

    /**
     * A commander's jump in {@code rt}, queued for the next tick's start; refused, and false, for a player who does not
     * command, a fifth jump within a second, or a point beyond the space and its reach.
     */
    public boolean jump(ServerPlayer player, MatchRuntime rt, CameraJump jump, long tick) {
        List<Long> recent = recentJumps.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        recent.removeIf(t -> tick - t >= 20);
        MatchSpace s = rt.space();
        boolean inside = jump.x() >= s.origin().getX() - REACH
                && jump.x() <= s.origin().getX() + s.sizeX() + REACH
                && jump.z() >= s.origin().getZ() - REACH
                && jump.z() <= s.origin().getZ() + s.sizeZ() + REACH
                && jump.y() >= s.minY() - REACH
                && jump.y() <= s.maxY() + 2 * REACH;
        if (!commands(player) || recent.size() >= JUMPS_PER_SECOND || !inside) {
            refused++;
            return false;
        }
        recent.add(tick);
        due.put(player.getUUID(), jump);
        return true;
    }

    /** A chunk reached {@code player}: if it commands, its pairing re-runs at the next tick's start. */
    public void chunkSent(ServerPlayer player) {
        if (commands(player)) {
            sent.add(player.getUUID());
        }
    }

    /** The tick's start, before the connections tick: due jumps teleport, then marked commanders re-pair. */
    public void tickStarts(MinecraftServer server) {
        for (Map.Entry<UUID, CameraJump> e : due.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            CameraJump j = e.getValue();
            if (player != null) {
                player.connection.teleport(j.x(), j.y(), j.z(), j.yaw(), j.pitch());
                jumps++;
            }
        }
        due.clear();
        for (UUID id : sent) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.serverLevel().getChunkSource().move(player);
            }
        }
        sent.clear();
    }

    public void forget(ServerPlayer player) {
        due.remove(player.getUUID());
        recentJumps.remove(player.getUUID());
        sent.remove(player.getUUID());
    }

    /** Jumps teleported so far. */
    public long jumps() {
        return jumps;
    }

    /** Jumps refused so far. */
    public long refused() {
        return refused;
    }

    /** The match {@code player}'s pending jump goes to, for tests: its target, or null. */
    @Nullable CameraJump due(ServerPlayer player) {
        return due.get(player.getUUID());
    }
}
