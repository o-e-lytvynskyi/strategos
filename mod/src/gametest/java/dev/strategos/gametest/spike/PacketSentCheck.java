package dev.strategos.gametest.spike;

import com.google.gson.GsonBuilder;
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
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA-19.4, once with the owner's consent: the bytes QA-19's encoder counts for a mock viewer against JFR's
 * {@code minecraft.PacketSent} total of a real client's connection. Both are spectators at the same spot above a 256²
 * match of {@value #UNITS} debug walkers, half of them walking, both asking for view distance 10. Once the client has
 * stood {@value #SETTLE} ticks, a JFR recording of that one event runs for {@value #WINDOW} ticks while the mock's
 * counter starts from zero; then the client is disconnected. Both count a packet's encoded body, before compression and
 * framing ({@code PacketEncoder} for the real one). Exit: the two totals within 5 % of each other.
 */
final class PacketSentCheck implements Spike {
    private static final Logger LOG = LoggerFactory.getLogger(PacketSentCheck.class);
    private static final int SIZE = 256;
    private static final int UNITS = 300;
    private static final int SETTLE = 400;
    private static final int WINDOW = 1_200;
    private static final double EXIT = 0.05;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final int floor;
    private final SplittableRandom random = new SplittableRandom(19);
    private @Nullable MatchRuntime match;
    private @Nullable MockViewer mock;
    private @Nullable ServerPlayer client;
    private @Nullable Recording recording;
    private final JsonObject result = new JsonObject();
    private long tick;
    private long joinedAt = -1;
    private boolean done;

    PacketSentCheck(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        this.floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
    }

    @Override
    public void preTick() {}

    @Override
    public boolean postTick() {
        tick++;
        if (tick == 1) {
            setUp();
        }
        if (tick % 200 == 0) {
            walk();
        }
        MockViewer viewer = mock;
        if (viewer != null) {
            viewer.poll();
            viewer.capture().clear();
            if (tick % 20 == 0) {
                viewer.moved(); // as a standing client's position packet, once a second
            }
        }
        watchClient();
        return done;
    }

    private void setUp() {
        MatchOptions options = new MatchOptions(19L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor);
        MatchRuntime m = StrategosServer.get()
                .matches()
                .create(level, StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), SIZE, SIZE), options);
        m.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(108, 128), UNITS / 2, 12));
        m.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(148, 128), UNITS / 2, 12));
        match = m;
        MockViewer viewer = MockViewer.join(level, "strategos-qa19", 0.5, floor + 24, 0.5, 10);
        viewer.player().setGameMode(GameType.SPECTATOR);
        mock = viewer;
        LOG.info("QA-19.4: {} walkers at floor {}; waiting for the client", UNITS, floor);
    }

    /** Half the walkers, in groups of 50, to random points within 60 blocks of the centre. */
    private void walk() {
        MatchRuntime m = match;
        MatchCore core = m == null ? null : m.core();
        if (m == null || core == null) {
            return;
        }
        int[] handles = core.queries().unitHandles();
        for (int from = 0; from + 50 <= handles.length / 2; from += 50) {
            Vec2d to = new Vec2d(68 + random.nextInt(120) + 0.5, 68 + random.nextInt(120) + 0.5);
            m.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(Arrays.copyOfRange(handles, from, from + 50)), to));
        }
    }

    private void watchClient() {
        ServerPlayer real = client;
        MockViewer viewer = mock;
        if (viewer == null) {
            return;
        }
        if (real == null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(viewer.player().getUUID())) {
                    real = p;
                }
            }
            if (real == null) {
                return;
            }
            client = real;
            joinedAt = tick;
            real.setGameMode(GameType.SPECTATOR);
            real.connection.teleport(0.5, floor + 24, 0.5, 0, 0);
            LOG.info("QA-19.4: {} joined at {}", real.getGameProfile().getName(), real.connection.getRemoteAddress());
            return;
        }
        if (real.hasDisconnected()) {
            done = true;
            return;
        }
        long since = tick - joinedAt;
        if (since == SETTLE) {
            viewer.bytes().clear();
            Recording r = new Recording();
            r.enable("minecraft.PacketSent");
            r.start();
            recording = r;
            result.addProperty("clientViewDistance", real.requestedViewDistance());
            result.addProperty("mockViewDistance", viewer.player().requestedViewDistance());
        } else if (since == SETTLE + WINDOW) {
            measure(real, viewer);
            real.connection.disconnect(Component.literal("QA-19.4 done"));
        }
    }

    private void measure(ServerPlayer real, MockViewer viewer) {
        Recording r = recording;
        if (r == null) {
            return;
        }
        r.stop();
        long mockBytes = viewer.bytes().bytes();
        JsonObject mockTypes = viewer.bytes().toJson();
        Path file = SpikeReport.dirFor("qa19").resolve("packets.jfr");
        String address = real.connection.getRemoteAddress().toString();
        Map<String, long[]> jfrTypes = new TreeMap<>();
        long jfrBytes = 0;
        long jfrPackets = 0;
        try {
            Files.createDirectories(file.getParent());
            r.dump(file);
            r.close();
            for (RecordedEvent e : RecordingFile.readAllEvents(file)) {
                if (!e.getEventType().getName().equals("minecraft.PacketSent")
                        || !address.equals(e.getString("remoteAddress"))
                        || !"play".equals(e.getString("protocolId"))
                        || !"clientbound".equals(e.getString("packetDirection"))) {
                    continue;
                }
                long[] t = jfrTypes.computeIfAbsent(e.getString("packetId"), k -> new long[2]);
                t[0]++;
                t[1] += e.getInt("bytes");
                jfrPackets++;
                jfrBytes += e.getInt("bytes");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the recording " + file, e);
        }
        JsonObject jfr = new JsonObject();
        jfr.addProperty("packets", jfrPackets);
        jfr.addProperty("bytes", jfrBytes);
        JsonObject types = new JsonObject();
        jfrTypes.forEach((k, v) -> {
            JsonObject t = new JsonObject();
            t.addProperty("packets", v[0]);
            t.addProperty("bytes", v[1]);
            types.add(k, t);
        });
        jfr.add("types", types);
        double difference = jfrBytes == 0 ? 1 : Math.abs(jfrBytes - mockBytes) / (double) jfrBytes;
        result.addProperty("units", UNITS);
        result.addProperty("windowTicks", WINDOW);
        result.addProperty("jfrBytes", jfrBytes);
        result.addProperty("mockBytes", mockBytes);
        result.addProperty("difference", difference);
        result.addProperty("pass", jfrBytes > 0 && difference <= EXIT);
        result.add("jfr", jfr);
        result.add("mock", mockTypes);
        LOG.info(String.format(
                Locale.ROOT,
                "QA-19.4: JFR %d bytes, mock %d bytes, difference %.2f %%",
                jfrBytes,
                mockBytes,
                difference * 100));
    }

    @Override
    public void finish(boolean complete) {
        SpikeReport report = new SpikeReport(SpikeReport.dirFor("qa19"));
        report.writeText(
                "results.json", new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        report.writeEnv();
        MockViewer viewer = mock;
        if (viewer != null) {
            viewer.close();
        }
        if (complete && result.has("pass")) {
            report.done();
        }
    }
}
