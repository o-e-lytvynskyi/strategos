package dev.strategos.devtools;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlUtil;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * PERF-14 with M5's scene (CMB-17.2, T17): the RTS view at 64 blocks over the ford of {@code
 * strategos:battle_at_the_ford} on the map {@code proving_ground}, render distance 16, about {@value #UNITS} units
 * fighting, the computer playing both sides. Three windows of {@value #WINDOW} ticks with the frame limit off: the
 * view over the bare map (base), the battle (battle), and, at vanilla's default limit of {@code
 * RtsFrameScenario.TICK_CAP} frames a second, the client tick (battleCapped). The units' cost is battle less base in the
 * frame interval's p50, as in {@link RtsFrameScenario}; the frame is the battle window's. The battle is live, with no
 * selection, so our drawing is the health bars' and the HUD's own timers together, in the same budget. The card's
 * budgets decide {@code ok}.
 */
final class BattleFrameScenario implements Scenario {
    static final int UNITS = 200;
    private static final int WINDOW = 160;
    private static final int LOAD_MIN = 300;
    private static final int RENDER_DISTANCE = 16;
    private static final int UNLIMITED = 260;
    private static final int LEAD = 20;
    private static final int SETTLE = 40;
    /** The armies' extras, per faction and ford, to bring the scenario's bodies to {@value #UNITS} and more: its 10 workers are never bodies on screen (a run showed 192 of 202), so 22 each leave 208. */
    private static final int EXTRA = 75;

    private enum Stage {
        LOADING,
        BASE,
        SPAWNING,
        BATTLE,
        BATTLE_CAPPED,
        DONE
    }

    private final StringBuilder failures = new StringBuilder();
    private final AtomicInteger bodiesOnServer = new AtomicInteger();
    private final AtomicInteger joined = new AtomicInteger();
    private final Map<String, RtsFrameScenario.Sample> samples = new HashMap<>();
    private volatile @Nullable MatchRuntime match;
    private Stage stage = Stage.LOADING;
    private int inStage;
    private RtsFrameScenario.@Nullable Sample sample;
    private RtsFrameScenario.@Nullable Sample pending;
    private int lead;

    @Override
    public String name() {
        return "rts_frame_battle";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerPlayer player = ScenarioWorld.player(server);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        match = M5GoldensScenario.createFord(server);
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(
                0.5,
                M5GoldensScenario.floorOf(server) + CameraRig.DEFAULT_HEIGHT,
                M5GoldensScenario.FORD_WORLD_Z - back,
                0,
                55);
    }

    @Override
    public void frameStart() {
        var s = sample;
        if (s != null) {
            s.intervals.end();
            s.frames.start();
            s.intervals.start();
        }
    }

    @Override
    public void frameEnd() {
        var s = sample;
        if (s != null) {
            s.frames.end();
            s.hudDraw.add(StrategosClient.hud().drawNanos());
            s.bars.add(StrategosClient.healthBars().nanos());
        }
    }

    @Override
    public void clientTickStart() {
        var s = sample;
        if (s != null) {
            s.ticks.start();
        }
    }

    @Override
    public void clientTickEnd() {
        var s = sample;
        if (s != null) {
            s.ticks.end();
            s.hudBuild.add(StrategosClient.hud().buildNanos());
        }
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        IntegratedServer server = mc.getSingleplayerServer();
        MatchRuntime rt = match;
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, RENDER_DISTANCE);
            mc.options.framerateLimit().set(UNLIMITED);
            mc.options.particles().set(ParticleStatus.ALL); // a player's default
        }
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (server == null || rt == null) {
            return false;
        }
        inStage++;
        if (pending != null && inStage >= lead) {
            sample = pending;
            pending = null;
        }
        switch (stage) {
            case LOADING -> {
                // once the map has run, the player is seated as its observer with no units yet: the bare map is base
                server.execute(() -> {
                    if (joined.get() == 0 && rt.lifecycle() == dev.strategos.core.sim.MatchLifecycle.RUNNING) {
                        ServerPlayer player = ScenarioWorld.player(server);
                        StrategosServer.get().matches().join(rt, player.getUUID(), PlayerSlot.ADMIN);
                        StrategosServer.get().commanders().enter(player, rt);
                        StrategosServer.get().net().joined(rt, player, PlayerSlot.ADMIN);
                        joined.set(1);
                    }
                });
                if (joined.get() == 1 && !rts.active() && tick % 20 == 0) {
                    rts.enter();
                }
                var rig = rts.rig();
                if (inStage >= LOAD_MIN
                        && joined.get() == 1
                        && rts.active()
                        && rts.view() != null
                        && rig != null
                        && mc.levelRenderer.hasRenderedAllSections()) {
                    rts.centreOn(0, M5GoldensScenario.FORD_WORLD_Z);
                    rig.zoomTo(CameraRig.MAX_HEIGHT);
                    ctx.result.addProperty("loadTicks", tick);
                    next(Stage.BASE, true, SETTLE);
                }
            }
            case BASE -> {
                if (inStage >= SETTLE + WINDOW) {
                    keep("base");
                    server.execute(() -> spawn(server, rt));
                    next(Stage.SPAWNING, false, 0);
                }
            }
            case SPAWNING -> {
                server.execute(() -> bodiesOnServer.set(rt.bodies().count()));
                if (bodiesOnServer.get() >= UNITS && bodies(mc) >= UNITS && inStage >= SETTLE) {
                    int seen = onScreen(mc, rts);
                    ctx.result.addProperty("onScreen", seen);
                    check(seen >= UNITS * 3 / 4, seen + " of " + UNITS + " units on screen");
                    ctx.capture("rts_frame_battle");
                    next(Stage.BATTLE, true, 0);
                } else if (inStage > 1_200) {
                    return fail(ctx, "only " + bodies(mc) + " bodies arrived");
                }
            }
            case BATTLE -> {
                if (inStage >= WINDOW) {
                    keep("battle");
                    ctx.result.addProperty("bodiesAtEnd", bodies(mc));
                    mc.options.framerateLimit().set(RtsFrameScenario.TICK_CAP);
                    next(Stage.BATTLE_CAPPED, true, LEAD);
                }
            }
            case BATTLE_CAPPED -> {
                if (inStage >= LEAD + WINDOW) {
                    keep("battleCapped");
                    next(Stage.DONE, false, 0);
                }
            }
            case DONE -> {
                return finish(mc, ctx);
            }
        }
        return false;
    }

    private void next(Stage to, boolean timed, int after) {
        stage = to;
        inStage = 0;
        sample = null;
        pending = timed ? new RtsFrameScenario.Sample() : null;
        lead = after;
    }

    private void keep(String name) {
        var s = sample;
        if (s != null) {
            samples.put(name, s);
        }
        sample = null;
        pending = null;
    }

    /** The scenario applied, the computer playing both sides, and 75 spearmen more a side at the north ford the camera looks at. Server thread. */
    private void spawn(IntegratedServer server, MatchRuntime rt) {
        M5GoldensScenario.applyBattle(server, rt);
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(
                        TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(168, M5GoldensScenario.FORD_Z), EXTRA, 10));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(
                        TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(216, M5GoldensScenario.FORD_Z), EXTRA, 10));
    }

    private static int bodies(Minecraft mc) {
        int n = 0;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Bodies whose feet and heads project inside the window. */
    private static int onScreen(Minecraft mc, RtsMode rts) {
        var v = rts.view();
        int n = 0;
        if (v == null || mc.level == null) {
            return 0;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity) {
                double[] feet = dev.strategos.core.client.pick.Picking.project(v, e.getX(), e.getY(), e.getZ());
                double[] head =
                        dev.strategos.core.client.pick.Picking.project(v, e.getX(), e.getBoundingBox().maxY, e.getZ());
                if (inside(v, feet) && inside(v, head)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean inside(dev.strategos.core.client.pick.View v, double @Nullable [] w) {
        return w != null && w[0] >= 0 && w[0] <= v.width() && w[1] >= 0 && w[1] <= v.height();
    }

    private boolean finish(Minecraft mc, ScenarioContext ctx) {
        JsonObject windows = new JsonObject();
        samples.forEach((name, s) -> windows.add(name, s.json()));
        ctx.result.add("windows", windows);
        var base = samples.get("base");
        var battle = samples.get("battle");
        var capped = samples.get("battleCapped");
        if (base == null || battle == null || capped == null) {
            return fail(ctx, "a window is missing: " + samples.keySet());
        }
        double units = battle.intervals.ms(0.50) - base.intervals.ms(0.50);
        double ours = battle.bars.ms(0.50) + battle.hudDraw.ms(0.50);
        ctx.result.addProperty("unitsMs", units);
        ctx.result.addProperty("oursMs", ours);
        ctx.result.addProperty("frameP50Ms", battle.intervals.ms(0.50));
        ctx.result.addProperty("frameP99Ms", battle.intervals.ms(0.99));
        ctx.result.addProperty("clientTickP50Ms", capped.ticks.ms(0.50));
        ctx.result.addProperty("clientTickP99Ms", capped.ticks.ms(0.99));
        ctx.result.addProperty("tickCap", RtsFrameScenario.TICK_CAP);
        ctx.result.addProperty("units", UNITS);
        ctx.result.addProperty("scene", "strategos:battle_at_the_ford");
        ctx.result.addProperty("renderDistance", RENDER_DISTANCE);
        ctx.result.addProperty("sodium", ModList.get().isLoaded("sodium"));
        ctx.result.addProperty("iris", ModList.get().isLoaded("iris"));
        ctx.result.addProperty("gl", GlUtil.getRenderer());
        ctx.result.addProperty(
                "window",
                mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " points, "
                        + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " pixels");
        ctx.result.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        ctx.result.addProperty("cores", Runtime.getRuntime().availableProcessors());
        check(battle.intervals.ms(0.50) <= RtsFrameScenario.FRAME_P50_MS, "frame p50 " + battle.intervals.ms(0.50));
        check(battle.intervals.ms(0.99) <= RtsFrameScenario.FRAME_P99_MS, "frame p99 " + battle.intervals.ms(0.99));
        check(units <= RtsFrameScenario.UNITS_MS, "units " + units + " ms");
        check(ours <= RtsFrameScenario.OURS_MS, "our drawing " + ours + " ms");
        check(
                capped.ticks.ms(0.50) <= RtsFrameScenario.CLIENT_TICK_MS,
                "client tick p50 " + capped.ticks.ms(0.50) + " ms");
        return done(ctx);
    }

    private void check(boolean ok, String what) {
        if (!ok) {
            failures.append(what).append("; ");
        }
    }

    private boolean fail(ScenarioContext ctx, String why) {
        failures.append(why);
        return done(ctx);
    }

    private boolean done(ScenarioContext ctx) {
        String f = failures.toString();
        if (!f.isEmpty()) {
            ctx.result.addProperty("failures", f);
        }
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
