package dev.strategos.mod.server.world;

import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.persist.RegistryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The arena world (04 §3.2, MAP-07): a world made with the {@code strategos:arena} preset, known by its overworld's
 * generator, a {@code FlatLevelSource} whose biome is {@code strategos:arena} (FlatLevelSource.java:55,
 * FlatLevelGeneratorSettings.java:184). The first time such a world starts it gets D-13's controlled-arena rules and a
 * 9 x 9 stone lobby pad under the spawn, written through the block writer (rule B7); the registry blob records each, so
 * the owner's later changes stick. Gamerules belong to the whole world, so no other world's rules are ever touched.
 */
public final class ArenaWorld {
    /** {@code level-type=strategos:arena} on a dedicated server; "Strategos Arena" in Create World. */
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Ids.id("arena"));

    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, Ids.id("arena"));

    /** Noon: the time an arena keeps, its daylight cycle off. */
    public static final long NOON = 6_000;

    /** The lobby pad reaches this many blocks from the spawn on each side: 9 x 9. */
    public static final int PAD_REACH = 4;

    /** The writes that are not a match's, such as the lobby pad. */
    public static final WriteSite WORLD_SITE = new WriteSite(0, 0);

    private static final Logger LOG = LoggerFactory.getLogger(ArenaWorld.class);

    /** What {@link #onStarted} did. */
    public enum Outcome {
        NOT_AN_ARENA,
        APPLIED,
        ALREADY_APPLIED,
        REGISTRY_UNUSABLE
    }

    private ArenaWorld() {}

    public static boolean isArena(MinecraftServer server) {
        return server.overworld().getChunkSource().getGenerator() instanceof FlatLevelSource flat
                && flat.settings().getBiome().is(BIOME);
    }

    /**
     * On server start, once the registry blob is loaded: an arena world gets the rules, and the lobby pad, the first
     * time. A registry that cannot record them leaves the world alone, so an owner's choices are never overwritten on a
     * later start.
     */
    public static Outcome onStarted(MinecraftServer server, @Nullable RegistryData registry, BlockWriter writer) {
        if (!isArena(server)) {
            return Outcome.NOT_AN_ARENA;
        }
        if (registry == null || registry.quarantineReason() != null) {
            LOG.error("Arena rules not applied: the match registry cannot record them");
            return Outcome.REGISTRY_UNUSABLE;
        }
        if (!registry.lobbyPadBuilt()) {
            buildLobbyPad(server.overworld(), writer, server.getTickCount());
            registry.markLobbyPadBuilt();
        }
        if (registry.arenaRulesApplied()) {
            return Outcome.ALREADY_APPLIED;
        }
        applyRules(server);
        registry.markArenaRulesApplied();
        LOG.info("Arena rules applied to this new arena world");
        return Outcome.APPLIED;
    }

    /**
     * Stone under the world spawn, written now: no match runs at start, so the writer takes it under the import budget
     * in one flush, before the flag that records it is set.
     */
    private static void buildLobbyPad(ServerLevel overworld, BlockWriter writer, long tick) {
        BlockPos floor = overworld.getSharedSpawnPos().below();
        for (int dx = -PAD_REACH; dx <= PAD_REACH; dx++) {
            for (int dz = -PAD_REACH; dz <= PAD_REACH; dz++) {
                writer.enqueue(
                        overworld,
                        WORLD_SITE,
                        WritePriority.CONSTRUCTION,
                        floor.offset(dx, 0, dz),
                        Blocks.STONE.defaultBlockState(),
                        0);
            }
        }
        writer.flush(overworld, tick, false);
        if (writer.pending(overworld) != 0) {
            throw new IllegalStateException("the lobby pad did not fit one import flush");
        }
    }

    /** D-13 (GameRules.java:35–101, {@code set} 304 and 408): no spawning, fire, griefing, weather or random ticks. */
    private static void applyRules(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        rules.getRule(GameRules.RULE_SPECTATORSGENERATECHUNKS).set(false, server);
        server.overworld().setDayTime(NOON);
    }
}
