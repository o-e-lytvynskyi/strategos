package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Resign;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.BlockCracked;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchEnded;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnderAttack;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * M3's goldens (T30): one pinned first-loop scene, a civic centre, two workers and an oak, whose world changes only
 * through commands the server sends at fixed core ticks, and which freezes ({@code /tick freeze}) at core ticks a
 * condition picks, so every run frames the same state. Frozen, the client selects, arms placement and presses keys,
 * which change nothing on the server. Frames: the centre selected with its rally point ({@code select_civic_centre},
 * UI-06, PROD-03.3), the house's ghost on free ground and over the centre ({@code placement_valid}, {@code
 * placement_invalid}, BLD-02.4), the idle worker taken by its key ({@code idle_worker}, UI-10), the oak's crack close
 * up ({@code log_crack}, AUD-04.3), the woodcutter on its way home with its axe and log ({@code worker_tool_and_load},
 * ECO-05.9) and selected ({@code select_worker}), the alert of an enemy's blow ({@code attack_alert}, UI-11 and
 * UI-12) and the end screen ({@code end_screen}, MATCH-01.6). Every frame shows the resource bar (UI-07) and the
 * minimap.
 */
final class M3GoldensScenario implements Scenario {
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    /** Ticks for a pose to settle: the rig eases 35 % of the way a tick. */
    private static final int SETTLE = 50;
    /** Ticks after a freeze for the last packets and the chunk sections. */
    private static final int ARRIVE = 20;
    /** Core ticks: the woodcutter's order and the rally point, then the first freeze. */
    private static final long ORDERS_AT = 20;

    private static final long FIRST_FREEZE = 40;
    /** The crack stage the oak's log is framed at. */
    private static final int CRACK = 3;
    /** How far from the oak the woodcutter with its first load is framed. */
    private static final double AWAY = 3;
    /** Ticks of a held turn key for a quarter turn: 4.5° a tick. */
    private static final int QUARTER = 20;

    private static final double TREE_X = 7.5;
    private static final double TREE_Z = 16.5;

    private final StringBuilder failures = new StringBuilder();
    private int floor;
    /** The server's stage: an even one runs, an odd one is frozen for the client's frames. */
    private final AtomicInteger serverStage = new AtomicInteger();

    private volatile long frozenAt = -1;
    /** Where the cursor rests, in window pixels; the window's middle when null. */
    private double @Nullable [] cursor;

    private int clientStage = 1;
    private int phase;
    private int inPhase;
    private int woodcutter = -1;

