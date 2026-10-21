package dev.strategos.mod;

import dev.strategos.mod.server.body.SpawnBudget;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.tick.FaultBarrier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.Nullable;

/**
 * Everything Strategos keeps for one running server, from {@code ServerAboutToStartEvent} (before the levels exist)
 * to {@code ServerStoppedEvent}: an integrated server gets a fresh one per world. The one static holder (rule B8's
 * first exception); everything else hangs off it.
 */
public final class StrategosServer {
    private static volatile @Nullable StrategosServer current;

    private final MinecraftServer server;
    private final StrategosSettings settings;
    private final SpawnBudget spawnBudget = new SpawnBudget();
    private final FaultBarrier faults;
    private final MatchRegistry matches;

    private StrategosServer(MinecraftServer server, StrategosSettings settings) {
        this.server = server;
        this.settings = settings;
        this.faults = new FaultBarrier(server);
        this.matches = new MatchRegistry(server, settings, spawnBudget);
    }

    /** The running server's holder; throws when no server runs. */
    public static StrategosServer get() {
        StrategosServer s = current;
        if (s == null) {
            throw new IllegalStateException("no Strategos server");
        }
        return s;
    }

    public static @Nullable StrategosServer getOrNull() {
        return current;
    }

    public MinecraftServer server() {
        return server;
    }

    public MatchRegistry matches() {
        return matches;
    }

    public StrategosSettings settings() {
        return settings;
    }

    public FaultBarrier faults() {
        return faults;
    }

    public SpawnBudget spawnBudget() {
        return spawnBudget;
    }

    public static void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        current = new StrategosServer(server, StrategosSettings.fromSystemProperties(server.getServerDirectory()));
    }

    /** The levels exist now: the saved matches come back, PAUSED. */
    public static void onStarted(ServerStartedEvent event) {
        StrategosServer s = current;
        if (s != null) {
            s.matches.loadPersisted();
        }
    }

    /** After the run loop, before {@code stopServer} saves the levels: the live blobs and the registry are marked dirty. */
    public static void onStopping(ServerStoppingEvent event) {
        StrategosServer s = current;
        if (s != null) {
            s.matches.prepareSave();
        }
    }

    public static void onStopped(ServerStoppedEvent event) {
        StrategosServer s = current;
        if (s != null) {
            s.matches.shutdown();
        }
        current = null;
    }
}
