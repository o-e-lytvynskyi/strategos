package dev.strategos.gametest.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
import dev.strategos.core.sim.UnitView;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.StrategosServer;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The L5 server smoke: boots a dedicated server, checks it, runs a headless match, writes the marker the Gradle guard
 * reads, and stops the server. Acts only when {@code strategos.smoke} is {@code true} (run 1; the restart run of T9b
 * uses {@code restart}).
 *
 * <ol>
 *   <li>the mod is loaded, core runs in the mod module, the holder exists, the unit body type is registered;
 *   <li>a 16 x 16 match around the world spawn; 8 units around local (4, 8), ordered to (12.5, 8.5);
 *   <li>100 server ticks later: every unit on the target, 8 bodies, the match RUNNING;
 *   <li>the marker with the numbers, then {@code halt}; a 600-tick deadline fails the run.
 * </ol>
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class SmokeDriver {
    private static final Logger LOG = LoggerFactory.getLogger(SmokeDriver.class);
    private static final int UNITS = 8;
    private static final Vec2d START = new Vec2d(4, 8);
    private static final Vec2d TARGET = new Vec2d(12.5, 8.5);
    private static final int CHECK_AT = 100;
    private static final int DEADLINE = 600;

    /** The running smoke; gametest code, so a static holder is fine. Server thread only. */
    private static @Nullable Run run;

    private SmokeDriver() {}

    private static final class Run {
        final MinecraftServer server;
        final MatchRuntime match;
        int ticks;
        boolean ordered;

        Run(MinecraftServer server, MatchRuntime match) {
            this.server = server;
            this.match = match;
        }
    }

    /** Lowest priority, so it runs after the holder's own listeners. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStarted(ServerStartedEvent event) {
        if (!"true".equals(System.getProperty("strategos.smoke"))) {
            return;
        }
        MinecraftServer server = event.getServer();
        String problem = startupProblem();
        if (problem != null) {
            finish(server, problem, null);
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int floor = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        BlockPos feet = new BlockPos(spawn.getX(), floor, spawn.getZ());
        // Saved matches arrive with M0 T9; T9b makes this match persistent for the restart run.
        MatchRuntime match = StrategosServer.get()
                .matches()
                .create(
                        overworld,
                        StrategosCommands.spaceAround(overworld, feet, 16, 16),
                        MatchOptions.generated(floor).withPersistent(false));
        match.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), START, UNITS, 2));
        run = new Run(server, match);
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
        int[] units = core.queries().unitHandles();
        if (!r.ordered && units.length == UNITS) {
            r.match.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), TARGET));
            r.ordered = true;
        }
        if (r.ticks == CHECK_AT) {
            finish(r.server, matchProblem(r, core, units), r);
        } else if (r.ticks >= DEADLINE) {
            finish(r.server, "no verdict within " + DEADLINE + " ticks", r);
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

    private static void finish(MinecraftServer server, @Nullable String problem, @Nullable Run r) {
        run = null;
        JsonObject marker = new JsonObject();
        marker.addProperty("ok", problem == null);
        if (problem != null) {
            marker.addProperty("reason", problem);
            LOG.error("Server smoke failed: {}", problem);
        }
        MatchCore core = r == null ? null : r.match.core();
        if (r != null && core != null) {
            marker.addProperty("ticks", r.ticks);
            marker.addProperty("units", core.queries().unitCount());
            marker.addProperty("bodies", r.match.bodies().count());
            marker.addProperty("tick", core.queries().tick());
            marker.addProperty("hash", Hex64.format(core.stateHash()));
            marker.addProperty("mspt_p50_ms", medianMillis(server.getTickTimesNanos()));
        }
        writeMarker(marker);
        server.halt(false); // never true: this handler runs on the server thread
    }

    /** The median of the recorded tick times, in milliseconds; slots not yet written are left out. */
    private static double medianMillis(long[] tickNanos) {
        long[] recorded = Arrays.stream(tickNanos).filter(n -> n > 0).sorted().toArray();
        return recorded.length == 0 ? 0 : recorded[(recorded.length - 1) / 2] / 1e6;
    }

    private static void writeMarker(JsonObject marker) {
        String path = System.getProperty("strategos.smoke.marker");
        if (path == null) {
            throw new IllegalStateException("strategos.smoke.marker is not set");
        }
        try {
            Path file = Path.of(path);
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(
                    file, new GsonBuilder().setPrettyPrinting().create().toJson(marker) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the smoke marker " + path, e);
        }
    }
}
