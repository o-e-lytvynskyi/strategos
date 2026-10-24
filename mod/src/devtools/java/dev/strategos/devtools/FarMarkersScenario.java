package dev.strategos.devtools;

import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
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
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * NET-08.5 on screen: from the RTS camera's top height at render distance 12, a band of 72 enemy units 150 to 175 blocks
 * from the eye, round the forward line, and five own units near the focus. Bodies pair within 160 blocks; past that the
 * summaries' far markers must stand in. Every unit whose ground the client has loaded and whose feet project inside the
 * window is a body or a marker, never neither.
 */
final class FarMarkersScenario implements Scenario {
    private final StringBuilder failures = new StringBuilder();
    private volatile @Nullable MatchRuntime match;
    private int phase;
    private int inPhase;

    @Override
    public String name() {
        return "far_markers";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 256, 256),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        match = rt;
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        // the eye ends 28 blocks south of here once zoomed out, 30 south of the space, so the band fits inside it
        player.connection.teleport(0.5, floor + 24, -130, 0, 55);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
        }
        if (tick == 20) {
            rts.enter();
        }
        Screen screen = mc.screen;
        if (tick < 40 || screen == null || !rts.active() || rts.rig() == null || rts.view() == null) {
            return tick > 600 && fail(ctx, "RTS mode is not on");
        }
        if (ctx.captureName != null) {
            return false;
        }
        inPhase++;
        switch (phase) {
            case 0 -> {
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -20); // to the top height
                next();
            }
            case 1 -> {
                if (inPhase >= 60) {
                    spawn(mc, rts);
                    next();
                }
            }
            case 2 -> {
                if (StrategosClient.model().units().size() == 77
                        && inPhase >= 100
                        && mc.levelRenderer.hasRenderedAllSections()) {
                    next();
                } else if (inPhase > 600) {
                    return fail(
                            ctx,
                            "the model knows " + StrategosClient.model().units().size() + " units");
                }
            }
            case 3 -> {
                check(mc, rts, ctx);
                ctx.capture("far_markers");
                next();
            }
            default -> {
                return done(ctx);
            }
        }
        return false;
    }

    /** Five own units at the focus, and the band of enemies 150 to 175 blocks from the eye, 44° either side of ahead. */
    private void spawn(Minecraft mc, RtsMode rts) {
        var rig = rts.rig();
        MatchRuntime rt = match;
        IntegratedServer server = mc.getSingleplayerServer();
        if (rig == null || rt == null || server == null || mc.player == null) {
            return;
        }
        double eyeX = mc.player.getX();
        double eyeZ = mc.player.getZ();
        double yaw = Math.toRadians(rig.pose().yaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double focusX = rig.focusX();
        double focusZ = rig.focusZ();
        server.execute(() -> {
            var space = rt.space();
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(
                            TemplateSet.DEBUG_WALKER,
                            new FactionSlot(0),
                            new Vec2d(space.localX(focusX), space.localZ(focusZ)),
                            5,
                            2));
            for (int d = 150; d <= 175; d += 5) {
                for (int a = -44; a <= 44; a += 8) {
                    double t = Math.toRadians(a);
                    double dx = fx * Math.cos(t) - fz * Math.sin(t);
                    double dz = fz * Math.cos(t) + fx * Math.sin(t);
                    rt.submit(
                            PlayerSlot.ADMIN,
                            new SpawnUnits(
                                    TemplateSet.DEBUG_WALKER,
                                    new FactionSlot(1),
                                    new Vec2d(space.localX(eyeX + dx * d), space.localZ(eyeZ + dz * d)),
                                    1,
                                    0));
                }
            }
        });
    }

    /** Every known unit on loaded ground in the window: a tracked body or a marker. */
    private void check(Minecraft mc, RtsMode rts, ScenarioContext ctx) {
        View v = rts.view();
        MatchJoin join = StrategosClient.match();
        if (v == null || join == null || mc.level == null) {
            failures.append("no view; ");
            return;
        }
        IntOpenHashSet bodies = new IntOpenHashSet();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body && body.spawnData() != null) {
                bodies.add(Handle.index(body.spawnData().handle()));
            }
        }
        IntOpenHashSet markers = StrategosClient.farMarkers().drawn();
        int inView = 0;
        int asBodies = 0;
        int asMarkers = 0;
        int neither = 0;
        for (UnitSummary.Entry u : StrategosClient.model().units()) {
            double x = join.originX() + u.x();
            double z = join.originZ() + u.z();
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            if (!mc.level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))) {
                continue;
            }
            double y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            double[] w = Picking.project(v, x, y, z);
            if (w == null || w[0] < 0 || w[0] > v.width() || w[1] < 0 || w[1] > v.height()) {
                continue;
            }
            inView++;
            if (bodies.contains(u.index())) {
                asBodies++;
            } else if (markers.contains(u.index())) {
                asMarkers++;
            } else {
                neither++;
            }
        }
        ctx.result.addProperty("inView", inView);
        ctx.result.addProperty("bodies", asBodies);
        ctx.result.addProperty("markers", asMarkers);
        ctx.result.addProperty("neither", neither);
        if (neither > 0) {
            failures.append(neither).append(" units in view are neither a body nor a marker; ");
        }
        if (asMarkers == 0) {
            failures.append("no unit in view stood beyond the bodies' range, so the markers went unchecked; ");
        }
    }

    private void next() {
        phase++;
        inPhase = 0;
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
