package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * ART-01.3's scene (golden candidates for T30): at noon, four workers of faction 0 and four spearmen each of factions 0
 * and 1 stand still; the RTS camera looks at them from its default 24 blocks and from its top, 64. Each body must be
 * dressed in its look: the dyed tunic, a spearman's helmet, trident and shield. Frames {@code look_24} and
 * {@code look_64}.
 */
final class ArtLookScenario implements Scenario {
    private static final int EACH = 4;
    private static final int SETTLE = 50;

    private final StringBuilder failures = new StringBuilder();
    private int phase;
    private int wait;

    @Override
    public String name() {
        return "art_look";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 128, 128),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(60, 82), EACH, 2));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(64.5, 82), EACH, 2));
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(69, 82), EACH, 2));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(0.5, floor + 24, 18 - back, 0, 55);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 20 && !rts.active()) {
            rts.enter();
        }
        if (tick < 60 || mc.level == null) {
            return false;
        }
        if (tick < 600 && bodies(mc) < 3 * EACH) {
            return false;
        }
        Screen screen = mc.screen;
        if (screen == null || !rts.active()) {
            return fail(ctx, "RTS mode is not on");
        }
        if (wait > 0) {
            wait--;
            return false;
        }
        switch (phase) {
            case 0 -> {
                mc.options.hideGui = true;
                wait = SETTLE;
            }
            case 1 -> {
                int dressed = 0;
                int armed = 0;
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof UnitEntity body && body.dressed()) {
                        dressed++;
                        if (body.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.TRIDENT)
                                && body.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD)
                                && body.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)) {
                            armed++;
                        }
                    }
                }
                check(dressed == 3 * EACH, dressed + " bodies dressed of " + 3 * EACH);
                check(armed == 2 * EACH, armed + " spearmen armed of " + 2 * EACH);
                ctx.result.addProperty("dressed", dressed);
                ctx.result.addProperty("spearmen", armed);
                ctx.capture("look_24");
            }
            case 2 -> {
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -20); // out, to the top
                wait = SETTLE;
            }
            case 3 -> ctx.capture("look_64");
            default -> {
                mc.options.hideGui = false;
                return finish(ctx);
            }
        }
        phase++;
        return false;
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
