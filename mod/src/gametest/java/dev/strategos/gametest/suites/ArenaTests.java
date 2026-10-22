package dev.strategos.gametest.suites;

import com.mojang.serialization.Lifecycle;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.persist.RegistryData;
import dev.strategos.mod.server.world.ArenaWorld;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * The arena world (MAP-07). The preset is checked here rather than in L2: world presets, biomes and dimension types are
 * data-driven registries that exist only on a server with its data packs (spec §1.6 D row 14).
 */
public final class ArenaTests {
    private ArenaTests() {}

    /**
     * MAP-07.1: the preset decodes, joins {@code #minecraft:normal}, makes a void flat overworld of biome {@code
     * strategos:arena} with dimension type {@code minecraft:overworld} and the vanilla nether and end, and so bakes
     * stable, not experimental; the biome has no spawners, precipitation or features.
     */
    @StrategosTest
    public static void presetDecodesInNormal(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Optional<Holder.Reference<WorldPreset>> preset =
                server.registryAccess().registryOrThrow(Registries.WORLD_PRESET).getHolder(ArenaWorld.PRESET);
        if (preset.isEmpty()) {
            helper.fail("no world preset " + ArenaWorld.PRESET.location());
            return;
        }
        if (!preset.get().is(WorldPresetTags.NORMAL)) {
            helper.fail("the arena preset is not in #minecraft:normal, so Create World does not list it");
        }
        WorldDimensions dimensions = preset.get().value().createWorldDimensions();
        if (!dimensions.dimensions().keySet().equals(Set.of(LevelStem.OVERWORLD, LevelStem.NETHER, LevelStem.END))) {
            helper.fail("the preset's dimensions are " + dimensions.dimensions().keySet());
        }
        LevelStem overworld = dimensions.get(LevelStem.OVERWORLD).orElseThrow();
        if (!overworld.type().is(BuiltinDimensionTypes.OVERWORLD)) {
            helper.fail("the overworld's dimension type is " + overworld.type());
        }
        if (!(overworld.generator() instanceof FlatLevelSource flat)
                || !flat.settings().getBiome().is(ArenaWorld.BIOME)) {
            helper.fail("the overworld is not a flat level of biome " + ArenaWorld.BIOME.location());
            return;
        }
        for (BlockState layer : flat.settings().getLayers()) {
            if (!layer.isAir()) {
                helper.fail("the arena's layers hold " + layer + ", not only air");
            }
        }
        Lifecycle lifecycle = dimensions
                .bake(new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze())
                .lifecycle();
        if (!lifecycle.equals(Lifecycle.stable())) {
            helper.fail("the arena world bakes " + lifecycle + ", so Create World would call it experimental");
        }
        Biome biome = server.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(ArenaWorld.BIOME);
        if (biome.hasPrecipitation()) {
            helper.fail("the arena biome has precipitation");
        }
        for (MobCategory category : MobCategory.values()) {
            if (!biome.getMobSettings().getMobs(category).isEmpty()) {
                helper.fail("the arena biome spawns " + category);
            }
        }
        if (!biome.getGenerationSettings().features().stream().allMatch(step -> step.size() == 0)) {
            helper.fail("the arena biome has features");
        }
        helper.succeed();
    }

    /**
     * MAP-07.3: a world that is not an arena keeps its gamerules and time: the check that runs on every start changes
     * nothing, now or when run again.
     */
    @StrategosTest
    public static void foreignWorldRulesUntouched(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        if (ArenaWorld.isArena(server)) {
            helper.fail("the GameTest world counts as an arena");
            return;
        }
        if (StrategosServer.get().arena() != ArenaWorld.Outcome.NOT_AN_ARENA) {
            helper.fail("the start's arena check did " + StrategosServer.get().arena());
        }
        CompoundTag before = server.getGameRules().createTag();
        long time = server.overworld().getDayTime();
        RegistryData registry = StrategosServer.get().matches().persisted();
        boolean recorded = registry != null && registry.arenaRulesApplied();

        ArenaWorld.Outcome outcome = ArenaWorld.onStarted(server, registry);

        if (outcome != ArenaWorld.Outcome.NOT_AN_ARENA) {
            helper.fail("the arena check did " + outcome + " in a world that is not an arena");
        }
        if (!server.getGameRules().createTag().equals(before)) {
            helper.fail("the gamerules changed: " + before + " -> "
                    + server.getGameRules().createTag());
        }
        if (server.overworld().getDayTime() != time) {
            helper.fail(
                    "the time moved from " + time + " to " + server.overworld().getDayTime());
        }
        if (recorded || (registry != null && registry.arenaRulesApplied())) {
            helper.fail("the registry records arena rules in a world that is not an arena");
        }
        helper.succeed();
    }
}
