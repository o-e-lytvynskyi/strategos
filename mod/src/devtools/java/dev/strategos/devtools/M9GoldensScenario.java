package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * M9's goldens (T19, TERR-03 acceptance 4): the territory borders, three static scenes on a flat world with no units,
 * the server frozen so a run draws the same frame as the last. Scene one ({@code m9_borders_meet}): two factions'
 * civic centres 60 blocks apart, their borders meeting along one line, the viewer's own in its colour and the enemy's
 * in its. Scene two ({@code m9_island_slope}): an own house 88 blocks from the centre, beyond its reach, holds an
 * island of tiles no root connects (the dashed border), and a ramp of one-block steps is built across the meeting line
 * so the border climbs a slope. Scene three ({@code m9_minimap}): the minimap with the borders over it. Each scene is
 * shot twice, ten ticks apart, the second as {@code <name>_2}. The borders are meshed by the time of a shot when the
 * client's territory holds the scene's owners and the section compiler and the minimap have stood still for
 * {@value #CALM_WORLD} ticks: a territory version dirties its sections, so a pending mesh keeps the compiler busy. Run
 * with {@code -Pcompat} for the Sodium set (the section geometry event is the same path; Sodium draws it its own
 * way). About {@value #MINUTES} minutes.
 */
final class M9GoldensScenario implements Scenario {
    static final int MINUTES = 3;

    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final FactionSlot OWN = new FactionSlot(0);
    private static final FactionSlot ENEMY = new FactionSlot(1);

    private static final int SIZE = 256;
    /** World X and Z of the two centres (60 blocks apart: the border is the line X = 0) and the island's house. */
    private static final int OWN_X = -30;

    private static final int ENEMY_X = 30;
    private static final int ISLAND_X = -30;
    private static final int ISLAND_Z = 88;
    /** The ramp: ten one-block steps two blocks deep along Z from {@value #RAMP_Z0}, across the border, 24 wide. */
    private static final int RAMP_Z0 = 26;

    private static final int RAMP_STEPS = 10;
    private static final int RAMP_HALF_WIDTH = 12;

    /** Ticks for a pose to settle: the rig eases 35 % of the way a tick. */
    private static final int SETTLE = 50;
    /** Ticks after a freeze for the last packets and the chunk sections. */
    private static final int ARRIVE = 20;
    /** Ticks between a scene's two takes. */
    private static final int BETWEEN = 10;

    private static final int LIMIT = 600;
    /** Consecutive ticks the section compiler must be idle before a shot, and the world unchanged before a freeze. */
    private static final int CALM_RENDER = 20;

    private static final int CALM_WORLD = 60;
    /** Server steps the block writer's queue must stay empty before the freeze. */
    private static final int IDLE_STEPS = 8;

    /** One step of a scene: true once it is done; it runs once a client tick, {@link #inStep} ticks in. */
    @FunctionalInterface
    private interface Step {
        boolean run(Minecraft mc, ScenarioContext ctx);
    }

    private final StringBuilder failures = new StringBuilder();
    private volatile @Nullable MatchRuntime match;
    private volatile int floor;
    /** Set by the client: freeze the server once the writer's queue has stayed empty for a few steps. */
    private volatile boolean freezeOnIdle;

    private volatile boolean frozenIdle;
    private int idleSteps;

    private boolean abort;
    private List<Step> steps = List.of();
    private int at;
    private int inStep;
    private int calm;
    private int lastHash;

    @Override
    public String name() {
        return "m9_goldens";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        floor = y;
        // revealed, so no shroud: the borders are the scene
        PlayRules play = new PlayRules(PlayRules.Victory.NONE, true, false, false);
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, y, 0), SIZE, SIZE),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), y, null, play));
        match = rt;
        rt.addListener(out -> onStep(server));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(0.5, y + CameraRig.DEFAULT_HEIGHT, -back, 0, 55);
    }

    // ---- the server's side ----

    /** On the server thread after each step: the freeze once the block writer is done. */
    private void onStep(IntegratedServer server) {
        if (freezeOnIdle) {
            idleSteps = StrategosServer.get().writer().pending(server.overworld()) == 0 ? idleSteps + 1 : 0;
            if (idleSteps >= IDLE_STEPS) { // longer than a site's 4-tick burst interval
                freezeOnIdle = false;
                ScenarioWorld.run(server, "tick freeze");
                frozenIdle = true;
            }
        }
    }

    /** A finished building of {@code template} for {@code faction}, its middle at world (x, z). */
    private static void place(MatchRuntime rt, TemplateId template, FactionSlot faction, double x, double z) {
        TemplateSet templates = StrategosServer.get().templates().set();
        UnitTemplate t = Objects.requireNonNull(templates.byId(template), template.value());
        Blueprint bp = Objects.requireNonNull(templates.blueprint(t.netIndex()), template.value());
        int cornerX = (int) Math.floor(rt.space().localX(x)) - bp.sizeX() / 2;
        int cornerZ = (int) Math.floor(rt.space().localZ(z)) - bp.sizeZ() / 2;
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, faction, cornerX, cornerZ, 0));
    }

    // ---- the client's side: the scenes as steps ----

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        // the cursor rests mid-window, as a player's does: at an edge it would pan the camera
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
            StrategosClientConfig.INSTANCE.seenHints.set(List.of("first_order", "idle_worker", "build_house"));
            steps = scenes();
            at = 0;
            inStep = 0;
        }
        if (tick >= 20 && tick % 20 == 0 && !rts.active() && StrategosClient.match() != null) {
            rts.enter();
        }
        if (ctx.captureName != null) {
            return false; // the shot comes first
        }
        if (abort || tick > 30_000) {
            return fail(ctx, "stuck at step " + at);
        }
        if (at >= steps.size()) {
            return done(ctx);
        }
        inStep++;
        if (steps.get(at).run(mc, ctx)) {
            at++;
            inStep = 0;
        }
        return false;
    }

    private List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(until("the world", LIMIT, mc -> StrategosClient.match() != null && rendered(mc)));
        s.add(until("RTS mode", LIMIT, mc -> {
            RtsMode rts = StrategosClient.rts();
            return rts.active() && rts.view() != null && rts.rig() != null;
        }));
        // the two centres, and an own house beyond its centre's reach
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            place(rt, CIVIC_CENTRE, OWN, OWN_X, 0);
            place(rt, CIVIC_CENTRE, ENEMY, ENEMY_X, 0);
            place(rt, HOUSE, OWN, ISLAND_X, ISLAND_Z);
        })));
        s.add(until("the territory with both centres and the island", LIMIT, mc -> territoryHeld()));
        freeze(s);
        // scene one: the borders meeting
        s.add(act((mc, ctx) -> frame(0, 5, CameraRig.MAX_HEIGHT)));
        s.add(wait(SETTLE));
        shoot(s, "m9_borders_meet");
        // scene two: the ramp across the meeting line, the island in the same frame
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            for (int k = 0; k < RAMP_STEPS; k++) {
                int z0 = RAMP_Z0 + 2 * k;
                ScenarioWorld.run(
                        server,
                        "fill " + -RAMP_HALF_WIDTH + " " + floor + " " + z0 + " " + RAMP_HALF_WIDTH + " " + (floor + k)
                                + " " + (z0 + 1) + " stone");
            }
            ScenarioWorld.run(server, "tick unfreeze");
        })));
        freeze(s);
        s.add(act((mc, ctx) -> frame(-15, 50, CameraRig.MAX_HEIGHT)));
        s.add(wait(SETTLE));
        shoot(s, "m9_island_slope");
        // scene three: the minimap with the borders
        s.add(act((mc, ctx) -> frame(0, 5, 24)));
        s.add(wait(SETTLE));
        s.add(until("the minimap", LIMIT, mc -> StrategosClient.hud().minimapPixel(SIZE / 2, SIZE / 2) != 0));
        shoot(s, "m9_minimap");
        return s;
    }

    // ---- step builders ----

    private Step wait(int ticks) {
        return (mc, ctx) -> inStep >= ticks;
    }

    private static Step act(BiConsumer<Minecraft, ScenarioContext> action) {
        return (mc, ctx) -> {
            action.accept(mc, ctx);
            return true;
        };
    }

    private Step until(String what, int limit, Predicate<Minecraft> done) {
        return (mc, ctx) -> {
            if (done.test(mc)) {
                return true;
            }
            if (inStep > limit) {
                failures.append("no ")
                        .append(what)
                        .append(" within ")
                        .append(limit)
                        .append(" ticks; ");
                abort = true;
            }
            return false;
        };
    }

    /** A shot of the scene as it stands, and a second {@value #BETWEEN} ticks later as {@code <name>_2}. */
    private void shoot(List<Step> s, String name) {
        s.add(act((mc, ctx) -> calm = 0));
        s.add(until("the level renderer to be done for " + name, LIMIT, mc -> {
            calm = mc.levelRenderer.hasRenderedAllSections() ? calm + 1 : 0;
            return calm >= CALM_RENDER;
        }));
        s.add(act((mc, ctx) -> {
            check(territoryHeld(), "the territory changed before the shot of " + name);
            ctx.capture(name);
        }));
        s.add(wait(BETWEEN));
        s.add(act((mc, ctx) -> ctx.capture(name + "_2")));
    }

    /**
     * Freezes the server as soon as the block writer has written every building, then waits for the client to settle:
     * every building finished, the section compiler idle and the minimap's image unchanged for {@value #CALM_WORLD}
     * ticks (a territory version dirties the sections its tiles touch, so the borders' meshes are done by then).
     */
    private void freeze(List<Step> s) {
        s.add(act((mc, ctx) -> {
            idleSteps = 0;
            frozenIdle = false;
            freezeOnIdle = true;
        }));
        s.add(until("the writer idle and the server frozen", 2 * LIMIT, mc -> frozenIdle));
        s.add(act((mc, ctx) -> {
            calm = 0;
            lastHash = 0;
        }));
        s.add(until("the client to settle", 2 * LIMIT, mc -> {
            int hash = minimapHash();
            boolean still = hash == lastHash
                    && mc.levelRenderer.hasRenderedAllSections()
                    && allBuilt()
                    && territoryHeld()
                    && StrategosClient.hud().minimapPixel(1, 1) != 0;
            lastHash = hash;
            calm = still ? calm + 1 : 0;
            return calm >= CALM_WORLD;
        }));
        s.add(wait(ARRIVE));
    }

    private static boolean allBuilt() {
        for (BuildingState.Entry e : StrategosClient.model().buildings()) {
            if (e.state() != CommandCard.COMPLETE) {
                return false;
            }
        }
        return true;
    }

    /** A hash of the minimap's image, sampled every fourth column. */
    private static int minimapHash() {
        MatchJoin join = StrategosClient.match();
        int h = 1;
        if (join != null) {
            for (int z = 0; z < join.sizeZ(); z += 4) {
                for (int x = 0; x < join.sizeX(); x += 4) {
                    h = 31 * h + StrategosClient.hud().minimapPixel(x, z);
                }
            }
        }
        return h;
    }

    // ---- what the steps do ----

    /**
     * Whether the client's territory is the scene's: each centre's ground its owner's and connected, the border's two
     * sides the two owners, the island's ground the viewer's and not connected (the dashed border).
     */
    private static boolean territoryHeld() {
        MatchJoin join = StrategosClient.match();
        TerritoryGrid grid = StrategosClient.model().territory().grid();
        if (join == null || grid == null) {
            return false;
        }
        int cx = join.sizeX() / 2;
        int cz = join.sizeZ() / 2;
        return grid.ownerAt(cx + OWN_X, cz) == OWN.value()
                && grid.connectedAt(cx + OWN_X, cz)
                && grid.ownerAt(cx + ENEMY_X, cz) == ENEMY.value()
                && grid.connectedAt(cx + ENEMY_X, cz)
                && grid.ownerAt(cx - 6, cz) == OWN.value()
                && grid.ownerAt(cx + 6, cz) == ENEMY.value()
                && grid.ownerAt(cx + ISLAND_X, cz + ISLAND_Z) == OWN.value()
                && !grid.connectedAt(cx + ISLAND_X, cz + ISLAND_Z);
    }

    /** Runs {@code work} on the server thread with the match, once it stands. */
    private void onServer(Minecraft mc, BiConsumer<IntegratedServer, MatchRuntime> work) {
        IntegratedServer server = mc.getSingleplayerServer();
        MatchRuntime rt = match;
        if (server == null || rt == null) {
            failures.append("no server or match to work on; ");
            abort = true;
            return;
        }
        server.execute(() -> work.accept(server, rt));
    }

    private static boolean rendered(Minecraft mc) {
        return mc.level != null && mc.levelRenderer.hasRenderedAllSections();
    }

    /** The camera on world (x, z) at {@code height}. */
    private static void frame(double x, double z, double height) {
        RtsMode rts = StrategosClient.rts();
        var rig = Objects.requireNonNull(rts.rig());
        rts.centreOn(x, z);
        rig.zoomTo(height);
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
        ctx.result.addProperty("step", at);
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
