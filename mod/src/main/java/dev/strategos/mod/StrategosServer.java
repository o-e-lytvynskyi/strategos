package dev.strategos.mod;

import dev.strategos.mod.registry.ModDataMaps;
import dev.strategos.mod.server.body.SpawnBudget;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.tick.FaultBarrier;
import dev.strategos.mod.server.world.ArenaWorld;
import dev.strategos.mod.server.world.BlockClassTable;
import dev.strategos.mod.server.world.BlockWriter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
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
    private final BlockWriter writer;
    private volatile BlockClassTable blockClasses;
    private ArenaWorld.Outcome arena = ArenaWorld.Outcome.NOT_AN_ARENA;

    private StrategosServer(MinecraftServer server, StrategosSettings settings) {
        this.server = server;
        this.settings = settings;
        this.faults = new FaultBarrier(server);
        this.writer = new BlockWriter(settings.writerBudget(), settings.importBudget());
        this.matches = new MatchRegistry(server, settings, spawnBudget, writer);
        this.blockClasses = buildBlockClasses();
    }

    /** Every block state's nav class, with the {@code strategos:nav_class} overrides of the loaded data packs. */
    private static BlockClassTable buildBlockClasses() {
        return BlockClassTable.build(block -> block.builtInRegistryHolder().getData(ModDataMaps.NAV_CLASS));
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

    /** Every block Strategos writes goes through it (rule B7). */
    public BlockWriter writer() {
        return writer;
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

    /** What the arena check did on this start. */
    public ArenaWorld.Outcome arena() {
        return arena;
    }

    /** Immutable: page jobs read it from any thread; a data map reload replaces it. */
    public BlockClassTable blockClasses() {
        return blockClasses;
    }

    /** Tags reloaded on the server: other mods' doors, fences, leaves and climbables may have changed. */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        StrategosServer s = current;
        if (s != null && event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            s.blockClasses = buildBlockClasses();
        }
    }

    /** The block registry's data maps reloaded on the server: the overrides may have changed. */
    public static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        StrategosServer s = current;
        if (s != null
                && event.getRegistryKey().equals(Registries.BLOCK)
                && event.getCause() == DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD) {
            s.blockClasses = buildBlockClasses();
        }
    }

    public static void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        current = new StrategosServer(server, StrategosSettings.fromSystemProperties(server.getServerDirectory()));
    }

    /** The levels exist now: the saved matches come back, PAUSED, and a new arena world gets its rules. */
    public static void onStarted(ServerStartedEvent event) {
        StrategosServer s = current;
        if (s != null) {
            s.matches.loadPersisted();
            s.arena = ArenaWorld.onStarted(s.server, s.matches.persisted(), s.writer);
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
