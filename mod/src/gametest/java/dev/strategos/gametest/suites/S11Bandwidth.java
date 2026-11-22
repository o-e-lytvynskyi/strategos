package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.shared.FactionSlot;
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
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.TickPacer;
import dev.strategos.gametest.net.ByteCounter;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

final class S11Bandwidth {
    private static final int WARMUP = 200;
    private static final int TICKS = 1_200;
    private static final int TICKS_PER_SECOND = 20;
    private static final int LIMIT_BYTES_PER_SECOND = 100 * 1024;
    private static final int UNITS = 600;
    private static final int VIEWERS = 4;
    private static final int VIEW_DISTANCE = 12;
    private static final int PAN_PER_TICK = 2;
    private static final List<String> TERRAIN = List.of(
            "minecraft:level_chunk_with_light",
            "minecraft:light_update",
            "minecraft:forget_level_chunk",
            "minecraft:chunk_batch_start",
            "minecraft:chunk_batch_finished",
            "minecraft:set_chunk_cache_center",
            "minecraft:set_chunk_cache_radius");

    enum Scene {
        BATTLE("s11_battle", 41),
        PAN("s11_pan", 42),
        FOG("s11_fog", 43),
        CONSTRUCTION("s11_construction", 44);

        final String id;
        final int lane;

        Scene(String id, int lane) {
            this.id = id;
            this.lane = lane;
        }
    }

    private final GameTestHelper helper;
    private final MatchRuntime match;
    private final Scene scene;
    private final SoakScene workload;
    private final List<MockViewer> viewers = new ArrayList<>();
    private final long[][] total = new long[VIEWERS][TICKS];
    private final long[][] terrain = new long[VIEWERS][TICKS];
    private final long[] previousTotal = new long[VIEWERS];
    private final long[] previousTerrain = new long[VIEWERS];
    private final int[] departures = new int[VIEWERS];
    private long firstTick = -1;
    private long startedAt;
    private int warmup;
    private int samples;
    private int minUnits = Integer.MAX_VALUE;
    private int maxUnits;
    private int movingSamples;
    private int fogSamples;
    private long hits;
    private long windowHits;
    private long windowPlacements;
    private double panStart;
    private double panX;
    private double panZ;
    private double panY;
    private int panDirection = PAN_PER_TICK;

    private S11Bandwidth(GameTestHelper helper, Scene scene) {
        this.helper = helper;
        this.scene = scene;
        var manifest = MapTests.manifest(helper);
        var corner = helper.absolutePos(BlockPos.ZERO);
        var origin = new BlockPos(corner.getX() + 2_048 * scene.lane, corner.getY(), corner.getZ());
        var space = new MatchSpace(
                helper.getLevel().dimension(),
                origin,
                manifest.sizeX(),
                manifest.sizeZ(),
                origin.getY(),
                origin.getY() + manifest.height() - 1);
        var play = new PlayRules(PlayRules.Victory.NONE, scene != Scene.FOG, scene != Scene.FOG, false);
        match = MatchFixture.create(
                helper,
                space,
                MatchFixture.options(SimHarness.DEFAULT_SEED, helper)
                        .withMap(
                                MapTests.PROVING_GROUND,
                                origin.getY() + manifest.startSlots().getFirst().y())
                        .withPlay(play));
        var teams = DiplomacyState.identity(2)
                .bind(new PlayerSlot(2), new FactionSlot(0))
                .bind(new PlayerSlot(3), new FactionSlot(1))
                .lockTeams();
        match.initializeDiplomacy(new DiplomacySetup(teams.snapshot()));
        SoakScene.Spec spec = scene == Scene.CONSTRUCTION
                ? new EconomyScene.Config(150, 150, 10, 4, 0)
                : new BattleScene.Config(300, 0);
        workload = spec.create(manifest.startSlots());
        panX = origin.getX() + manifest.sizeX() / 2.0;
        panStart = panX;
        panZ = origin.getZ() + 143.5;
        panY = origin.getY() + manifest.startSlots().getFirst().y() + 22;
        match.addListener(out -> {
            for (var event : out.events()) {
                if (event instanceof UnitHurt) hits++;
            }
        });
    }

    static void start(GameTestHelper helper, Scene scene) {
        var run = new S11Bandwidth(helper, scene);
        helper.onEachTick(run::tick);
    }