    @Override
    public String name() {
        return "m3_goldens";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        for (int y = 0;
                y < 4;
                y++) { // a bare oak trunk, before the match captures its pages; leaves would hide its crack
            level.setBlockAndUpdate(new BlockPos(7, floor + y, 16), Blocks.OAK_LOG.defaultBlockState());
        }
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 128, 128),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        var space = rt.space();
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(
                        CIVIC_CENTRE, new FactionSlot(0), (int) space.localX(-13), (int) space.localZ(22), 0));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 300));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 300));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(
                        TemplateSet.WORKER,
                        new FactionSlot(0),
                        new Vec2d(space.localX(1.5), space.localZ(12.5)),
                        2,
                        1));
        rt.addListener(out -> server(server, rt, out));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(0.5, floor + 24, 18 - back, 0, 55); // RTS mode's focus: (0.5, 18)
    }

    /** The server's side, after each step: the orders at their ticks, and each freeze at its condition's first tick. */
    private void server(IntegratedServer server, MatchRuntime rt, TickOutputs out) {
        MatchCore core = rt.core();
        if (core == null) {
            return;
        }
        MatchQueries q = core.queries();
        long tick = out.tick();
        int[] own = q.unitHandlesOf(new FactionSlot(0));
        switch (serverStage.get()) {
            case 0 -> {
                if (tick == ORDERS_AT && own.length == 2 && !q.buildings().isEmpty()) {
                    NodeView tree = q.nodes().stream()
                            .filter(n -> n.resource().equals("wood"))
                            .findFirst()
                            .orElse(null);
                    if (tree == null) {
                        failures.append("no tree node; ");
                    } else {
                        rt.submit(
                                new PlayerSlot(0),
                                Gather.replace(UnitSet.of(own[0]), tree.topX(), tree.topY(), tree.topZ()));
                    }
                    var space = rt.space();
                    rt.submit(
                            new PlayerSlot(0),
                            new SetRally(
                                    UnitSet.of(q.buildings().get(0).handle()),
                                    (int) space.localX(-8),
                                    floor - rt.space().origin().getY(),
                                    (int) space.localZ(12)));
                } else if (tick >= FIRST_FREEZE) {
                    freeze(server, tick);
                }
            }
            case 2 -> {
                for (OutputEvent e : out.events()) {
                    if (e instanceof BlockCracked c && c.stage() >= CRACK) { // the oak's log, a third chopped
                        freeze(server, tick);
                        break;
                    }
                }
            }
            case 4 -> {
                UnitView cutter = own.length > 0 ? q.unit(own[0]) : null;
                if (cutter != null
                        && q.carriedAmount(own[0]) >= 10
                        && Math.hypot(
                                        cutter.x() - rt.space().localX(TREE_X),
                                        cutter.z() - rt.space().localZ(TREE_Z))
                                > AWAY) {
                    freeze(server, tick); // the first load, on its way home
                }
            }
            case 6 -> {
                if (frozenAt >= 0 && tick == frozenAt + 1 && own.length > 1) {
                    UnitView worker = q.unit(own[1]);
                    if (worker != null) {
                        rt.submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(
                                        TemplateSet.INFANTRY,
                                        new FactionSlot(1),
                                        new Vec2d(worker.x() + 3, worker.z()),
                                        1,
                                        0));
                    }
                } else if (frozenAt >= 0 && tick == frozenAt + 2) {
                    int[] enemies = q.unitHandlesOf(new FactionSlot(1));
                    if (enemies.length == 1 && own.length > 1) {
                        rt.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(enemies[0]), own[1]));
                    }
                }
                for (OutputEvent e : out.events()) {
                    if (e instanceof UnderAttack a && a.faction().value() == 0) {
                        freeze(server, tick);
                        break;
                    }
                }
            }
            case 8 -> {
                if (frozenAt >= 0 && tick == frozenAt + 1) {
                    int[] enemies = q.unitHandlesOf(new FactionSlot(1));
                    if (enemies.length > 0) {
                        rt.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Stop(UnitSet.of(enemies)));
                    }
                    rt.submit(PlayerSlot.ADMIN, new RuleChange("victory", "civic_centre"));
                    rt.submit(PlayerSlot.ADMIN, new Resign(new FactionSlot(1)));
                }
                for (OutputEvent e : out.events()) {
                    if (e instanceof MatchEnded) {
                        freeze(server, tick);
                        break;
                    }
                }
            }
            default -> {}
        }
    }

    private void freeze(IntegratedServer server, long tick) {
        ScenarioWorld.run(server, "tick freeze");
        frozenAt = tick;
        serverStage.incrementAndGet();
    }

    /** From the client: the next running stage, the game unfrozen. */
    private void resume(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                serverStage.incrementAndGet();
                ScenarioWorld.run(server, "tick unfreeze");
            });
        }
        clientStage += 2;
        phase = 0;
        inPhase = 0;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        double[] at = cursor;
        if (at == null) { // mid-window: at an edge it would pan the camera
            GLFW.glfwSetCursorPos(
                    mc.getWindow().getWindow(),
                    mc.getWindow().getScreenWidth() / 2.0,
                    mc.getWindow().getScreenHeight() / 2.0);
        } else { // only the handler's fields: a GLFW move would come back as a move event to the middle
            mouseAt(mc, at[0], at[1]);
        }
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
        }
        if (tick == 20) {
            rts.enter();
        }
        Screen screen = mc.screen;
        if (tick < 40 || screen == null || !rts.active() || rts.view() == null) {
            return tick > 600 && fail(ctx, "RTS mode is not on");
        }
        if (tick > 6_000) {
            return fail(ctx, "stuck in stage " + clientStage + " phase " + phase);
        }
        if (ctx.captureName != null || serverStage.get() != clientStage) {
            return false; // the shot, or the server's freeze, comes first
        }
        inPhase++;
        try {
            return switch (clientStage) {
                case 1 -> first(mc, ctx, rts, screen);
                case 3 -> crack(mc, ctx, rts, screen);
                case 5 -> woodcutter(mc, ctx, rts, screen);
                case 7 -> alert(mc, ctx);
                case 9 -> end(mc, ctx);
                default -> fail(ctx, "client stage " + clientStage);
            };
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    /** Frozen at tick 40: the centre and its rally point, the house's ghost twice, the idle worker. */
    private boolean first(Minecraft mc, ScenarioContext ctx, RtsMode rts, Screen screen) {
        switch (phase) {
            case 0 -> {
                if (inPhase >= ARRIVE
                        && RtsGoldensScenario.bodies(mc) == 2
                        && mc.levelRenderer.hasRenderedAllSections()) {
                    RtsGoldensScenario.pinBodies(mc);
                    var join = Objects.requireNonNull(StrategosClient.match());
                    var centre = StrategosClient.model().buildings().get(0);
                    click(
                            screen,
                            gui(
                                    screen,
                                    rts,
                                    join.originX() + centre.x() + 5.5,
                                    centre.baseY() + 3,
                                    join.originZ() + centre.z()));
                    check(rts.selection().building() != null, "the click selected no building");
                    next();
                }
            }
            case 1 -> {
                if (inPhase >= 3) {
                    check(rts.rallyShown() != null, "no rally marker for the selected centre");
                    ctx.capture("select_civic_centre");
                    next();
                }
            }
            case 2 -> {
                rts.selection().clear();
                rts.armPlacement(
                        Objects.requireNonNull(StrategosClient.templates().byId(HOUSE))
                                .netIndex());
                point(mc, screen, gui(screen, rts, 2.5, floor, 20)); // free ground between the oak and the centre
                next();
            }
            case 3 -> {
                if (inPhase >= 25) { // the ghost's verdict, recomputed on a move
                    ctx.capture("placement_valid");
                    point(mc, screen, gui(screen, rts, -8, floor + 1, 27));
                    next();
                }
            }
            case 4 -> {
                if (inPhase >= 25) {
                    ctx.capture("placement_invalid");
                    next();
                }
            }
            case 5 -> {
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                check(rts.placementTemplate() == -1, "Escape left placement armed");
                cursor = null;
                mouseAt(
                        mc,
                        mc.getWindow().getScreenWidth() / 2.0,
                        mc.getWindow().getScreenHeight() / 2.0);
                screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
                check(
                        rts.selection().handles().size() == 1,
                        "the idle key took " + rts.selection().handles());
                next();
            }
            case 6 -> {
                if (inPhase >= SETTLE) { // the key centres the camera on the worker
                    ctx.capture("idle_worker");
                    next();
                }
            }
            default -> {
                rts.selection().clear();
                resume(mc);
            }
        }
        return false;
    }

    /** Frozen at the oak's crack: the camera on the oak, a quarter turn round and close in, then back. */
    private boolean crack(Minecraft mc, ScenarioContext ctx, RtsMode rts, Screen screen) {
        switch (phase) {
            case 0 -> {
                if (inPhase >= ARRIVE) {
                    RtsGoldensScenario.pinBodies(mc);
                    rts.centreOn(TREE_X, TREE_Z);
                    screen.keyPressed(KeyCode.PERIOD, 0, 0);
                    next();
                }
            }
            case 1 -> {
                if (inPhase >= QUARTER) {
                    screen.keyReleased(KeyCode.PERIOD, 0, 0);
                    screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 6); // in
                    next();
                }
            }
            case 2 -> {
                if (inPhase >= SETTLE && mc.levelRenderer.hasRenderedAllSections()) {
                    ctx.capture("log_crack");
                    next();
                }
            }
            case 3 -> {
                screen.keyPressed(KeyCode.COMMA, 0, 0);
                next();
            }
            case 4 -> {
                if (inPhase >= QUARTER) {
                    screen.keyReleased(KeyCode.COMMA, 0, 0);
                    screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -6);
                    rts.centreOn(0.5, 18);
                    next();
                }
            }
            default -> {
                if (inPhase >= SETTLE) {
                    resume(mc);
                }
            }
        }
        return false;
    }

    /**
     * Where vanilla's mouse handler thinks the cursor is, which placement's ghost follows: moving the GLFW cursor from
     * code raises no move event, so its fields are set as {@code MouseHandler#onMove} would.
     */
    private static void mouseAt(Minecraft mc, double x, double y) {
        try {
            var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(mc.mouseHandler, x);
            ypos.setDouble(mc.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot move the mouse handler's cursor", e);
        }
    }

    /** Points the cursor at {@code gui}, GUI pixels, for the next ticks, and tells the screen it moved there. */
    private void point(Minecraft mc, Screen screen, double[] gui) {
        double scale = mc.getWindow().getScreenWidth() / (double) screen.width;
        cursor = new double[] {gui[0] * scale, gui[1] * scale};
        screen.mouseMoved(gui[0], gui[1]);
    }

    /** Frozen once the first load is on its way home: the woodcutter, then selected. */
    private boolean woodcutter(Minecraft mc, ScenarioContext ctx, RtsMode rts, Screen screen) {
        switch (phase) {
            case 0 -> {
                if (inPhase >= ARRIVE && mc.levelRenderer.hasRenderedAllSections()) {
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("worker_tool_and_load");
                    next();
                }
            }
            case 1 -> {
                double[] at = woodcutterOnScreen(mc, screen, rts);
                click(screen, at);
                check(
                        rts.selection().handles().size() == 1,
                        "the woodcutter click took " + rts.selection().handles());
                woodcutter = rts.selection().handles().isEmpty()
                        ? -1
                        : rts.selection().handles().get(0);
                next();
            }
            case 2 -> {
                if (inPhase >= 15) { // its unit_detail arrives
                    ctx.capture("select_worker");
                    next();
                }
            }
            default -> {
                rts.selection().clear();
                ctx.result.addProperty("woodcutter", woodcutter);
                resume(mc);
            }
        }
        return false;
    }

    /** Frozen at the enemy's first blow: the alert, framed on a lit beat of the minimap's ring. */
    private boolean alert(Minecraft mc, ScenarioContext ctx) {
        var hud = StrategosClient.hud();
        switch (phase) {
            case 0 -> {
                if (inPhase >= ARRIVE && hud.alertAge() >= 10 && hud.alertAge() < 100 && hud.clientTicks() % 10 == 0) {
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("attack_alert");
                    next();
                } else if (inPhase > 200) {
                    fail(ctx, "no alert heard: age " + hud.alertAge());
                }
            }
            default -> resume(mc);
        }
        return false;
    }

    /** Frozen at the end: the victory screen. */
    private boolean end(Minecraft mc, ScenarioContext ctx) {
        switch (phase) {
            case 0 -> {
                var end = StrategosClient.model().end();
                if (inPhase >= ARRIVE && end != null) {
                    check(end.faction() == 0, "the winner the client heard: " + end.faction());
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("end_screen");
                    next();
                } else if (inPhase > 200) {
                    return fail(ctx, "no end heard");
                }
            }
            case 1 -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> ScenarioWorld.run(server, "tick unfreeze"));
                }
                next();
            }
            default -> {
                return done(ctx);
            }
        }
        return false;
    }

    private void next() {
        phase++;
        inPhase = 0;
    }

    private static void click(Screen screen, double[] at) {
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
    }

    /** The first worker's chest, the one sent to the oak, in GUI pixels. */
    private static double[] woodcutterOnScreen(Minecraft mc, Screen screen, RtsMode rts) {
        View v = rts.view();
        if (v == null || mc.level == null) {
            throw new IllegalStateException("no view");
        }
        UnitEntity first = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && body.spawnData().faction() == 0
                    && !body.getMainHandItem().isEmpty()) {
                first = body;
            }
        }
        if (first == null) {
            throw new IllegalStateException("no worker holds a tool");
        }
        double[] w = Picking.project(v, first.getX(), first.getY() + 0.9, first.getZ());
        if (w == null) {
            throw new IllegalStateException("the woodcutter is off screen");
        }
        return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
    }

    private static double[] gui(Screen screen, RtsMode rts, double x, double y, double z) {
        View v = rts.view();
        double[] w = v == null ? null : Picking.project(v, x, y, z);
        if (v == null || w == null) {
            throw new IllegalStateException("cannot project " + x + ", " + y + ", " + z);
        }
        return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
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
        ctx.result.addProperty("stage", clientStage);
        ctx.result.addProperty("phase", phase);
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
