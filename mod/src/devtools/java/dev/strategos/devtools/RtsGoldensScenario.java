package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
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
 * The RTS view's goldens (QA-18, CAM-02.3, UNIT-03.3): a fixed scene of a few blocks and 12 own and 8 enemy units,
 * the game frozen once they stand ({@code /tick freeze}, so bodies draw at a fixed partial tick), driven only through
 * {@code RtsScreen}'s keys, wheel and mouse (QA-18.3). Frames: the default pose, the own units box-selected and
 * outlined, a quarter turn right and zoomed in, and a quarter turn back at the top height.
 */
final class RtsGoldensScenario implements Scenario {
    static final int OWN = 12;
    static final int ENEMIES = 8;
    /** Ticks for a pose to settle: the rig eases 35 % of the way a tick. */
    private static final int SETTLE = 50;
    /** Far enough that the top pose (64 blocks up) sees ground to the window's corners. */
    private static final int RENDER_DISTANCE = 12;

    private final StringBuilder failures = new StringBuilder();
    private int floor;
    private int phase;
    private int inPhase;

    @Override
    public String name() {
        return "rts_goldens";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        floor = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        scene(server);
    }

    /**
     * The goldens' scene, shared with the overlay's ({@link OverlayGoldensScenario}): noon, no weather, the blocks, a
     * 128² match with the two groups, and the player seated in slot 0 as its commander, its RTS focus at (0.5, 18).
     */
    static MatchRuntime scene(IntegratedServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        // something to judge a pose by: a raised stone pad, a wall and two posts
        for (int x = -3; x <= 3; x++) {
            for (int z = 20; z <= 26; z++) {
                level.setBlockAndUpdate(new BlockPos(x, floor, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = -10; x <= -4; x++) {
            level.setBlockAndUpdate(new BlockPos(x, floor, 12), Blocks.COBBLESTONE_WALL.defaultBlockState());
        }
        for (int y = 0; y < 4; y++) {
            level.setBlockAndUpdate(new BlockPos(8, floor + y, 10), Blocks.OAK_LOG.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(8, floor + y, 24), Blocks.OAK_LOG.defaultBlockState());
        }
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 128, 128),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(60, 80), OWN, 3));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(70, 84), ENEMIES, 3));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(0.5, floor + 24, 18 - back, 0, 55); // RTS mode's focus: (0.5, 18)
        return rt;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        // the cursor rests mid-window: at an edge it would pan the camera
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            mc.options.renderDistance().set(RENDER_DISTANCE);
        }
        if (tick == 20) {
            rts.enter();
        }
        Screen screen = mc.screen;
        if (tick < 40 || screen == null || !rts.active() || rts.view() == null) {
            return tick > 600 && fail(ctx, "RTS mode is not on");
        }
        if (ctx.captureName != null) {
            return false; // a slow renderer (llvmpipe) takes the shot frames later; nothing moves until it is taken
        }
        inPhase++;
        switch (phase) {
            case 0 -> { // the bodies arrive and stand, then the game freezes
                if (bodies(mc) == OWN + ENEMIES && inPhase >= 100 && mc.levelRenderer.hasRenderedAllSections()) {
                    IntegratedServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> ScenarioWorld.run(server, "tick freeze"));
                    }
                    next();
                } else if (inPhase > 600) {
                    return fail(ctx, bodies(mc) + " bodies arrived");
                }
            }
            case 1 -> {
                if (inPhase >= 20) {
                    pinBodies(mc);
                    ctx.capture("pose_default");
                    next();
                }
            }
            case 2 -> {
                screen.mouseClicked(1, 1, 0);
                screen.mouseMoved(screen.width - 2, screen.height - 2);
                screen.mouseReleased(screen.width - 2, screen.height - 2, 0);
                int selected = rts.selection().handles().size();
                ctx.result.addProperty("selected", selected);
                check(selected == OWN, "the drag selected " + selected);
                screen.keyPressed(KeyCode.digit(1).code(), 0, 2); // Ctrl+1: the group bar shows group 1
                next();
            }
            case 3 -> {
                if (inPhase >= 5) {
                    ctx.capture("outline"); // with the HUD's selection panel and group bar (UI-02.3)
                    next();
                }
            }
            case 4 -> {
                double[] enemy = enemyOnScreen(mc, rts);
                screen.mouseMoved(
                        enemy[0] * screen.width / mc.getWindow().getScreenWidth(),
                        enemy[1] * screen.height / mc.getWindow().getScreenHeight()); // the HUD's tooltip names it
                next();
            }
            case 5 -> {
                if (inPhase >= 2) {
                    ctx.capture("hud_tooltip");
                    next();
                }
            }
            case 6 -> {
                double[] empty = gui(screen, rts, 0.5, floor, 4.5);
                screen.mouseClicked(empty[0], empty[1], 0);
                screen.mouseReleased(empty[0], empty[1], 0);
                check(
                        rts.selection().handles().isEmpty(),
                        "an empty click left " + rts.selection().handles().size());
                screen.keyPressed(KeyCode.PERIOD, 0, 0); // a quarter turn right: 20 ticks at 4.5° a tick
                next();
            }
            case 7 -> {
                if (inPhase >= 20) {
                    screen.keyReleased(KeyCode.PERIOD, 0, 0);
                    screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 6); // in, to 10.4 blocks up
                    next();
                }
            }
            case 8 -> {
                if (inPhase >= SETTLE && mc.levelRenderer.hasRenderedAllSections()) {
                    ctx.result.addProperty("closeHeight", rig(rts).height());
                    ctx.capture("pose_turned_close");
                    screen.keyPressed(KeyCode.COMMA, 0, 0);
                    next();
                }
            }
            case 9 -> {
                if (inPhase >= 20) {
                    screen.keyReleased(KeyCode.COMMA, 0, 0);
                    screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -20); // out, to the top
                    next();
                }
            }
            case 10 -> {
                if (inPhase >= SETTLE && mc.levelRenderer.hasRenderedAllSections()) {
                    ctx.result.addProperty("highHeight", rig(rts).height());
                    check(Math.abs(rig(rts).height() - CameraRig.MAX_HEIGHT) < 0.01, "the top is " + rig(rts).height());
                    ctx.capture("pose_high");
                    next();
                }
            }
            case 11 -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> ScenarioWorld.run(server, "tick unfreeze"));
                }
                return done(ctx);
            }
            default -> {
                return done(ctx);
            }
        }
        return false;
    }

    /**
     * Frozen bodies still draw their idle arm swing from their age, which differs by the ticks each lived on this
     * client before the freeze, and their legs from the last walk speed; a body frozen mid-step of its interpolation
     * stands short of where the server put it. All pinned, so every run draws one pose: age 0, no walk, at its target,
     * body and head turned as the server last said.
     */
    static void pinBodies(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body) {
                body.tickCount = 0;
                body.walkAnimation.update(0, 1);
                body.walkAnimation.update(0, 1);
                float yaw = body.lerpTargetYRot();
                body.setPos(body.lerpTargetX(), body.lerpTargetY(), body.lerpTargetZ());
                body.setYRot(yaw);
                body.yBodyRot = yaw;
                body.yHeadRot = yaw;
                body.setOldPosAndRot();
                body.yBodyRotO = yaw;
                body.yHeadRotO = yaw;
            }
        }
    }

    private void next() {
        phase++;
        inPhase = 0;
    }

    /** Where an enemy's chest projects, in window points: the tooltip's unit. */
    private static double[] enemyOnScreen(Minecraft mc, RtsMode rts) {
        View v = rts.view();
        if (v == null || mc.level == null) {
            throw new IllegalStateException("no view");
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && body.spawnData().faction() == 1) {
                double[] w = Picking.project(v, e.getX(), e.getY() + 0.9, e.getZ());
                if (w != null && w[0] > 0 && w[0] < v.width() && w[1] > 0 && w[1] < v.height()) {
                    return w;
                }
            }
        }
        throw new IllegalStateException("no enemy on screen");
    }

    private static CameraRig rig(RtsMode rts) {
        CameraRig rig = rts.rig();
        if (rig == null) {
            throw new IllegalStateException("RTS mode is off");
        }
        return rig;
    }

    private static double[] gui(Screen screen, RtsMode rts, double x, double y, double z) {
        View v = rts.view();
        double[] w = v == null ? null : Picking.project(v, x, y, z);
        if (v == null || w == null) {
            throw new IllegalStateException("cannot project " + x + ", " + y + ", " + z);
        }
        return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
    }

    static int bodies(Minecraft mc) {
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
        ctx.result.addProperty("phase", phase);
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
