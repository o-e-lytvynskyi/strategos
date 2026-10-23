package dev.strategos.devtools;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import dev.strategos.mod.Strategos;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The L4 scenario runner (spec 7.7), only when {@code strategos.scenario} is set (the {@code scenario} run): waits for
 * the title screen, creates a flat creative peaceful world, waits until it is rendered, sets the scenario up on the
 * integrated server, ticks it, writes {@code result.json} and stops the client. A watchdog exits 1 after
 * {@code strategos.scenario.timeoutSeconds}. Opens a window, so it runs only with the owner's consent.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID, value = Dist.CLIENT)
public final class ScenarioRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ScenarioRunner.class);

    private enum Phase {
        TITLE,
        LOADING,
        RUNNING,
        DONE
    }

    /** The running scenario; dev code on the client thread, so static state is fine. */
    private static @Nullable Run run;

    private static boolean started;

    private ScenarioRunner() {}

    private static final class Run {
        final Scenario scenario;
        final ScenarioContext ctx;
        Phase phase = Phase.TITLE;
        String world;
        long seed;
        int ticks;

        Run(Scenario scenario, ScenarioContext ctx) {
            this.scenario = scenario;
            this.ctx = ctx;
            this.world = scenario.worldName();
            this.seed = scenario.seed();
        }
    }

    private static @Nullable Scenario scenarioNamed(String name) {
        return switch (name) {
            case "smoke" -> new SmokeScenario();
            case "stall" -> new StallScenario();
            case "world_switch" -> new WorldSwitchScenario();
            case "s4_units" -> new UnitsFrameTimeScenario();
            case "s10_steps" -> new S10StepsScenario();
            case "s9" -> new S9CameraScenario();
            case "rts_basics" -> new RtsBasicsScenario();
            default -> null;
        };
    }

    private static void start() {
        started = true;
        String name = System.getProperty("strategos.scenario", "");
        if (name.isBlank()) {
            return;
        }
        Path out = Path.of(System.getProperty("strategos.scenario.out", "scenario-out"));
        Scenario scenario = scenarioNamed(name);
        if (scenario == null) {
            write(out, failure("no scenario " + name));
            Runtime.getRuntime().halt(1);
            return;
        }
        Run r = new Run(scenario, new ScenarioContext(out));
        run = r;
        startWatchdog(r);
        LOG.info("Scenario {} starts", name);
    }

    private static void startWatchdog(Run r) {
        long seconds = Long.parseLong(System.getProperty("strategos.scenario.timeoutSeconds", "180"));
        Thread watchdog = new Thread(
                () -> {
                    try {
                        Thread.sleep(seconds * 1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (r.phase != Phase.DONE) {
                        write(r.ctx.out, failure("watchdog: no result within " + seconds + " s"));
                        Runtime.getRuntime().halt(1);
                    }
                },
                "strategos-scenario-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Run r = run;
        if (r != null && r.phase == Phase.RUNNING) {
            holdCamera(Minecraft.getInstance(), r.ctx);
            r.scenario.clientTickStart();
        }
    }

    /**
     * The scenario's camera pose, with the cursor released, so input to a focused window changes nothing. The vignette
     * fades by 1 % a client tick towards the light at the camera (Gui#updateVignetteBrightness), so a frame would
     * depend on how many ticks the load took; it is pinned at its limit under the open noon sky, 0.
     */
    private static void holdCamera(Minecraft mc, ScenarioContext ctx) {
        double[] pose = ctx.camera;
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
        mc.gui.vignetteBrightness = 0f;
        if (pose != null && mc.player != null) {
            mc.player.moveTo(pose[0], pose[1], pose[2], (float) pose[3], (float) pose[4]);
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        if (!started) {
            start();
        }
        Run r = run;
        if (r == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (r.phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen title && mc.getOverlay() == null) {
                    String address = r.scenario.server();
                    if (address != null) {
                        ConnectScreen.startConnecting(
                                title,
                                mc,
                                ServerAddress.parseString(address),
                                new ServerData("strategos-spike", address, ServerData.Type.OTHER),
                                false,
                                null);
                    } else {
                        createWorld(mc, r.world, r.seed);
                    }
                    r.phase = Phase.LOADING;
                }
            }
            case LOADING -> {
                if (r.scenario.server() != null) {
                    if (mc.level != null
                            && mc.player != null
                            && mc.screen == null
                            && mc.levelRenderer.hasRenderedAllSections()) {
                        r.scenario.joined(mc, r.ctx);
                        r.ticks = 0;
                        r.phase = Phase.RUNNING;
                    }
                    return;
                }
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null
                        && mc.level != null
                        && mc.player != null
                        && mc.screen == null
                        && mc.levelRenderer.hasRenderedAllSections()) {
                    server.execute(() -> r.scenario.setup(server, r.ctx));
                    r.ticks = 0;
                    r.phase = Phase.RUNNING;
                }
            }
            case RUNNING -> {
                r.scenario.clientTickEnd();
                r.ticks++;
                if (r.scenario.tick(mc, r.ctx, r.ticks)) {
                    finish(mc, r);
                } else if (r.ctx.nextWorld != null) {
                    switchWorld(mc, r);
                }
            }
            case DONE -> {}
        }
    }

    @SubscribeEvent
    static void onFramePre(RenderFrameEvent.Pre event) {
        Run r = run;
        if (r != null && r.phase == Phase.RUNNING) {
            holdCamera(Minecraft.getInstance(), r.ctx);
            r.scenario.frameStart();
        }
    }

    /** Screenshots, synchronous, from the frame just rendered. */
    @SubscribeEvent
    static void onFramePost(RenderFrameEvent.Post event) {
        Run r = run;
        if (r == null || r.phase != Phase.RUNNING) {
            return;
        }
        r.scenario.frameEnd();
        String name = r.ctx.captureName;
        if (name != null) {
            r.ctx.captureName = null;
            try (NativeImage image =
                    Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                Files.createDirectories(r.ctx.out);
                image.writeToFile(r.ctx.out.resolve(name + ".png"));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot write the screenshot " + name, e);
            }
        }
    }

    private static void createWorld(Minecraft mc, String name, long seed) {
        LevelSettings settings = new LevelSettings(
                name,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows()
                .createFreshLevel(
                        name,
                        settings,
                        new WorldOptions(seed, false, false),
                        registries -> registries
                                .registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT)
                                .value()
                                .createWorldDimensions(),
                        new TitleScreen());
    }

    /** The pause menu's Save and Quit, queued outside the client tick (disconnect waits for the server to stop). */
    private static void switchWorld(Minecraft mc, Run r) {
        String next = r.ctx.nextWorld;
        if (next == null) {
            return;
        }
        r.ctx.nextWorld = null;
        r.world = next;
        r.seed = r.ctx.nextSeed;
        r.phase = Phase.TITLE;
        mc.tell(() -> {
            if (mc.level != null) {
                mc.level.disconnect();
            }
            mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            mc.setScreen(new TitleScreen());
        });
    }

    private static void finish(Minecraft mc, Run r) {
        r.phase = Phase.DONE;
        if (!r.ctx.result.has("ok")) {
            r.ctx.result.addProperty("ok", true);
        }
        r.ctx.result.addProperty("scenario", r.scenario.name());
        write(r.ctx.out, r.ctx.result);
        LOG.info("Scenario {} done: {}", r.scenario.name(), r.ctx.result);
        mc.stop();
    }

    private static com.google.gson.JsonObject failure(String reason) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("ok", false);
        json.addProperty("reason", reason);
        return json;
    }

    private static void write(Path out, com.google.gson.JsonObject result) {
        try {
            Files.createDirectories(out);
            Files.writeString(
                    out.resolve("result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the scenario result", e);
        }
    }
}
