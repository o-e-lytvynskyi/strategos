package dev.strategos.mod.client.menu;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.server.net.payload.SkirmishPayload;
import dev.strategos.mod.server.world.ArenaWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.Nullable;

/**
 * Opens the skirmish arena from code (MATCH-14, spec §2.9, spike S13): the save {@value #WORLD} when it exists
 * ({@code WorldOpenFlows#openWorld}), otherwise a fresh world of the {@code strategos:arena} preset
 * ({@code createFreshLevel}); {@code ArenaWorld.onStarted} gives it the arena rules once. A save of that name that is
 * not an arena is refused: the player goes back to where the launch started, with a message. A launch from the setup
 * screen then sends its {@code SkirmishSetup} (MATCH-02). One instance, held by
 * {@code StrategosClient}; client thread only.
 */
public final class ArenaWorldLauncher {
    /** The save every skirmish plays in: a new match per skirmish, never a new world (MATCH-02). */
    public static final String WORLD = "strategos_skirmish";

    static final String DISPLAY_NAME = "Strategos Skirmish";

    /** Where to go back if the world just launched is not an arena; null when no launch waits for its check. */
    private @Nullable Screen awaitingCheck;

    /** The skirmish to ask the server for once the arena is joined; null for a plain launch. */
    private @Nullable SkirmishSetup awaitingSetup;

    public void launch(Minecraft mc, Screen parent) {
        launch(mc, parent, null);
    }

    /** As {@link #launch(Minecraft, Screen)}, then, in an arena, {@code setup} sent as {@code strategos:skirmish}. */
    public void launch(Minecraft mc, Screen parent, @Nullable SkirmishSetup setup) {
        awaitingCheck = parent;
        awaitingSetup = setup;
        if (mc.getLevelSource().levelExists(WORLD)) {
            mc.createWorldOpenFlows().openWorld(WORLD, () -> {
                awaitingCheck = null;
                mc.setScreen(parent);
            });
        } else {
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            WORLD,
                            settings(),
                            new WorldOptions(WorldOptions.randomSeed(), false, false),
                            registries -> registries
                                    .registryOrThrow(Registries.WORLD_PRESET)
                                    .getHolderOrThrow(ArenaWorld.PRESET)
                                    .value()
                                    .createWorldDimensions(),
                            parent);
        }
    }

    /** Adventure on the lobby pad, so its blocks stay; peaceful, no hardcore, no cheats (S13). */
    static LevelSettings settings() {
        return new LevelSettings(
                DISPLAY_NAME,
                GameType.ADVENTURE,
                false,
                Difficulty.PEACEFUL,
                false,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
    }

    /**
     * The player joined a world: if a launch waits, the integrated server checks it is an arena. An arena keeps
     * NeoForge's confirmed-experimental flag, so a reopen skips the backup screen its data-pack world preset would
     * raise; anything else sends the player back with a message.
     */
    public void onLoggedIn(Minecraft mc) {
        Screen parent = awaitingCheck;
        SkirmishSetup setup = awaitingSetup;
        awaitingCheck = null;
        awaitingSetup = null;
        IntegratedServer server = mc.getSingleplayerServer();
        if (parent == null || server == null) {
            return;
        }
        server.execute(() -> {
            if (!ArenaWorld.isArena(server)) {
                mc.execute(() -> refuse(mc, parent));
                return;
            }
            if (server.getWorldData() instanceof PrimaryLevelData data) {
                data.withConfirmedWarning(true);
            }
            if (setup != null) {
                mc.execute(() -> ClientPayloads.toServer(new SkirmishPayload(setup)));
            }
        });
    }

    private static void refuse(Minecraft mc, Screen parent) {
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        mc.setScreen(new AlertScreen(
                () -> mc.setScreen(parent),
                Component.translatable("strategos.skirmish.not_arena.title"),
                Component.translatable("strategos.skirmish.not_arena", WORLD)));
    }
}
