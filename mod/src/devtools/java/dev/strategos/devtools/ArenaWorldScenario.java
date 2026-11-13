package dev.strategos.devtools;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.menu.ArenaWorldLauncher;
import dev.strategos.mod.server.world.ArenaWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.Nullable;

/**
 * MATCH-14.1 (spike S13): the arena world made from code, then opened again. The first world comes from
 * {@link ArenaWorldLauncher} with no save of its name: it must be an arena with the preset's biome under the spawn, the
 * lobby pad, D-13's rules, and {@code ArenaWorld.onStarted} reporting APPLIED; the scenario then turns the daylight
 * cycle on, as an owner might, and leaves. The launcher opens the same save the second time: ALREADY_APPLIED, the
 * owner's daylight cycle kept, the confirmed-experimental flag set, and still one save of that name.
 */
final class ArenaWorldScenario implements Scenario {
    private int launches;
    private volatile @Nullable List<String> problems;
    private boolean left;

    @Override
    public String name() {
        return "arena_world";
    }

    @Override
    public String worldName() {
        return ArenaWorldLauncher.WORLD;
    }

    @Override
    public boolean openWorld(Minecraft mc) {
        launches++;
        problems = null;
        StrategosClient.arena().launch(mc, new TitleScreen());
        return true;
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        List<String> found = new ArrayList<>();
        boolean first = launches == 1;
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        GameRules rules = server.getGameRules();
        if (!ArenaWorld.isArena(server)) {
            found.add("not an arena");
        }
        if (!overworld.getBiome(spawn).is(ArenaWorld.BIOME)) {
            found.add(
                    "the biome under the spawn is " + overworld.getBiome(spawn).getRegisteredName());
        }
        if (!overworld.getBlockState(spawn.below()).is(Blocks.STONE)) {
            found.add("no lobby pad under the spawn: " + overworld.getBlockState(spawn.below()));
        }
        ArenaWorld.Outcome outcome = StrategosServer.get().arena();
        if (outcome != (first ? ArenaWorld.Outcome.APPLIED : ArenaWorld.Outcome.ALREADY_APPLIED)) {
            found.add("onStarted said " + outcome + " at launch " + launches);
        }
        if (rules.getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || rules.getBoolean(GameRules.RULE_DOFIRETICK)
                || rules.getBoolean(GameRules.RULE_MOBGRIEFING)
                || rules.getBoolean(GameRules.RULE_WEATHER_CYCLE)
                || rules.getInt(GameRules.RULE_RANDOMTICKING) != 0) {
            found.add("D-13's rules are not all applied");
        }
        boolean daylight = rules.getBoolean(GameRules.RULE_DAYLIGHT);
        if (first) {
            if (daylight) {
                found.add("the daylight cycle runs in a new arena");
            }
            rules.getRule(GameRules.RULE_DAYLIGHT).set(true, server); // the owner's change, which must stick
        } else {
            if (!daylight) {
                found.add("the arena rules were applied again: the owner's daylight cycle is off");
            }
            if (!(server.getWorldData() instanceof PrimaryLevelData data) || !data.hasConfirmedExperimentalWarning()) {
                found.add("the reopened arena has no confirmed-experimental flag");
            }
        }
        ctx.result.addProperty("outcome_" + launches, outcome.name());
        ctx.result.addProperty(
                "gameType_" + launches, server.getWorldData().getGameType().getName());
        ctx.result.addProperty(
                "difficulty_" + launches, server.getWorldData().getDifficulty().getKey());
        ctx.result.addProperty("cheats_" + launches, server.getWorldData().isAllowCommands());
        problems = found;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        List<String> found = problems;
        if (found == null) {
            return false;
        }
        if (launches == 1 && !left) {
            if (!found.isEmpty()) {
                return fail(ctx, found);
            }
            if (tick == 20) {
                ctx.capture("arena_created");
            } else if (tick > 40) {
                left = true;
                ctx.switchWorld(ArenaWorldLauncher.WORLD, 0);
            }
            return false;
        }
        if (launches < 2) {
            return false;
        }
        int saves = saves(mc.getLevelSource());
        if (saves != 1) {
            found = new ArrayList<>(found);
            found.add(saves + " saves named " + ArenaWorldLauncher.WORLD + "*");
        }
        if (!found.isEmpty()) {
            return fail(ctx, found);
        }
        if (tick == 20) {
            ctx.capture("arena_reopened");
        }
        if (tick > 40) {
            ctx.result.addProperty("ok", true);
            return true;
        }
        return false;
    }

    private static boolean fail(ScenarioContext ctx, List<String> found) {
        ctx.result.addProperty("ok", false);
        ctx.result.addProperty("reason", String.join("; ", found));
        return true;
    }

    private static int saves(LevelStorageSource source) {
        int n = 0;
        try {
            for (LevelStorageSource.LevelDirectory dir : source.findLevelCandidates()) {
                if (dir.directoryName().startsWith(ArenaWorldLauncher.WORLD)) {
                    n++;
                }
            }
        } catch (LevelStorageException e) {
            return -1;
        }
        return n;
    }
}
