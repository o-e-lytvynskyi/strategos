package dev.strategos.mod.server.commander;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.protocol.CameraJump;
import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * camera stands still (sending a chunk pairs nothing, PlayerChunkSender.java:76).
 *
 * <p>A commander may step down into first person (CAM-05, 04 §13.7, D-08): a {@code mode_switch} at most once a second
 * and {@value #COOLDOWN_TICKS} ticks after the last switch, to a column inside the match space that is explored and
 * standable, makes it an observer in adventure mode there at the next tick's start; the next switch takes it back to
 * spectator mode with its RTS focus on where it stood. An observer stays inside the space (moved back to where it last
 * stood inside), and {@link ObserverRules} keeps it from being hurt, attacking bodies or using blocks. Server thread
 * only.
 */
public final class CommanderService {
    static final int JUMPS_PER_SECOND = 4;
    static final int REACH = 32;
    static final int SWITCH_EVERY_TICKS = 20;
    static final int COOLDOWN_TICKS = 100;
    private static final String SAVED = "strategos.commander";
    private static final String OBSERVER = "observer";

    /** A switch queued for the next tick's start: to first person at the feet position, or back. */
    private record Switch(boolean firstPerson, double x, double y, double z) {}

    private final Map<UUID, Switch> dueSwitches = new HashMap<>();
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    private final Map<UUID, double[]> lastInside = new HashMap<>();
    private final Map<UUID, MatchSpace> observing = new HashMap<>();
    private final Map<UUID, Integer> refusedOf = new HashMap<>();
    private long switches;
    private long switchesRefused;

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
        data.getCompound(SAVED).putBoolean(OBSERVER, false); // a commander who logs in again starts in the RTS view
        observing.remove(player.getUUID());
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
        dueSwitches.remove(player.getUUID());
        observing.remove(player.getUUID());
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

    /** Whether {@code player} is a commander stepped down into first person, an observer (D-08). */
    public boolean observes(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(SAVED) && data.getCompound(SAVED).getBoolean(OBSERVER);
    }

    /**
     * A commander's {@code mode_switch} in {@code rt}, queued for the next tick's start; refused, and false, within a
     * second of its last request or {@value #COOLDOWN_TICKS} ticks of its last switch, for a player who neither
     * commands nor observes, or for a drop column outside the space, unexplored or not standable.
     */
    public boolean switchMode(ServerPlayer player, MatchRuntime rt, ModeSwitch request, long tick) {
        UUID id = player.getUUID();
        Long asked = lastRequest.put(id, tick);
        Long switched = lastSwitch.get(id);
        if ((asked != null && tick - asked < SWITCH_EVERY_TICKS)
                || (switched != null && tick - switched < COOLDOWN_TICKS)) {
            return refuse(id);
        }
        if (request.firstPerson() && commands(player)) {
            double[] feet = dropPoint(player.serverLevel(), rt, request.x(), request.z());
            if (feet == null) {
                return refuse(id);
            }
            dueSwitches.put(id, new Switch(true, feet[0], feet[1], feet[2]));
        } else if (!request.firstPerson() && observes(player)) {
            dueSwitches.put(id, new Switch(false, player.getX(), player.getY(), player.getZ()));
        } else {
            return refuse(id);
        }
        lastSwitch.put(id, tick);
        observing.put(id, rt.space());
        return true;
    }

    private boolean refuse(UUID id) {
        switchesRefused++;
        refusedOf.merge(id, 1, Integer::sum);
        return false;
    }

    /**
     * Where an observer dropped at the world column ({@code x}, {@code z}) would stand: the highest place in the match
     * space's column and height band with a sturdy top below and two blocks above that hold neither a collision box
     * nor a fluid, if explored; null otherwise. Scanning the band, not a heightmap, keeps a roof above the band (a
     * GameTest arena's barriers) out of it.
     */
    static double @Nullable [] dropPoint(ServerLevel level, MatchRuntime rt, double x, double z) {
        MatchSpace space = rt.space();
        BlockPos column = BlockPos.containing(x, space.minY(), z);
        if (!level.isLoaded(column) || !space.contains(column)) {
            return null;
        }
        for (int y = space.maxY(); y > space.minY(); y--) {
            BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
            BlockPos below = feet.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    && free(level, feet)
                    && free(level, feet.above())) {
                return explored(rt, feet) ? new double[] {feet.getX() + 0.5, y, feet.getZ() + 0.5} : null;
            }
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    /** Every cell counts as explored until fog arrives (M6, D-24); then the faction's explored cells. */
    static boolean explored(MatchRuntime rt, BlockPos feet) {
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
        for (Map.Entry<UUID, Switch> e : dueSwitches.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) {
                apply(player, e.getValue());
                switches++;
            }
        }
        dueSwitches.clear();
        keepObserversInside(server);
        for (UUID id : sent) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.serverLevel().getChunkSource().move(player);
            }
        }
        sent.clear();
    }

    /** To first person: adventure mode at the feet; back: spectator mode, the RTS focus on where it stood. */
    private void apply(ServerPlayer player, Switch s) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SAVED)) {
            return;
        }
        if (s.firstPerson()) {
            data.getCompound(SAVED).putBoolean(OBSERVER, true);
            player.setGameMode(GameType.ADVENTURE);
            player.connection.teleport(s.x(), s.y(), s.z(), player.getYRot(), 0);
            lastInside.put(player.getUUID(), new double[] {s.x(), s.y(), s.z()});
        } else {
            data.getCompound(SAVED).putBoolean(OBSERVER, false);
            observing.remove(player.getUUID());
            lastInside.remove(player.getUUID());
            // RTS mode puts its focus the default height's reach ahead of the eye: here, on the feet
            double yaw = Math.toRadians(player.getYRot());
            double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
            player.setGameMode(GameType.SPECTATOR);
            player.connection.teleport(
                    s.x() + Math.sin(yaw) * back,
                    s.y() + CameraRig.DEFAULT_HEIGHT,
                    s.z() - Math.cos(yaw) * back,
                    player.getYRot(),
                    (float) CameraRig.DEFAULT_PITCH);
        }
    }

    /** An observer that walked out of its match's space, or into an unexplored cell, goes back to where it last stood. */
    private void keepObserversInside(MinecraftServer server) {
        for (Map.Entry<UUID, MatchSpace> e : observing.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            double[] last = lastInside.get(e.getKey());
            if (player == null || !observes(player) || last == null) {
                continue;
            }
            if (e.getValue().contains(player.blockPosition())) {
                lastInside.put(e.getKey(), new double[] {player.getX(), player.getY(), player.getZ()});
            } else {
                player.connection.teleport(last[0], last[1], last[2], player.getYRot(), player.getXRot());
            }
        }
    }

    public void forget(ServerPlayer player) {
        dueSwitches.remove(player.getUUID());
        lastRequest.remove(player.getUUID());
        lastSwitch.remove(player.getUUID());
        lastInside.remove(player.getUUID());
        observing.remove(player.getUUID());
        refusedOf.remove(player.getUUID());
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

    /** Mode switches applied so far. */
    public long switches() {
        return switches;
    }

    /** Mode switches refused so far. */
    public long switchesRefused() {
        return switchesRefused;
    }

    /** Mode switches refused so far to one player, for tests running beside others. */
    public int switchesRefused(UUID player) {
        return refusedOf.getOrDefault(player, 0);
    }

    /** The match {@code player}'s pending jump goes to, for tests: its target, or null. */
    @Nullable CameraJump due(ServerPlayer player) {
        return due.get(player.getUUID());
    }
}
