package dev.strategos.mod;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.mod.registry.ModDataMaps;
import dev.strategos.mod.server.body.SpawnBudget;
import dev.strategos.mod.server.commander.CommanderService;
import dev.strategos.mod.server.content.ResourceFiles;
import dev.strategos.mod.server.content.ServerTemplates;
import dev.strategos.mod.server.content.TemplateFiles;
import dev.strategos.mod.server.content.TemplateReloadListener;
import dev.strategos.mod.server.content.TemplateSync;
import dev.strategos.mod.server.debug.DebugFeed;
import dev.strategos.mod.server.debug.PerfDump;
import dev.strategos.mod.server.fog.InterestSets;
import dev.strategos.mod.server.jobs.PathThreads;
import dev.strategos.mod.server.match.MapImports;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.net.NetGateway;
import dev.strategos.mod.server.net.Replication;
import dev.strategos.mod.server.tick.BetweenTicks;
import dev.strategos.mod.server.tick.FaultBarrier;
import dev.strategos.mod.server.world.ArenaWorld;
import dev.strategos.mod.server.world.BlockClassTable;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything Strategos keeps for one running server, from {@code ServerAboutToStartEvent} (before the levels exist)
 * to {@code ServerStoppedEvent}: an integrated server gets a fresh one per world. The one static holder (rule B8's
 * first exception); everything else hangs off it.
 */
public final class StrategosServer {
    private static final Logger LOG = LoggerFactory.getLogger(StrategosServer.class);
    private static volatile @Nullable StrategosServer current;

    private final MinecraftServer server;
    private final StrategosSettings settings;
    private final SpawnBudget spawnBudget = new SpawnBudget();
    private final BetweenTicks betweenTicks = new BetweenTicks();
    private final FaultBarrier faults;
    private final MatchRegistry matches;
    private final BlockWriter writer;
    private final MapImports mapImports = new MapImports();
    private final JobExecutor jobs;
    private final ServerTemplates templates = new ServerTemplates();
    private final TemplateSync templateSync = new TemplateSync();
    private final CommanderService commanders = new CommanderService();
    private final NetGateway net;
    private final InterestSets interest = new InterestSets();
    private final DebugFeed debug;
    private final Replication replication;
    private final PerfDump perf;
    private volatile BlockClassTable blockClasses;
    private ArenaWorld.Outcome arena = ArenaWorld.Outcome.NOT_AN_ARENA;

