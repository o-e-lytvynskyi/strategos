package dev.strategos.gametest.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.registry.ModTickets;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.persist.RegistryData;
import dev.strategos.mod.server.world.ArenaPolicy;
import dev.strategos.mod.server.world.ArenaTickets;
import dev.strategos.mod.server.world.ArenaWorld;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The L5 arena smoke (spec §7.5, MAP-07.2 and .4): a dedicated server with {@code level-type=strategos:arena}, twice in
 * one game directory. Acts only when {@code strategos.smoke} is {@code arena1} or {@code arena2}.
 *
 * <p>Run 1, on a fresh world, with {@code strategos.smoke.leakTickets}:
 *
 * <ol>
 *   <li>the start applied D-13's rules and the registry recorded it;
 *   <li>the owner turns {@code doFireTick} on;
 *   <li>match A takes slot 0 and B slot 1, 2,048 blocks on, each holding its chunk tickets;
 *   <li>A is disposed, but the hook keeps its tickets, as a crash would; B stays, saved.
 * </ol>
 *
 * <p>Run 2: the rules were not applied again ({@code doFireTick} is still on); A's tickets were dropped when the level
 * loaded and B's kept; B is back PAUSED; a new match C takes slot 2, never a used one, and disposing it releases its
 * tickets.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class ArenaSmokeDriver {
    private static final Logger LOG = LoggerFactory.getLogger(ArenaSmokeDriver.class);
    private static final int SIZE = 32;

    private ArenaSmokeDriver() {}

    private static String mode() {
        return System.getProperty("strategos.smoke", "");
    }

    /** Lowest priority: after the holder loaded the saved matches and checked the arena. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStarted(ServerStartedEvent event) {
        String mode = mode();
        if (!mode.equals("arena1") && !mode.equals("arena2")) {
            return;
        }
        MinecraftServer server = event.getServer();
        JsonObject marker = new JsonObject();
        String problem;
        try {
            problem = mode.equals("arena1") ? run1(server, marker) : run2(server, marker);
        } catch (RuntimeException e) {
            problem = "the arena smoke threw " + e;
            LOG.error("Arena smoke threw", e);
        }
        marker.addProperty("ok", problem == null);
        if (problem != null) {
            marker.addProperty("reason", problem);
            LOG.error("Arena smoke failed: {}", problem);
        }
        writeMarker(markerFile(), marker);
        server.halt(false); // never true: this handler runs on the server thread
    }

    private static @Nullable String run1(MinecraftServer server, JsonObject marker) {
        StrategosServer s = StrategosServer.get();
        if (s.arena() != ArenaWorld.Outcome.APPLIED) {
            return "the first start of an arena world did " + s.arena() + ", not APPLIED";
        }
        RegistryData registry = s.matches().persisted();
        if (registry == null || !registry.arenaRulesApplied()) {
            return "the registry does not record the arena rules";
        }
        String rules = rulesProblem(server);
        if (rules != null) {
            return rules;
        }
        if (!s.settings().leakTickets()) {
            return "run 1 needs strategos.smoke.leakTickets";
        }
        server.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, server); // the owner's later change

        MatchRegistry matches = s.matches();
        MatchRuntime a = matches.createInArena(SIZE, SIZE, MatchOptions.generated(ArenaPolicy.FLOOR_Y));
        MatchRuntime b = matches.createInArena(SIZE, SIZE, MatchOptions.generated(ArenaPolicy.FLOOR_Y));
        if (a.space().origin().getX() != 0 || b.space().origin().getX() != 2_048) {
            return "slots 0 and 1 start at x " + a.space().origin().getX() + " and "
                    + b.space().origin().getX();
        }
        ServerLevel level = server.overworld();
        ChunkPos aChunk = new ChunkPos(a.space().origin());
        ChunkPos bChunk = new ChunkPos(b.space().origin());
        if (!held(level, a.id(), aChunk) || !held(level, b.id(), bChunk)) {
            return "a new arena match holds no ticket on its first chunk";
        }
        matches.dispose(a.id());
        if (!held(level, a.id(), aChunk)) {
            return "disposing A released its tickets despite strategos.smoke.leakTickets";
        }
        marker.addProperty("leaked", a.id().value());
        marker.addProperty("kept", b.id().value());
        return null;
    }

    private static @Nullable String run2(MinecraftServer server, JsonObject marker) {
        StrategosServer s = StrategosServer.get();
        if (s.arena() != ArenaWorld.Outcome.ALREADY_APPLIED) {
            return "the second start did " + s.arena() + ", not ALREADY_APPLIED";
        }
        if (!server.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return "doFireTick is off again: the second start re-applied the arena rules over the owner's change";
        }
        JsonObject run1 = readMarker(markerFile().resolveSibling("arena-run1.json"));
        MatchId leaked = new MatchId(run1.get("leaked").getAsInt());
        MatchId kept = new MatchId(run1.get("kept").getAsInt());
        ServerLevel level = server.overworld();
        ChunkPos leakedChunk = new ChunkPos(slotOrigin(0));
        if (ModTickets.MATCH.forceChunk(
                level, ArenaTickets.owner(leaked), leakedChunk.x, leakedChunk.z, false, false)) {
            return "the leaked tickets of " + leaked + " survived the restart";
        }
        List<MatchRuntime> all = s.matches().all();
        if (all.size() != 1 || !all.get(0).id().equals(kept) || all.get(0).lifecycle() != MatchLifecycle.PAUSED) {
            return "after the restart the matches are "
                    + all.stream().map(rt -> rt.id() + " " + rt.lifecycle()).toList() + ", not " + kept + " PAUSED";
        }
        if (!held(level, kept, new ChunkPos(slotOrigin(1)))) {
            return "the restart dropped the tickets of the saved match " + kept;
        }
        MatchRuntime c = s.matches().createInArena(SIZE, SIZE, MatchOptions.generated(ArenaPolicy.FLOOR_Y));
        if (c.space().origin().getX() != 4_096) {
            return "the third match took the slot at x " + c.space().origin().getX() + ", not slot 2 at 4096";
        }
        ChunkPos cChunk = new ChunkPos(c.space().origin());
        s.matches().dispose(c.id());
        if (ModTickets.MATCH.forceChunk(level, ArenaTickets.owner(c.id()), cChunk.x, cChunk.z, false, false)) {
            return "disposing " + c.id() + " left its tickets";
        }
        marker.addProperty("slot", 2);
        return null;
    }

    /** D-13's rules and noon, as the first start sets them. */
    private static @Nullable String rulesProblem(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        if (rules.getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || rules.getBoolean(GameRules.RULE_DOFIRETICK)
                || rules.getBoolean(GameRules.RULE_MOBGRIEFING)
                || rules.getBoolean(GameRules.RULE_DAYLIGHT)
                || rules.getBoolean(GameRules.RULE_WEATHER_CYCLE)
                || rules.getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS)
                || rules.getInt(GameRules.RULE_RANDOMTICKING) != 0) {
            return "the arena rules are not all set: " + rules.createTag();
        }
        if (server.overworld().getDayTime() != ArenaWorld.NOON) {
            return "the arena's time is " + server.overworld().getDayTime() + ", not noon";
        }
        return null;
    }

    /**
     * Whether the match holds its ticket on {@code chunk}. Adding a ticket that is there changes nothing and returns
     * false (ForcedChunkManager.java:94, 385); a missing one is added, which only happens when the run fails anyway.
     */
    private static boolean held(ServerLevel level, MatchId id, ChunkPos chunk) {
        return !ModTickets.MATCH.forceChunk(level, ArenaTickets.owner(id), chunk.x, chunk.z, true, false);
    }

    private static BlockPos slotOrigin(int slot) {
        return ArenaPolicy.slotSpace(slot, SIZE, SIZE).origin();
    }

    private static Path markerFile() {
        String path = System.getProperty("strategos.smoke.marker");
        if (path == null) {
            throw new IllegalStateException("strategos.smoke.marker is not set");
        }
        return Path.of(path).toAbsolutePath();
    }

    private static JsonObject readMarker(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the arena smoke marker " + file, e);
        }
    }

    private static void writeMarker(Path file, JsonObject marker) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file, new GsonBuilder().setPrettyPrinting().create().toJson(marker) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the arena smoke marker " + file, e);
        }
    }
}
