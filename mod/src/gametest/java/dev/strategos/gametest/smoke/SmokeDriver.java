package dev.strategos.gametest.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The L5 server smoke and its restart round trip (spec §7.5). Acts only when {@code strategos.smoke} is {@code true}
 * (run 1) or {@code restart} (run 2, in run 1's game directory).
 *
 * <p>Run 1:
 *
 * <ol>
 *   <li>the mod is loaded, core runs in the mod module, the holder exists from {@code ServerAboutToStartEvent} on, the
 *       unit body type is registered;
 *   <li>a saved 16 x 16 match around the world spawn; 8 units around local (4, 8), ordered to (12.5, 8.5);
 *   <li>100 server ticks later: every unit on the target, 8 bodies, the match RUNNING; then ordered back to (4.5, 8.5);
 *   <li>20 ticks later, the units still walking: the marker with the numbers, the live journal in
 *       {@code journal-run1.json}, and {@code halt}; the stop saves the match. A 600-tick deadline fails the run.
 * </ol>
 *
 * <p>Run 2: the match is back PAUSED with run 1's tick and hash; resumed, the level holds exactly one body per unit;
 * 60 ticks later the live hash equals one uninterrupted replay of run 1's journal and run 2's. Both runs add
 * {@code holderClearedOnStop} to their marker when the server has stopped.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class SmokeDriver {
    private static final Logger LOG = LoggerFactory.getLogger(SmokeDriver.class);
    private static final int UNITS = 8;
    private static final Vec2d START = new Vec2d(4, 8);
    private static final Vec2d TARGET = new Vec2d(12.5, 8.5);
    private static final Vec2d BACK = new Vec2d(4.5, 8.5);
    private static final int CHECK_AT = 100;
    private static final int SAVE_AFTER = 20;
    private static final int REPLAY_AFTER = 60;
    private static final int DEADLINE = 600;

    /** The running smoke; gametest code, so static state is fine. Server thread only. */
    private static @Nullable Run run;

    private static boolean holderAtAboutToStart;
    private static boolean markerWritten;

    private SmokeDriver() {}

    private static final class Run {
        final MinecraftServer server;
        final MatchRuntime match;
        final boolean restart;
        int ticks;
        boolean ordered;
        boolean orderedBack;
        long restoredTick = -1;
        int bodiesAt = -1;

        @Nullable JournalExport run1;

        Run(MinecraftServer server, MatchRuntime match, boolean restart) {
            this.server = server;
            this.match = match;
            this.restart = restart;
        }
    }

    private static String mode() {
        return System.getProperty("strategos.smoke", "");
    }

    private static boolean active() {
        return mode().equals("true") || mode().equals("restart");
    }

    /** Lowest priority, so the holder's own listener has run (CORE-01.6). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onAboutToStart(ServerAboutToStartEvent event) {
        holderAtAboutToStart = StrategosServer.getOrNull() != null;
    }

    /** Lowest priority, so it runs after the holder's own listeners, saved matches loaded. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStarted(ServerStartedEvent event) {
        if (!active()) {
            return;
        }
        MinecraftServer server = event.getServer();
        String problem = startupProblem();
        if (problem != null) {
            finish(server, problem, null);
            return;
        }
        if (mode().equals("restart")) {
            startRestart(server);
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int floor = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        BlockPos feet = new BlockPos(spawn.getX(), floor, spawn.getZ());
        MatchRuntime match = StrategosServer.get()
                .matches()
                .create(
                        overworld,
                        StrategosCommands.spaceAround(overworld, feet, 16, 16),
                        MatchOptions.generated(floor));
        match.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), START, UNITS, 2));
        run = new Run(server, match, false);
    }

    /** Run 2: the saved match is back PAUSED where run 1 stopped, and walks on. */
    private static void startRestart(MinecraftServer server) {
        JsonObject run1;
        JournalExport journal;
        try {
            run1 = readMarker(sibling("smoke-result.json"));
            journal = JournalFiles.read(sibling("journal-run1.json"));
        } catch (RuntimeException e) {
            finish(server, "run 1 left no marker or journal: " + e.getMessage(), null);
            return;
        }
        List<MatchRuntime> matches = StrategosServer.get().matches().all();
        if (matches.size() != 1) {
            finish(server, matches.size() + " matches after the restart, not 1", null);
            return;
        }
        MatchRuntime match = matches.get(0);
        MatchCore core = match.core();
        if (match.lifecycle() != MatchLifecycle.PAUSED || core == null) {
            finish(server, "the saved match is back " + match.lifecycle() + ": " + match.faultMessage(), null);
            return;
        }
        long tick = run1.get("tick").getAsLong();
        String hash = run1.get("hash").getAsString();
        if (core.queries().tick() != tick || !Hex64.format(core.stateHash()).equals(hash)) {
            finish(
                    server,
                    "the saved match is back at tick " + core.queries().tick() + " with hash "
                            + Hex64.format(core.stateHash()) + ", run 1 stopped at tick " + tick + " with " + hash,
                    null);
            return;
        }
        Run r = new Run(server, match, true);
        r.restoredTick = tick;
        r.run1 = journal;
        StrategosServer.get().matches().resume(match.id());
        run = r;
    }

    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        Run r = run;
        if (r == null) {
            return;
        }
        r.ticks++;
        MatchCore core = r.match.core();
        if (core == null) {
            finish(r.server, "the smoke match lost its core: " + r.match.faultMessage(), r);
            return;
        }
        if (r.restart) {
            tickRestart(r, core);
        } else {
            tickRun1(r, core);
        }
        if (run != null && r.ticks >= DEADLINE) {
            finish(r.server, "no verdict within " + DEADLINE + " ticks", r);
        }
    }

    private static void tickRun1(Run r, MatchCore core) {
        int[] units = core.queries().unitHandles();
        if (!r.ordered && units.length == UNITS) {
            r.match.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), TARGET));
            r.ordered = true;
        }
        if (r.ticks == CHECK_AT) {
            String problem = matchProblem(r, core, units);
            if (problem != null) {
                finish(r.server, problem, r);
                return;
            }
            r.match.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), BACK));
            r.orderedBack = true;
        } else if (r.orderedBack && r.ticks == CHECK_AT + SAVE_AFTER) {
            boolean walking = Arrays.stream(units).anyMatch(u -> {
                UnitView unit = core.queries().unit(u);
                return unit != null && unit.state() == UnitState.WALKING;
            });
            if (!walking) {
                finish(r.server, "no unit walks when the match is saved", r);
                return;
            }
            JournalFiles.write(
                    sibling("journal-run1.json"),
                    new JournalExport(core.journal(), core.queries().tick(), core.stateHash()));
            finish(r.server, null, r);
        }
    }

    private static void tickRestart(Run r, MatchCore core) {
        int[] units = core.queries().unitHandles();
        if (r.bodiesAt < 0) {
            if (r.match.bodies().count() < units.length) {
                return; // the bodies come back as their chunks tick entities
            }
            String problem = bodiesProblem(r, units);
            if (problem != null) {
                finish(r.server, problem, r);
                return;
            }
            r.bodiesAt = r.ticks;
        } else if (r.ticks == r.bodiesAt + REPLAY_AFTER) {
            JournalExport run1 = r.run1;
            if (run1 == null) {
                throw new IllegalStateException("run 2 without run 1's journal");
            }
            ReplayRunner.ReplayReport replay = ReplayRunner.chain(
                    TemplateSet.builtin(), core.stateHash(), List.of(run1.journal(), core.journal()));
            LOG.info("Server smoke restart: {}", replay.message());
            finish(r.server, replay.ok() ? null : replay.message(), r);
        }
    }

    private static @Nullable String startupProblem() {
        if (!ModList.get().isLoaded(Strategos.MOD_ID)) {
            return "strategos is not loaded";
        }
        if (Handle.class.getModule() != Strategos.class.getModule()) {
            return "core runs in module " + Handle.class.getModule().getName() + ", the mod in "
                    + Strategos.class.getModule().getName();
        }
        if (!holderAtAboutToStart) {
            return "no Strategos server holder at ServerAboutToStartEvent";
        }
        if (StrategosServer.getOrNull() == null) {
            return "no Strategos server holder after ServerStartedEvent";
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(Ids.id("unit_humanoid"))) {
            return "strategos:unit_humanoid is not registered";
        }
        return null;
    }

    private static @Nullable String matchProblem(Run r, MatchCore core, int[] units) {
        if (r.match.lifecycle() != MatchLifecycle.RUNNING) {
            return "the match is " + r.match.lifecycle() + ": " + r.match.faultMessage();
        }
        if (!r.match.persistent()) {
            return "the smoke match is not saved";
        }
        if (units.length != UNITS) {
            return units.length + " units, not " + UNITS;
        }
        for (int handle : units) {
            UnitView unit = core.queries().unit(handle);
            if (unit == null || unit.x() != TARGET.x() || unit.z() != TARGET.z()) {
                return "unit " + Handle.toString(handle) + " is not on the target: " + unit;
            }
        }
        if (r.match.bodies().count() != UNITS) {
            return r.match.bodies().count() + " bodies, not " + UNITS + " (" + Arrays.toString(units) + ")";
        }
        return null;
    }

    /** QA-05.4, CORE-06.3: after the restart the level holds one body per unit and no other unit body. */
    private static @Nullable String bodiesProblem(Run r, int[] units) {
        List<? extends UnitEntity> bodies =
                r.server.overworld().getEntities(EntityTypeTest.forClass(UnitEntity.class), e -> true);
        int[] shown = bodies.stream()
                .map(UnitEntity::spawnData)
                .mapToInt(d -> d == null || d.matchId() != r.match.id().value() ? -1 : d.handle())
                .sorted()
                .toArray();
        int[] expected = units.clone();
        Arrays.sort(expected);
        if (!Arrays.equals(shown, expected)) {
            return "the level shows unit bodies " + Arrays.toString(shown) + " for the units "
                    + Arrays.toString(expected);
        }
        return null;
    }

    private static void finish(MinecraftServer server, @Nullable String problem, @Nullable Run r) {
        run = null;
        JsonObject marker = new JsonObject();
        marker.addProperty("ok", problem == null);
        if (problem != null) {
            marker.addProperty("reason", problem);
            LOG.error("Server smoke failed: {}", problem);
        }
        marker.addProperty("holderAtAboutToStart", holderAtAboutToStart);
        MatchCore core = r == null ? null : r.match.core();
        if (r != null && core != null) {
            marker.addProperty("ticks", r.ticks);
            marker.addProperty("units", core.queries().unitCount());
            marker.addProperty("bodies", r.match.bodies().count());
            if (r.restart) {
                marker.addProperty("restoredTick", r.restoredTick);
            }
            marker.addProperty("tick", core.queries().tick());
            marker.addProperty("hash", Hex64.format(core.stateHash()));
            marker.addProperty("mspt_p50_ms", medianMillis(server.getTickTimesNanos()));
        }
        writeMarker(marker);
        markerWritten = true;
        server.halt(false); // never true: this handler runs on the server thread
    }

    /** CORE-01.6: the holder is gone once the server has stopped; lowest priority, after the holder's listener. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStopped(ServerStoppedEvent event) {
        if (!active() || !markerWritten) {
            return;
        }
        Path file = markerFile();
        JsonObject marker = readMarker(file);
        boolean cleared = StrategosServer.getOrNull() == null;
        marker.addProperty("holderClearedOnStop", cleared);
        if (!cleared && marker.get("ok").getAsBoolean()) {
            marker.addProperty("ok", false);
            marker.addProperty("reason", "the Strategos server holder survived ServerStoppedEvent");
        }
        writeMarker(marker);
    }

    /** The median of the recorded tick times, in milliseconds; slots not yet written are left out. */
    private static double medianMillis(long[] tickNanos) {
        long[] recorded = Arrays.stream(tickNanos).filter(n -> n > 0).sorted().toArray();
        return recorded.length == 0 ? 0 : recorded[(recorded.length - 1) / 2] / 1e6;
    }

    private static Path markerFile() {
        String path = System.getProperty("strategos.smoke.marker");
        if (path == null) {
            throw new IllegalStateException("strategos.smoke.marker is not set");
        }
        return Path.of(path).toAbsolutePath();
    }

    /** A file beside this run's marker: both runs share the game directory. */
    private static Path sibling(String name) {
        return markerFile().resolveSibling(name);
    }

    private static JsonObject readMarker(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the smoke marker " + file, e);
        }
    }

    private static void writeMarker(JsonObject marker) {
        Path file = markerFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file, new GsonBuilder().setPrettyPrinting().create().toJson(marker) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the smoke marker " + file, e);
        }
    }
}
