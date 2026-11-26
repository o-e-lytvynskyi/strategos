package dev.strategos.devtools;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.BattleScene;
import dev.strategos.core.testkit.EconomyScene;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.SoakScene;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.Comparator;
import java.util.List;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/** Operator-started dedicated-server fixture, available only in a dev run with S11 explicitly enabled. */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class S11Live {
    private static final ResourceLocation MAP = ResourceLocation.fromNamespaceAndPath("strategos", "proving_ground");
    static final int LANE_SPACING = 2_048;
    static final int PAN_LANE = 2;
    private static final int WARMUP = 200;
    private static final int TICKS = 1_200;
    private static final int UNITS = 600;
    private static final int TIMEOUT = 6_000;
    private static final String[] SCENES = {"battle", "pan", "fog", "construction"};
    private static @Nullable S11Live active;

    private final CommandSourceStack source;
    private final MatchRuntime match;
    private final String scene;
    private final SoakScene workload;
    private final List<ServerPlayer> peers;
    private final double x;
    private final double y;
    private final double z;
    private final double[] previousX = new double[2];
    private final double[] distance = new double[2];
    private int warmup;
    private long firstBoundary = -1;
    private long hits;
    private long initialPlacements;
    private int maxUnits;
    private int movingSamples;
    private int fogEdgeSamples;
    private boolean admitted;

    private S11Live(CommandSourceStack source, String scene, int lane, List<ServerPlayer> peers) {
        this.source = source;
        this.peers = peers;
        this.scene = "s11_" + scene;
        var server = source.getServer();
        var level = server.overworld();
        var manifest = MapPackageLoader.load(server, MAP).manifest();
        var origin = new BlockPos(LANE_SPACING * lane, level.getMinBuildHeight() + 8, 2_048);
        var space = new MatchSpace(
                level.dimension(),
                origin,
                manifest.sizeX(),
                manifest.sizeZ(),
                origin.getY(),
                origin.getY() + manifest.height() - 1);
        boolean revealed = !scene.equals("fog");
        var play = new PlayRules(PlayRules.Victory.NONE, revealed, revealed, false);
        match = StrategosServer.get()
                .matches()
                .create(
                        level,
                        space,
                        MatchOptions.gameTest(
                                        SimHarness.DEFAULT_SEED,
                                        origin.getY()
                                                + manifest.startSlots()
                                                        .getFirst()
                                                        .y())
                                .withMap(
                                        MAP,
                                        origin.getY()
                                                + manifest.startSlots()
                                                        .getFirst()
                                                        .y())
                                .withPlay(play));
        match.initializeDiplomacy(
                new DiplomacySetup(DiplomacyState.identity(2).lockTeams().snapshot()));
        SoakScene.Spec spec = scene.equals("construction")
                ? new EconomyScene.Config(150, 150, 10, 4, 0)
                : new BattleScene.Config(300, 0);
        workload = spec.create(manifest.startSlots());
        x = origin.getX() + manifest.sizeX() / 2.0;
        y = origin.getY() + manifest.startSlots().getFirst().y() + 22;
        z = origin.getZ() + 143.5;
        match.addListener(out -> {
            long damage =
                    out.events().stream().filter(UnitHurt.class::isInstance).count();
            step(out.tick(), damage);
        });
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("strategos.s11.live")) return;
        var command = Commands.literal("s11-live").requires(source -> source.hasPermission(4));
        for (int index = 0; index < SCENES.length; index++) {
            String scene = SCENES[index];
            int lane = index + 1;
            command.then(Commands.literal(scene).executes(ctx -> start(ctx.getSource(), scene, lane)));
        }
        command.then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())));
        event.getDispatcher().register(command);
    }

    private static int start(CommandSourceStack source, String scene, int lane) {
        if (active != null) {
            source.sendFailure(Component.literal("Clear the previous S11 scene first"));
            return 0;
        }
        if (!source.getServer().isDedicatedServer() || !new Boundary().isEnabled()) {
            source.sendFailure(Component.literal("S11 requires a dedicated server with JFR already recording"));
            return 0;
        }
        var peers = source.getServer().getPlayerList().getPlayers().stream()
                .sorted(Comparator.comparing(p -> p.getGameProfile().getName()))
                .toList();
        if (peers.size() != 2
                || peers.stream()
                        .anyMatch(p -> StrategosServer.get().matches().seatOf(p.getUUID()) != null
                                || !p.serverLevel()
                                        .dimension()
                                        .equals(source.getServer().overworld().dimension()))) {
            source.sendFailure(Component.literal("S11 needs two unseated clients in the overworld"));
            return 0;
        }
        active = new S11Live(source, scene, lane, peers);
        source.sendSuccess(() -> Component.literal("Importing S11 " + scene), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        var run = active;
        if (run == null) return 0;
        var strategos = StrategosServer.get();
        strategos.matches().dispose(run.match.id());
        active = null;
        source.sendSuccess(() -> Component.literal("S11 scene cleared"), false);
        return 1;
    }

    private void step(long tick, long damage) {
        if (match.lifecycle() != MatchLifecycle.RUNNING) return;
        var core = match.core();
        if (core == null) return;
        var queries = core.queries();
        if (tick > TIMEOUT || peers.stream().anyMatch(ServerPlayer::hasDisconnected)) {
            finish("S11 scene invalid: timeout or client disconnected");
            return;
        }
        if (!admitted) {
            var strategos = StrategosServer.get();
            for (int i = 0; i < peers.size(); i++) {
                ServerPlayer peer = peers.get(i);
                var slot = new PlayerSlot(i);
                strategos.matches().join(match, peer.getUUID(), slot);
                strategos.commanders().enter(peer, match);
                peer.connection.teleport(x, y, z, 0, 55);
                strategos.net().joined(match, peer, slot);
                previousX[i] = x;
            }
            issue(workload.setup(queries.templates()));
            admitted = true;
            return;
        }
        if (warmup == 0 && queries.unitCount() < UNITS) {
            issue(workload.next(queries));
            return;
        }
        if (warmup < WARMUP) {
            warmup++;
        } else {
            if (firstBoundary < 0) {
                mark(tick);
                firstBoundary = tick;
                initialPlacements = workload.placed();
                source.sendSuccess(() -> Component.literal(scene + " measurement started at tick " + tick), false);
            } else {
                hits += damage;
                for (int i = 0; i < peers.size(); i++) {
                    double nextX = peers.get(i).getX();
                    distance[i] += Math.abs(nextX - previousX[i]);
                }
                maxUnits = Math.max(maxUnits, queries.unitCount());
                if ((tick - firstBoundary - 1) % 20 == 0) sampleActivity(queries);
                mark(tick);
                if (tick - firstBoundary == TICKS) {
                    finish(scene + " ticks " + firstBoundary + ".." + tick + ", hits " + hits
                            + ", placements " + (workload.placed() - initialPlacements)
                            + ", pan distances " + Math.round(distance[0]) + " and " + Math.round(distance[1]));
                    return;
                }
            }
        }
        for (int i = 0; i < peers.size(); i++) {
            previousX[i] = peers.get(i).getX();
        }
        issue(workload.next(queries));
        workload.damage(queries).forEach(match::damage);
    }

    private void sampleActivity(MatchQueries queries) {
        boolean moving = false;
        boolean visible = false;
        boolean hidden = false;
        for (int handle : queries.unitHandles()) {
            var unit = queries.unit(handle);
            if (unit == null) continue;
            moving |= unit.state() == UnitState.WALKING;
            if (scene.equals("s11_fog") && unit.faction().value() == 1) {
                if (queries.visibleUnit(0, handle)) visible = true;
                else hidden = true;
            }
        }
        if (moving) movingSamples++;
        if (visible && hidden) fogEdgeSamples++;
    }

    private void issue(List<SoakScene.Issue> commands) {
        for (SoakScene.Issue item : commands) {
            if (item.command() instanceof PlaceBuilding) {
                match.submit(
                        item.issuer(),
                        item.command(),
                        result -> workload.placementResult(result.outcome() == CommandOutcome.ACCEPTED));
            } else {
                match.submit(item.issuer(), item.command());
            }
        }
    }

    private void finish(String report) {
        StrategosServer.get().matches().end(match.id());
        source.sendSuccess(() -> Component.literal(report), false);
    }

    private void mark(long tick) {
        Boundary event = new Boundary();
        event.scene = scene;
        event.coreTick = tick;
        if (firstBoundary >= 0 && tick - firstBoundary == TICKS) {
            event.hits = hits;
            event.placements = workload.placed() - initialPlacements;
            event.maxUnits = maxUnits;
            event.movingSamples = movingSamples;
            event.fogEdgeSamples = fogEdgeSamples;
            event.panA = distance[0];
            event.panB = distance[1];
        }
        event.commit();
    }

    @Name("strategos.S11Tick")
    @StackTrace(false)
    static final class Boundary extends Event {
        public String scene = "";
        public long coreTick;
        public long hits;
        public long placements;
        public int maxUnits;
        public int movingSamples;
        public int fogEdgeSamples;
        public double panA;
        public double panB;
    }
}
