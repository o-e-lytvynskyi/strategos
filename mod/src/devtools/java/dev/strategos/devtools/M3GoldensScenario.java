package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
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
import org.lwjgl.glfw.GLFW;

/**
 * M3's goldens (T30): one pinned first-loop scene, a civic centre, two workers and an oak, whose world changes only
 * through commands the server sends at fixed core ticks, and which freezes ({@code /tick freeze}) at core ticks a
 * condition picks, so every run frames the same state. Frozen, the client selects, arms placement and presses keys,
 * which change nothing on the server. Frames: the centre selected with its rally point ({@code select_civic_centre},
 * UI-06, PROD-03.3), the house's ghost on free ground and over the centre ({@code placement_valid}, {@code
 * placement_invalid}, BLD-02.4), the idle worker taken by its key ({@code idle_worker}, UI-10), the woodcutter on its
 * way home with its axe and log ({@code worker_tool_and_load}, ECO-05.9) and selected ({@code select_worker}), the
 * oak's crack close up ({@code log_crack}, AUD-04.3), the alert of an enemy's blow ({@code attack_alert}, UI-11 and
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

    private final StringBuilder failures = new StringBuilder();
    private int floor;
    /** The server's stage: an even one runs, an odd one is frozen for the client's frames. */
    private final AtomicInteger serverStage = new AtomicInteger();

    private volatile long frozenAt = -1;
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
        for (int y = 0; y < 4; y++) { // an oak, before the match captures its pages
            level.setBlockAndUpdate(new BlockPos(7, floor + y, 16), Blocks.OAK_LOG.defaultBlockState());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 3; y <= 4; y++) {
                    BlockPos at = new BlockPos(7 + dx, floor + y, 16 + dz);
                    if (level.getBlockState(at).isAir()) {
                        level.setBlockAndUpdate(
                                at,
                                Blocks.OAK_LEAVES
                                        .defaultBlockState()
                                        .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                    }
                }
            }
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
                if (own.length > 0 && q.carriedAmount(own[0]) >= 10) { // the first load, on its way home
                    freeze(server, tick);
                }
            }
            case 4 -> {
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
            case 6 -> {
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
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            mc.options.renderDistance().set(12);
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
                case 3 -> woodcutter(mc, ctx, rts, screen);
                case 5 -> alert(mc, ctx);
                case 7 -> end(mc, ctx);
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
                    ctx.capture("select_civic_centre");
                    next();
                }
            }
            case 2 -> {
                rts.selection().clear();
                rts.armPlacement(
                        Objects.requireNonNull(StrategosClient.templates().byId(HOUSE))
                                .netIndex());
                double[] at = gui(screen, rts, 2.5, floor, 4.5);
                screen.mouseMoved(at[0], at[1]);
                next();
            }
            case 3 -> {
                if (inPhase >= 25) { // the ghost's verdict, recomputed on a move
                    ctx.capture("placement_valid");
                    double[] at = gui(screen, rts, -8, floor + 1, 27);
                    screen.mouseMoved(at[0], at[1]);
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
                double[] middle = gui(screen, rts, 0.5, floor, 18);
                screen.mouseMoved(middle[0], middle[1]);
                screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
                check(
                        rts.selection().handles().size() == 1,
                        "the idle key took " + rts.selection().handles());
                next();
            }
            case 6 -> {
                if (inPhase >= 3) {
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

    /** Frozen with the first load: the woodcutter on its way home, selected, and the oak's crack close up. */
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
            case 3 -> {
                rts.selection().clear();
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 6); // in, toward the oak
                next();
            }
            case 4 -> {
                if (inPhase >= SETTLE && mc.levelRenderer.hasRenderedAllSections()) {
                    ctx.capture("log_crack");
                    next();
                }
            }
            case 5 -> {
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -6);
                next();
            }
            default -> {
                if (inPhase >= SETTLE) {
                    ctx.result.addProperty("woodcutter", woodcutter);
                    resume(mc);
                }
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
