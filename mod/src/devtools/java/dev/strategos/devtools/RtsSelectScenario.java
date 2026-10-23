package dev.strategos.devtools;

import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.order.OrderKind;
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
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * UI-03.3–4, UNIT-03.2 and UI-04.4 on screen: the player, seated in slot 0 of a match with 150 of its own units and 20
 * enemies in front of it, enters RTS mode. A drag over the whole window selects the 150 and outlines them, and no enemy;
 * an empty click clears; a double click on one of them takes all of its type on screen; a click on an enemy inspects
 * it and selects nothing; Ctrl+1 keeps the 150 in group 1, which a 1 recalls after a clear; a plain click then takes one.
 * Then orders (UI-05): group 1 back, the right button held on the ground shows the places, its release sends one Move
 * that gives all 150 an order there, and a Shift click queues a second behind it. Then ORD-04: a right click on an
 * enemy sends an Attack (a red marker; debug walkers cannot strike, so the server refuses it), and Z then a right click
 * on the ground an AttackMove that all 150 hold (an orange marker). Screenshots of the outlined army and the markers.
 */
final class RtsSelectScenario implements Scenario {
    private static final int OWN = 150;
    private static final int ENEMIES = 20;

    private final StringBuilder failures = new StringBuilder();
    private int floor;