    private StrategosServer(MinecraftServer server, StrategosSettings settings) {
        this.server = server;
        this.settings = settings;
        this.jobs = PathThreads.executor(PathThreads.count(
                settings.pathThreads(), Runtime.getRuntime().availableProcessors(), server.isDedicatedServer()));
        this.faults = new FaultBarrier(server);
        this.writer = new BlockWriter(settings.writerBudget(), settings.importBudget());
        loadTemplatesAtStart();
        this.matches = new MatchRegistry(
                server, settings, spawnBudget, writer, this::blockClasses, jobs, () -> templates.set(), this::economy);
        this.matches.onUnseated(uuid -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                commanders.leave(server, player);
            }
        });
        this.net = new NetGateway(server, () -> this.matches, faults);
        this.debug =
                new DebugFeed(server, () -> this.matches, net, StrategosConfig.INSTANCE.debugPermission.getAsInt());
        this.perf = new PerfDump(server, () -> this.matches);
        this.replication = new Replication(server, () -> this.matches, net);
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

    /** Where page and path jobs run: the {@code strategos-path-N} pool, or inline with {@code pathThreads = 0}. */
    public JobExecutor jobs() {
        return jobs;
    }

    /** The server's template set, its id table and the last load's problems (DATA-03). */
    public ServerTemplates templates() {
        return templates;
    }

    /** The seated players' RTS bodies, jumps and re-pairs (CAM-03). */
    public CommanderService commanders() {
        return commanders;
    }

    public TemplateSync templateSync() {
        return templateSync;
    }

    /**
     * A reload's templates, from {@link TemplateReloadListener}: compiled, linted and, when good, swapped into every
     * running match; the outcome is logged, and told to the operators online.
     */
    public ServerTemplates.Outcome loadTemplates(List<RawTemplate> raws, List<TemplateProblem> problems) {
        return report(templates.load(raws, problems, server.registryAccess(), matches.all()));
    }

    private ServerTemplates.Outcome report(ServerTemplates.Outcome outcome) {
        if (outcome.accepted()) {
            LOG.info("Templates: {}", outcome.message());
        } else {
            LOG.error("Templates: {}", outcome.message());
            outcome.problems().forEach(p -> LOG.error("  {}", p));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("[Strategos] templates " + outcome.message()));
            }
        }
        return outcome;
    }

    /**
     * {@code /strategos templates reload}: the loaded data packs' templates read again, without a full {@code /reload};
     * a good set goes to every player too.
     */
    public ServerTemplates.Outcome reloadTemplates() {
        List<TemplateProblem> problems = new ArrayList<>();
        ServerTemplates.Outcome outcome = loadTemplates(packTemplates(problems), problems);
        if (outcome.accepted()) {
            server.getPlayerList().getPlayers().forEach(player -> templateSync.send(this, player));
        }
        return outcome;
    }

    private ConditionalOps<JsonElement> conditionalOps() {
        return new ConditionalOps<>(
                RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()),
                server.getServerResources().managers().getConditionContext());
    }

    /**
     * The resources of the loaded data packs (ECO-01), read when a match is created: a new match copies them into its
     * rules. A bad file is logged and D-10's four are used.
     */
    public EconomyRules economy() {
        List<String> problems = new ArrayList<>();
        EconomyRules rules = ResourceFiles.read(server.getResourceManager(), problems);
        problems.forEach(p -> LOG.error("Resources: {}", p));
        return rules;
    }

    /** The templates of the data packs the server started with, read here: no server was up when they applied. */
    private void loadTemplatesAtStart() {
        List<TemplateProblem> problems = new ArrayList<>();
        report(templates.load(packTemplates(problems), problems, server.registryAccess(), List.of()));
    }

    /** The templates of the loaded data packs, read now on this thread; the problems of the files go to {@code problems}. */
    public List<RawTemplate> packTemplates(List<TemplateProblem> problems) {
        TemplateFiles.Read read = TemplateFiles.read(server.getResourceManager());
        problems.addAll(read.problems());
        return TemplateFiles.raws(read, conditionalOps(), problems);
    }

    /** Map imports that belong to no match. */
    public MapImports mapImports() {
        return mapImports;
    }

    /** Every block Strategos writes goes through it (rule B7). */
    public BlockWriter writer() {
        return writer;
    }

    public StrategosSettings settings() {
        return settings;
    }

    /** The only sender (B6). */
    public NetGateway net() {
        return net;
    }

    /** The bodies each player is paired with. */
    public InterestSets interest() {
        return interest;
    }

    /** Replication tier 2's sender (NET-08). */
    public Replication replication() {
        return replication;
    }

    /** The movement overlay's feed to subscribed operators (PATH-10). */
    public DebugFeed debug() {
        return debug;
    }

    /** {@code /strategos perf}'s recordings. */
    public PerfDump perf() {
        return perf;
    }

    public FaultBarrier faults() {
        return faults;
    }

    /** Our work outside the server's measured ticks (budget C). */
    public BetweenTicks betweenTicks() {
        return betweenTicks;
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

    /** After every level ticked: the perf rows and the debug feed of this tick, then what the gateway queued goes out. */
    public static void onTickPost(ServerTickEvent.Post event) {
        StrategosServer s = current;
        if (s != null) {
            long start = System.nanoTime();
            s.perf.tick();
            s.debug.tick();
            s.replication.tick();
            s.net.flush();
            s.betweenTicks.tickEnds(System.nanoTime() - start);
        }
    }

    /** A chunk reached a player: a commander re-pairs at the next tick's start (CAM-03). */
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        StrategosServer s = current;
        if (s != null) {
            s.commanders.chunkSent(event.getPlayer());
        }
    }

    /** A player came in: one that sits in a match hears which, as it did when it joined it ({@code match_join}). */
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        StrategosServer s = current;
        if (s != null && event.getEntity() instanceof ServerPlayer player) {
            MatchRegistry.Seat seat = s.matches.seatOf(player.getUUID());
            if (seat != null) {
                s.commanders.enter(player, seat.match());
                s.net.joined(seat.match(), player, seat.slot());
            } else {
                s.commanders.leave(s.server, player); // its match ended while it was away
            }
        }
    }

    /** A player left: its rate limits, queue and debug subscription go; its seat stays, for its return. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        StrategosServer s = current;
        if (s != null && event.getEntity() instanceof ServerPlayer player) {
            s.net.forget(player);
            s.commanders.forget(player);
            s.templateSync.forget(player);
            s.interest.forget(player.getUUID());
            s.debug.forget(player);
            s.replication.forget(player.getUUID());
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
            if (s.jobs instanceof AutoCloseable pool) {
                try {
                    pool.close(); // the matches are saved and gone: no join waits on a worker now
                } catch (Exception e) {
                    throw new IllegalStateException("the path job pool did not stop", e);
                }
            }
        }
        current = null;
    }
}
