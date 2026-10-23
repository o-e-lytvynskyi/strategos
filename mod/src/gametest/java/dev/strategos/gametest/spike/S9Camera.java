package dev.strategos.gametest.spike;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.core.testkit.Percentiles;
import dev.strategos.gametest.LogCapture;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spike S9 (M2 spec §5, ADR-0017), the server: a 384² match round the world's centre with {@value #UNITS} debug walkers,
 * half of them walking between random points, and the 20 gold blocks the client picks; a mock commander, a spectator
 * QA-19 viewer driven through {@code handleMovePlayer} at 8 blocks a tick, as a second client would pan (M2 §1.6 row
 * 3); and the real client's commander, a spectator put at the picking pose. Every tick it times
 * {@code ServerChunkCache#move} for each commander, the call each accepted movement packet makes. From
 * {@value #FIRST_JUMP} ticks after the client joined it jumps the client 300 blocks every {@value #JUMP_EVERY} ticks,
 * {@value #JUMPS} times, measuring until every chunk of its view is sent and every body in its tracking range paired;
 * it counts the server's "moved too quickly" and "moved wrongly" warnings. Then it disconnects the client and reports.
 */
final class S9Camera implements Spike {
    private static final Logger LOG = LoggerFactory.getLogger(S9Camera.class);
    private static final int SIZE = 384;
    private static final int UNITS = 1_000;
    private static final int FIRST_JUMP = 1_600;
    private static final int JUMP_EVERY = 100;
    private static final int JUMPS = 20;
    private static final int[] GOLD_X = {-8, -4, 0, 4, 8};
    private static final int[] GOLD_Z = {10, 14, 18, 22};
    private static final double MOCK_SPEED = 8;
    private static final double EDGE = 150;
    private static final long EXIT_MOVE_NANOS = 500_000;
    private static final long EXIT_COVERAGE_MILLIS = 3_000;

    private record Jump(int index, long tick, long nanos, double distance, int warningsBefore) {}

    private final MinecraftServer server;
    private final ServerLevel level;
    private final int floor;
    private final SplittableRandom random = new SplittableRandom(9);
    private final LogCapture warnings = LogCapture.containing("moved too quickly", "moved wrongly");
    private final List<Long> moveReal = new ArrayList<>();
    private final List<Long> moveMock = new ArrayList<>();
    private final JsonArray jumps = new JsonArray();
    private @Nullable MatchRuntime match;
    private @Nullable MockViewer mock;
    private @Nullable ServerPlayer client;
    private @Nullable Jump open;
    private long tick;
    private long joinedAt = -1;
    private int jumped;
    private double mockX;
    private double mockZ;
    private double mockDirection = 1;
    private double mockZDirection = 1;
    private boolean mockAccepted;
    private boolean done;
    private int jumpWarnings;
    private int mostWarningsAJump;
    private double @Nullable [] dueJump;
    private double lastX;
    private double lastZ;
    private int moved;

    S9Camera(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        this.floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
    }

    /**
     * A due jump, at the start of the tick: the connections tick after this and take the new position as this tick's
     * first good one (ServerGamePacketListenerImpl.java:262), so the client's answer, arriving between ticks, is not
     * measured against the old one (S9's first run: two or three "moved too quickly" a jump from Post).
     */
    @Override
    public void preTick() {
        ServerPlayer real = client;
        double[] to = dueJump;
        if (real == null || to == null) {
            return;
        }
        dueJump = null;
        double distance = Math.hypot(to[0] - real.getX(), to[1] - real.getZ());
        real.connection.teleport(to[0], floor + 24, to[1], real.getYRot(), real.getXRot());
        open = new Jump(
                ++jumped, tick, System.nanoTime(), distance, warnings.lines().size());
        lastX = real.getX();
        lastZ = real.getZ();
        moved = 0;
    }

    @Override
    public boolean postTick() {
        tick++;
        if (tick == 1) {
            setUp();
        }
        if (tick % 200 == 0) {
            walk();
        }
        driveMock();
        watchClient();
        return done;
    }

    private void setUp() {
        StrategosServer strategos = StrategosServer.get();
        MatchOptions options = new MatchOptions(9L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor);
        MatchRuntime m = strategos
                .matches()
                .create(level, StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), SIZE, SIZE), options);
        double[][] quarters = {{-96, -96}, {96, -96}, {-96, 96}, {96, 96}};
        for (int q = 0; q < quarters.length; q++) {
            m.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(
                            TemplateSet.DEBUG_WALKER,
                            new FactionSlot(q % 2),
                            new Vec2d(SIZE / 2.0 + quarters[q][0], SIZE / 2.0 + quarters[q][1]),
                            UNITS / quarters.length,
                            16));
        }
        match = m;
        for (int gx : GOLD_X) {
            for (int gz : GOLD_Z) {
                strategos
                        .writer()
                        .enqueue(
                                level,
                                new WriteSite(m.id().value(), 99),
                                WritePriority.CONSTRUCTION,
                                new BlockPos(gx, floor - 1, gz),
                                Blocks.GOLD_BLOCK.defaultBlockState(),
                                0);
            }
        }
        MockViewer viewer = MockViewer.join(level, "strategos-s9mock", -EDGE, floor + 24, -60, 10);
        viewer.capture().keep(true);
        viewer.player().setGameMode(GameType.SPECTATOR);
        mock = viewer;
        mockX = -EDGE;
        mockZ = -60;
        LOG.info("S9: a {}² match with {} walkers at floor {}; waiting for the client", SIZE, UNITS, floor);
    }

    /** Half the walkers, in groups of 50, to random points; the other half stand. */
    private void walk() {
        MatchRuntime m = match;
        MatchCore core = m == null ? null : m.core();
        if (m == null || core == null) {
            return;
        }
        int[] handles = core.queries().unitHandles();
        for (int from = 0; from + 50 <= handles.length / 2; from += 50) {
            Vec2d to = new Vec2d(16 + random.nextInt(SIZE - 32) + 0.5, 16 + random.nextInt(SIZE - 32) + 0.5);
            m.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(Arrays.copyOfRange(handles, from, from + 50)), to));
        }
    }

    /** The mock commander pans back and forth along X at 8 blocks a tick, as a client's movement packets would. */
    private void driveMock() {
        MockViewer viewer = mock;
        if (viewer == null) {
            return;
        }
        viewer.poll();
        ServerPlayer player = viewer.player();
        if (!mockAccepted) {
            for (ClientboundPlayerPositionPacket p : viewer.capture().of(ClientboundPlayerPositionPacket.class)) {
                player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(p.getId()));
                mockAccepted = true;
            }
            viewer.capture().clear();
            return;
        }
        viewer.capture().clear();
        mockX += mockDirection * MOCK_SPEED;
        if (Math.abs(mockX) > EDGE) { // turn: this packet moves 8 blocks along Z only, as a client's would
            mockDirection = -mockDirection;
            mockX += mockDirection * MOCK_SPEED; // undoes this tick's step along X
            if (Math.abs(mockZ + mockZDirection * MOCK_SPEED) > 120) {
                mockZDirection = -mockZDirection;
            }
            mockZ += mockZDirection * MOCK_SPEED;
        }
        player.connection.handleMovePlayer(
                new ServerboundMovePlayerPacket.PosRot(mockX, floor + 24, mockZ, 0, 55, false));
        moveMock.add(timeMove(player));
    }

    private void watchClient() {
        ServerPlayer real = client;
        if (real == null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                MockViewer viewer = mock;
                if (viewer == null || !p.getUUID().equals(viewer.player().getUUID())) {
                    real = p;
                }
            }
            if (real == null) {
                return;
            }
            client = real;
            joinedAt = tick;
            real.setGameMode(GameType.SPECTATOR);
            real.connection.teleport(0.5, floor + 24, 0.5, 0, 55);
            LOG.info("S9: {} joined; jumps from tick {}", real.getGameProfile().getName(), tick + FIRST_JUMP);
            return;
        }
        if (real.hasDisconnected()) {
            done = true;
            return;
        }
        moveReal.add(timeMove(real));
        coverage(real);
        long since = tick - joinedAt;
        if (since >= FIRST_JUMP
                && (since - FIRST_JUMP) % JUMP_EVERY == 0
                && jumped < JUMPS
                && open == null
                && dueJump == null) {
            dueJump = new double[] {real.getX() > 0 ? -EDGE : EDGE, -100 + random.nextInt(201)};
        }
        if (jumped >= JUMPS && open == null && since >= FIRST_JUMP + JUMPS * JUMP_EVERY + 100) {
            real.connection.disconnect(Component.literal("S9 done"));
        }
    }

    /** The open jump closes once every chunk of the view is sent and every body in range is paired. */
    private void coverage(ServerPlayer player) {
        Jump j = open;
        if (j == null) {
            return;
        }
        ChunkPos centre = player.chunkPosition();
        boolean[] chunks = {true};
        int[] views = {0};
        player.getChunkTrackingView().forEach(pos -> {
            views[0]++;
            if (player.connection.chunkSender.isPending(pos.toLong())) {
                chunks[0] = false;
            }
        });
        boolean centred = player.getChunkTrackingView().contains(centre);
        // the player's own view distance, as ChunkMap#getPlayerViewDistance and TrackedEntity#updatePlayer take it
        int view = Math.max(
                2,
                Math.min(player.requestedViewDistance(), server.getPlayerList().getViewDistance()));
        double range = Math.min(160, view * 16);
        MatchRuntime m = match;
        int inRange = 0;
        int sent = 0;
        int paired = 0;
        if (player.getX() != lastX || player.getZ() != lastZ) {
            moved++;
            lastX = player.getX();
            lastZ = player.getZ();
        }
        if (m != null) {
            Set<Integer> pairs = Set.copyOf(StrategosServer.get().interest().handles(player.getUUID(), m.id().value()));
            AABB box = player.getBoundingBox().inflate(range, 64, range);
            for (UnitEntity body : level.getEntitiesOfClass(UnitEntity.class, box)) {
                double dx = body.getX() - player.getX();
                double dz = body.getZ() - player.getZ();
                if (dx * dx + dz * dz <= range * range
                        && player.getChunkTrackingView().contains(body.chunkPosition())
                        && body.spawnData() != null) {
                    inRange++;
                    if (!player.connection.chunkSender.isPending(
                            body.chunkPosition().toLong())) {
                        sent++;
                    }
                    if (pairs.contains(body.spawnData().handle())) {
                        paired++;
                    }
                }
            }
        }
        long ticks = tick - j.tick();
        if ((centred && chunks[0] && paired == inRange && views[0] > 0) || ticks > 400) {
            JsonObject row = new JsonObject();
            row.addProperty("jump", j.index());
            row.addProperty("distance", Math.round(j.distance()));
            row.addProperty("ticks", ticks);
            row.addProperty("millis", (System.nanoTime() - j.nanos()) / 1_000_000);
            row.addProperty("chunks", views[0]);
            row.addProperty("bodiesInRange", inRange);
            row.addProperty("paired", paired);
            row.addProperty("inSentChunks", sent);
            row.addProperty("chunksAllSent", chunks[0]);
            row.addProperty("centred", centred);
            row.addProperty("ticksTheClientMoved", moved);
            row.addProperty("viewDistance", view);
            if (m != null && m.core() != null) {
                row.addProperty("units", m.core().queries().unitCount());
                row.addProperty("bodies", m.bodies().count());
            }
            row.addProperty("covered", ticks <= 400);
            int warned = warnings.lines().size() - j.warningsBefore();
            row.addProperty("movementWarnings", warned);
            jumpWarnings += warned;
            mostWarningsAJump = Math.max(mostWarningsAJump, warned);
            jumps.add(row);
            open = null;
        }
    }

    private long timeMove(ServerPlayer player) {
        long start = System.nanoTime();
        level.getChunkSource().move(player);
        return System.nanoTime() - start;
    }

    @Override
    public void finish(boolean complete) {
        SpikeReport report = new SpikeReport(SpikeReport.dirFor("s9"));
        long[] real = moveReal.stream().mapToLong(Long::longValue).toArray();
        long[] mocked = moveMock.stream().mapToLong(Long::longValue).toArray();
        List<String> lines = warnings.lines();
        warnings.close();
        long worstCoverage = 0;
        boolean allCovered = true;
        for (int k = 0; k < jumps.size(); k++) {
            JsonObject row = jumps.get(k).getAsJsonObject();
            worstCoverage = Math.max(worstCoverage, row.get("millis").getAsLong());
            allCovered &= row.get("covered").getAsBoolean();
        }
        long realP99 = Percentiles.percentile(real, 0.99);
        long mockP99 = Percentiles.percentile(mocked, 0.99);
        JsonObject json = new JsonObject();
        json.addProperty("units", UNITS);
        int entities = 0;
        for (Entity ignored : level.getAllEntities()) {
            entities++;
        }
        json.addProperty("entities", entities);
        json.addProperty("viewDistance", server.getPlayerList().getViewDistance());
        json.addProperty("moveRealP50Ns", Percentiles.percentile(real, 0.50));
        json.addProperty("moveRealP99Ns", realP99);
        json.addProperty("moveRealMaxNs", Arrays.stream(real).max().orElse(0));
        json.addProperty("moveMockP50Ns", Percentiles.percentile(mocked, 0.50));
        json.addProperty("moveMockP99Ns", mockP99);
        json.addProperty("moveMockMaxNs", Arrays.stream(mocked).max().orElse(0));
        json.addProperty("movementWarnings", lines.size());
        json.addProperty("movementWarningsAtJumps", jumpWarnings);
        json.addProperty("mostMovementWarningsAJump", mostWarningsAJump);
        JsonArray warned = new JsonArray();
        lines.stream().limit(20).forEach(warned::add);
        json.add("warnings", warned);
        json.add("jumps", jumps);
        json.addProperty("worstCoverageMillis", worstCoverage);
        boolean pass = lines.size() - jumpWarnings == 0
                && mostWarningsAJump <= 1
                && allCovered
                && worstCoverage <= EXIT_COVERAGE_MILLIS
                && realP99 <= EXIT_MOVE_NANOS
                && mockP99 <= EXIT_MOVE_NANOS
                && jumps.size() == JUMPS;
        json.addProperty("pass", pass);
        report.writeText(
                "results.json", new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
        report.writeText(
                "results.md",
                String.format(
                        Locale.ROOT,
                        "# S9 server side%n%n- ChunkMap#move per commander: client p50 %.3f ms, p99 %.3f ms; mock p50"
                                + " %.3f ms, p99 %.3f ms%n- movement warnings: %d (%d at jumps)%n- jumps: %d, worst"
                                + " coverage %d ms%n- pass: %s%n",
                        Percentiles.percentile(real, 0.50) / 1e6,
                        realP99 / 1e6,
                        Percentiles.percentile(mocked, 0.50) / 1e6,
                        mockP99 / 1e6,
                        lines.size(),
                        jumpWarnings,
                        jumps.size(),
                        worstCoverage,
                        pass));
        report.writeEnv();
        LOG.info("S9: {}", json);
        MockViewer viewer = mock;
        if (viewer != null) {
            viewer.close();
        }
        if (complete) {
            report.done();
        }
    }
}