    private void tick() {
        if (samples == TICKS) return;
        try {
            step();
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    private void step() {
        if (match.lifecycle() == MatchLifecycle.PREPARING) return;
        check(match.lifecycle() == MatchLifecycle.RUNNING, "S11 match did not run: " + match.lifecycle());
        MatchQueries queries = core(match).queries();
        if (viewers.isEmpty()) {
            if (queries.tick() == 0) return;
            setup(queries);
            return;
        }
        poll();
        if (warmup == 0 && queries.unitCount() < UNITS) {
            issue(workload.next(queries));
            return;
        }
        if (warmup < WARMUP) {
            warmup++;
            if (warmup == WARMUP) begin(queries);
        } else {
            sample(queries);
            if (samples == TICKS) {
                finish();
                return;
            }
        }
        issue(workload.next(queries));
        for (var damage : workload.damage(queries)) match.damage(damage);
        if (scene == Scene.PAN) pan();
    }

    private void setup(MatchQueries queries) {
        var server = StrategosServer.get();
        for (int i = 0; i < VIEWERS; i++) {
            var viewer = MockViewer.join(helper.getLevel(), "s11-peer-" + i, panX, panY, panZ, VIEW_DISTANCE);
            viewers.add(viewer);
            viewer.capture().keep(scene == Scene.PAN);
            var slot = new PlayerSlot(i);
            server.matches().join(match, viewer.player().getUUID(), slot);
            if (scene == Scene.PAN) {
                server.commanders().enter(viewer.player(), match);
                // Commander entry teleports to the admission camera. Start the measured route at its own anchor.
                viewer.player().connection.teleport(panX, panY, panZ, 0, 55);
            }
            server.net().joined(match, viewer.player(), slot);
        }
        issue(workload.setup(queries.templates()));
        TickPacer.pace(true);
    }

    private void issue(List<SoakScene.Issue> commands) {
        for (var item : commands) {
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

    private void poll() {
        for (var viewer : viewers) {
            viewer.poll();
            if (scene == Scene.PAN) {
                for (var packet : viewer.capture().of(ClientboundPlayerPositionPacket.class)) {
                    viewer.player()
                            .connection
                            .handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(packet.getId()));
                }
                viewer.capture().clear();
            } else if (core(match).queries().tick() % TICKS_PER_SECOND == 0) {
                viewer.moved();
            }
        }
    }

    private void pan() {
        if (Math.abs(panX + panDirection - panStart) > 48) panDirection = -panDirection;
        panX += panDirection;
        for (int i = 0; i < VIEWERS; i++) {
            var player = viewers.get(i).player();
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(panX, panY, panZ, 0, 55, false));
            viewers.get(i).moved();
            if (firstTick >= 0 && Math.abs(player.getX() - panX) < 0.01) departures[i]++;
        }
    }

    private void begin(MatchQueries queries) {
        firstTick = queries.tick();
        startedAt = System.nanoTime();
        windowHits = hits;
        windowPlacements = workload.placed();
        for (var viewer : viewers) viewer.bytes().clear();
    }

    private void sample(MatchQueries queries) {
        check(queries.tick() == firstTick + samples + 1, "S11 skipped a core tick in the measured window");
        for (int i = 0; i < VIEWERS; i++) {
            var bytes = viewers.get(i).bytes();
            long terrainNow = terrainBytes(bytes);
            total[i][samples] = bytes.bytes() - previousTotal[i];
            terrain[i][samples] = terrainNow - previousTerrain[i];
            previousTotal[i] = bytes.bytes();
            previousTerrain[i] = terrainNow;
        }
        minUnits = Math.min(minUnits, queries.unitCount());
        maxUnits = Math.max(maxUnits, queries.unitCount());
        if (samples % TICKS_PER_SECOND == 0) {
            boolean moving = false;
            boolean visible = false;
            boolean hidden = false;
            for (int handle : queries.unitHandles()) {
                var unit = queries.unit(handle);
                if (unit == null) continue;
                moving |= unit.state() == UnitState.WALKING;
                if (scene == Scene.FOG && unit.faction().value() == 1) {
                    if (queries.visibleUnit(0, handle)) visible = true;
                    else hidden = true;
                }
            }
            if (moving) movingSamples++;
            if (visible && hidden) fogSamples++;
        }
        samples++;
    }

    private void finish() {
        var report = new JsonObject();
        report.addProperty("scene", scene.id);
        report.addProperty("warmupTicks", WARMUP);
        report.addProperty("firstTick", firstTick + 1);
        report.addProperty("lastTick", firstTick + samples);
        report.addProperty("measuredTicks", samples);
        report.addProperty("ticksPerSecond", TICKS_PER_SECOND);
        report.addProperty("wallSeconds", (System.nanoTime() - startedAt) / 1e9);
        report.addProperty("minUnits", minUnits);
        report.addProperty("maxUnits", maxUnits);
        report.addProperty("movingSamples", movingSamples);
        report.addProperty("fogEdgeSamples", fogSamples);
        report.addProperty("hits", hits - windowHits);
        report.addProperty("placements", workload.placed() - windowPlacements);
        report.addProperty("limitBytesPerSecond", LIMIT_BYTES_PER_SECOND);
        var results = new JsonArray();
        for (int i = 0; i < VIEWERS; i++) results.add(viewerReport(i));
        report.add("viewers", results);
        PerfVerdicts.write(scene.id, report);
        cleanup();

        check(maxUnits == UNITS && movingSamples > 0, "S11 missed its 600-unit moving workload");
        check(
                scene == Scene.CONSTRUCTION ? workload.placed() > windowPlacements : hits > windowHits,
                "S11 did not measure the required construction or fighting");
        check(scene != Scene.FOG || fogSamples > 0, "S11 had no visible/hidden fog edge");
        for (int i = 0; i < VIEWERS; i++) {
            var result = results.get(i).getAsJsonObject();
            check(result.get("failures").getAsInt() == 0, "unencodable packets for S11 viewer " + i);
            check(
                    result.get("gameplayBytes").getAsLong()
                            <= (long) LIMIT_BYTES_PER_SECOND * (TICKS / TICKS_PER_SECOND),
                    "S11 budget E exceeded by viewer " + i + " in " + scene.id);
            if (scene == Scene.PAN) {
                check(departures[i] == TICKS, "S11 pan did not travel 2 blocks per tick for viewer " + i);
                check(result.get("terrainBytes").getAsLong() > 0, "S11 pan did not stream terrain for viewer " + i);
            }
        }
        helper.succeed();
    }

    private JsonObject viewerReport(int index) {
        var json = viewers.get(index).bytes().toJson();
        var totalTicks = new JsonArray();
        var terrainTicks = new JsonArray();
        var gameplayTicks = new JsonArray();
        long[] gameplay = new long[TICKS];
        for (int tick = 0; tick < TICKS; tick++) {
            gameplay[tick] = total[index][tick] - terrain[index][tick];
            totalTicks.add(total[index][tick]);
            terrainTicks.add(terrain[index][tick]);
            gameplayTicks.add(gameplay[tick]);
        }
        long bytes = previousTotal[index] - previousTerrain[index];
        json.addProperty("viewer", index);
        json.addProperty("terrainBytes", previousTerrain[index]);
        json.addProperty("gameplayBytes", bytes);
        json.addProperty(
                "vanillaGameplayBytes", bytes - viewers.get(index).bytes().ours());
        json.addProperty("gameplayMeanBytesPerSecond", bytes * (double) TICKS_PER_SECOND / TICKS);
        json.addProperty(
                "gameplayPeakBytesPerTick",
                java.util.Arrays.stream(gameplay).max().orElseThrow());
        json.addProperty("gameplayMaxOneSecondBytes", peakSecond(gameplay));
        json.addProperty("acceptedPanTicks", departures[index]);
        json.add("totalPerTick", totalTicks);
        json.add("terrainPerTick", terrainTicks);
        json.add("gameplayPerTick", gameplayTicks);
        return json;
    }

    private static long peakSecond(long[] bytes) {
        long peak = 0;
        long sum = 0;
        for (int i = 0; i < bytes.length; i++) {
            sum += bytes[i];
            if (i >= TICKS_PER_SECOND) sum -= bytes[i - TICKS_PER_SECOND];
            if (i + 1 >= TICKS_PER_SECOND) peak = Math.max(peak, sum);
        }
        return peak;
    }

    private static long terrainBytes(ByteCounter bytes) {
        long total = 0;
        for (String key : TERRAIN) total += bytes.bytes(key);
        return total;
    }

    private void cleanup() {
        TickPacer.pace(false);
        viewers.forEach(MockViewer::close);
    }
}