    @Override
    public String name() {
        return "rts_select";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 256, 256),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(128, 142), OWN, 7));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(137, 144), ENEMIES, 2));
        match = rt;
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt); // what /strategos match join does
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        player.connection.teleport(0.5, floor + 30, -14, 0, 40);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        if (tick == 20) {
            rts.enter();
            return false;
        }
        if (tick < 40 || mc.level == null) {
            return false;
        }
        if (tick < 600 && bodies(mc) < OWN + ENEMIES) {
            return false; // the bodies arrive
        }
        Screen screen = mc.screen;
        if (screen == null || !rts.active() || rts.view() == null) {
            return fail(ctx, "RTS mode is not on");
        }
        try {
            return step(mc, ctx, rts, screen);
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    private boolean step(Minecraft mc, ScenarioContext ctx, RtsMode rts, Screen screen) {
        switch (phase) {
            case 0 -> {
                screen.mouseClicked(1, 1, 0);
                screen.mouseMoved(screen.width - 2, screen.height - 2);
                screen.mouseReleased(screen.width - 2, screen.height - 2, 0);
                check(
                        rts.selection().handles().size() == OWN,
                        "the drag selected " + rts.selection().handles().size());
                ctx.result.addProperty("dragSelected", rts.selection().handles().size());
            }
            case 1 -> {
                int glowing = glowing(mc, 0);
                int enemiesGlowing = glowing(mc, 1);
                check(glowing == OWN && enemiesGlowing == 0, glowing + " own and " + enemiesGlowing + " enemies glow");
                ctx.result.addProperty("outlined", glowing);
                ctx.capture("rts_select_outline_1");
            }
            case 2 -> ctx.capture("rts_select_outline_2");
            case 3 -> {
                double[] empty = gui(screen, rts, 0.5, floor, -6);
                click(screen, empty);
                check(
                        rts.selection().handles().isEmpty(),
                        "an empty click left " + rts.selection().handles().size());
            }
            case 4 -> {
                double[] own = unitOnScreen(mc, screen, rts, 0);
                click(screen, own);
                click(screen, own);
                check(
                        rts.selection().handles().size() == OWN,
                        "the double click took " + rts.selection().handles().size());
                ctx.result.addProperty(
                        "doubleClickTook", rts.selection().handles().size());
            }
            case 5 -> {
                screen.keyPressed(KeyCode.digit(1).code(), 0, 2); // Ctrl+1
                double[] enemy = unitOnScreen(mc, screen, rts, 1);
                click(screen, enemy);
                check(
                        rts.selection().handles().isEmpty() && rts.selection().inspected() != null,
                        "an enemy click: selected " + rts.selection().handles().size() + ", inspected "
                                + rts.selection().inspected());
            }
            case 6 -> {
                screen.keyPressed(KeyCode.digit(1).code(), 0, 0);
                check(
                        rts.selection().handles().size() == OWN,
                        "group 1 recalled " + rts.selection().handles().size());
                double[] own = unitOnScreen(mc, screen, rts, 0);
                screen.mouseClicked(own[0], own[1], 0);
                screen.mouseReleased(own[0], own[1], 0);
            }
            case 7 -> {
                // the click above was a plain one: it selected a single unit
                check(
                        rts.selection().handles().size() == 1,
                        "a click selected " + rts.selection().handles().size());
                ctx.result.addProperty("groupRecalled", OWN);
            }
            case 8, 9, 10, 11, 12, 13, 14 -> {} // past the double tap's 300 ms, so the next 1 recalls
            case 15 -> {
                screen.keyPressed(KeyCode.digit(1).code(), 0, 0);
                check(
                        rts.selection().handles().size() == OWN,
                        "1 recalled " + rts.selection().handles().size());
                orderAt = gui(screen, rts, 0.5, floor, 4.5);
                double[] w = window(rts, 0.5, floor, 4.5); // the preview follows the OS cursor, as a player's does
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), w[0], w[1]);
                screen.mouseClicked(orderAt[0], orderAt[1], 1); // the right button, held: the preview
            }
            case 16, 17, 18, 19, 20, 21, 22, 23 -> {} // the preview shows for a few ticks
            case 24 -> ctx.capture("rts_order_preview");
            case 25 -> {
                screen.mouseReleased(orderAt[0], orderAt[1], 1);
                check(rts.ordersSent() == 1, rts.ordersSent() + " orders sent for one click");
            }
            case 26, 27, 28, 29, 30, 31 -> {} // the order reaches the server's next step
            case 32 -> {
                int ordered = ordered(1, 0.5, 4.5);
                check(ordered == OWN, ordered + " of " + OWN + " units ordered near the click");
                ctx.result.addProperty("ordered", ordered);
                // Shift is read from the keyboard, so the queued click goes in through the mode, as the screen sends it
                double[] w = window(rts, 10.5, floor, 2.5);
                rts.order(w[0], w[1], true);
            }
            case 33, 34, 35, 36, 37 -> {}
            case 38 -> {
                int queued = ordered(2, 0.5, 4.5);
                check(queued == OWN, queued + " of " + OWN + " units with the queued order behind");
                ctx.result.addProperty("queued", queued);
                ctx.capture("rts_order_markers");
            }
            case 39 -> { // ORD-04: a right click on an enemy attacks it
                double[] enemy = unitOnScreen(mc, screen, rts, 1);
                screen.mouseClicked(enemy[0], enemy[1], 1);
                screen.mouseReleased(enemy[0], enemy[1], 1);
            }
            case 40, 41, 42, 43, 44, 45 -> {}
            case 46 -> {
                check(rts.lastCommand() instanceof Attack, "an enemy's right click sent " + rts.lastCommand());
                ctx.result.addProperty("attackSent", rts.lastCommand() instanceof Attack);
                ctx.capture("rts_attack_marker");
                screen.keyPressed(KeyCode.Z, 0, 0); // arms attack-move for the next right click
                check(rts.attackMoveArmed(), "Z did not arm attack-move");
            }
            case 47 -> {
                double[] at = gui(screen, rts, 6.5, floor, 0.5);
                screen.mouseClicked(at[0], at[1], 1);
                screen.mouseReleased(at[0], at[1], 1);
                check(!rts.attackMoveArmed(), "the click did not disarm attack-move");
            }
            case 48, 49, 50, 51, 52, 53 -> {}
            case 54 -> {
                int moving = attackMoving(6.5, 0.5);
                check(moving == OWN, moving + " of " + OWN + " units attack-moving to the click");
                ctx.result.addProperty("attackMoving", moving);
                ctx.capture("rts_attack_move_marker");
                var rig = rts.rig();
                if (rig != null) {
                    jumpTo = new double[] {rig.focusX() + 100, rig.focusZ()};
                    rts.centreOn(jumpTo[0], jumpTo[1]); // far: a camera_jump the server answers with a teleport
                }
            }
            case 55, 56, 57, 58, 59, 60, 61, 62, 63, 64 -> {}
            case 65 -> {
                var rig = rts.rig();
                check(
                        rig != null && Math.abs(rig.focusX() - jumpTo[0]) < 1 && Math.abs(rig.focusZ() - jumpTo[1]) < 1,
                        "the jump left the focus at " + (rig == null ? "nothing" : rig.focusX() + ", " + rig.focusZ()));
                ctx.result.addProperty("jumped", rig != null && Math.abs(rig.focusX() - jumpTo[0]) < 1);
                return finish(ctx);
            }
            default -> {
                return finish(ctx);
            }
        }
        phase++;
        return false;
    }

    private int phase;
    private double[] orderAt = new double[2];
    private double[] jumpTo = new double[2];
    private volatile @Nullable MatchRuntime match;

    private static void click(Screen screen, double[] at) {
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
    }

    /** A unit of {@code faction} whose feet project inside the middle of the window, in GUI pixels. */
    private double[] unitOnScreen(Minecraft mc, Screen screen, RtsMode rts, int faction) {
        View v = rts.view();
        if (v == null || mc.level == null) {
            throw new IllegalStateException("no view");
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && body.spawnData().faction() == faction) {
                double[] w = Picking.project(v, e.getX(), e.getY() + 0.9, e.getZ());
                if (w != null
                        && w[0] > v.width() * 0.2
                        && w[0] < v.width() * 0.8
                        && w[1] > v.height() * 0.2
                        && w[1] < v.height() * 0.8) {
                    return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
                }
            }
        }
        throw new IllegalStateException("no unit of faction " + faction + " in the middle of the window");
    }

    private static double[] gui(Screen screen, RtsMode rts, double x, double y, double z) {
        View v = rts.view();
        double[] w = v == null ? null : Picking.project(v, x, y, z);
        if (v == null || w == null) {
            throw new IllegalStateException("cannot project " + x + ", " + y + ", " + z);
        }
        return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
    }

    /** Own units with at least {@code count} orders, the first ending within 15 blocks of the world point. */
    private int ordered(int count, double wx, double wz) {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt == null || server == null) {
            return -1;
        }
        return server.submit(() -> {
                    var core = rt.core();
                    if (core == null) {
                        return -1;
                    }
                    int n = 0;
                    double lx = wx - rt.space().origin().getX();
                    double lz = wz - rt.space().origin().getZ();
                    for (int h : core.queries().unitHandlesOf(new FactionSlot(0))) {
                        var orders = core.queries().orders(h);
                        if (orders.size() >= count
                                && Math.hypot(
                                                orders.get(0).x() - lx,
                                                orders.get(0).z() - lz)
                                        < 15) {
                            n++;
                        }
                    }
                    return n;
                })
                .join();
    }

    /** Own units whose current order is an attack-move ending within 15 blocks of the world point. */
    private int attackMoving(double wx, double wz) {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt == null || server == null) {
            return -1;
        }
        return server.submit(() -> {
                    var core = rt.core();
                    if (core == null) {
                        return -1;
                    }
                    int n = 0;
                    double lx = wx - rt.space().origin().getX();
                    double lz = wz - rt.space().origin().getZ();
                    for (int h : core.queries().unitHandlesOf(new FactionSlot(0))) {
                        var orders = core.queries().orders(h);
                        if (!orders.isEmpty()
                                && orders.get(0).kind() == OrderKind.ATTACK_MOVE
                                && Math.hypot(
                                                orders.get(0).x() - lx,
                                                orders.get(0).z() - lz)
                                        < 15) {
                            n++;
                        }
                    }
                    return n;
                })
                .join();
    }

    private static double[] window(RtsMode rts, double x, double y, double z) {
        View v = rts.view();
        double[] w = v == null ? null : Picking.project(v, x, y, z);
        if (w == null) {
            throw new IllegalStateException("cannot project " + x + ", " + y + ", " + z);
        }
        return w;
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

    private static int glowing(Minecraft mc, int faction) {
        int n = 0;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity body
                        && body.spawnData() != null
                        && body.spawnData().faction() == faction
                        && body.isCurrentlyGlowing()) {
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
        return finish(ctx);
    }

    private boolean finish(ScenarioContext ctx) {
        String f = failures.toString();
        @Nullable String none = f.isEmpty() ? null : f;
        if (none != null) {
            ctx.result.addProperty("failures", none);
        }
        ctx.result.addProperty("phase", phase);
        ctx.result.addProperty("ok", none == null);
        return true;
    }
}
