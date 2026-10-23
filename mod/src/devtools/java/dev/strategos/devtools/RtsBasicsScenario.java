package dev.strategos.devtools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * M2's first L4 checks (CAM-04.3–4, UI-01.3, CAM-02): the player, made a spectator, enters RTS mode; ten clicks through
 * the RTS screen's own mouse handlers land on ten gold blocks; W held pans the camera; chat and the pause menu open
 * over the screen and it comes back, cursor free, when they close; the server sees no attack or use from the player
 * all along; attack-move, rebound to G in its key mapping, fires on G. Two screenshots of the default pose, for QA-18's
 * goldens later.
 */
final class RtsBasicsScenario implements Scenario {
    private static final int[] GOLD_X = {-6, -3, 0, 3, 6};
    private static final int[] GOLD_Z = {12, 18};
    private static final int CLICKS_FROM = 60;

    private final AtomicInteger attacksAndUses = new AtomicInteger();
    private final JsonArray clicks = new JsonArray();
    private int floor;
    private int hits;
    private double panFrom = Double.NaN;
    private boolean chatBack;
    private boolean pauseBack;
    private @Nullable String failure;

    @Override
    public String name() {
        return "rts_basics";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        for (int x : GOLD_X) {
            for (int z : GOLD_Z) {
                level.setBlock(new BlockPos(x, floor - 1, z), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            }
        }
        player.setGameMode(GameType.SPECTATOR);
        player.connection.teleport(0.5, floor + 30, -8, 0, 40);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.LeftClickBlock.class, e -> attacksAndUses.incrementAndGet());
        NeoForge.EVENT_BUS.addListener(
                PlayerInteractEvent.RightClickBlock.class, e -> attacksAndUses.incrementAndGet());
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, e -> attacksAndUses.incrementAndGet());
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, e -> attacksAndUses.incrementAndGet());
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, e -> attacksAndUses.incrementAndGet());
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        try {
            if (tick == 20) {
                rts.enter();
            } else if (tick == 40) {
                require(rts.active() && mc.screen != null && !mc.mouseHandler.isMouseGrabbed(), "RTS mode is on");
                ctx.capture("rts_pose_default_1");
            } else if (tick == 42) {
                ctx.capture("rts_pose_default_2");
            } else if (tick >= CLICKS_FROM && tick < CLICKS_FROM + 40 && (tick - CLICKS_FROM) % 4 == 0) {
                click(mc, rts, (tick - CLICKS_FROM) / 4);
            } else if (tick == 110) {
                panFrom = rig(rts)[1];
                screen(mc).keyPressed(KeyCode.W, 0, 0);
            } else if (tick == 130) {
                screen(mc).keyReleased(KeyCode.W, 0, 0);
                require(rig(rts)[1] - panFrom > 10, "W panned " + (rig(rts)[1] - panFrom) + " blocks");
            } else if (tick == 140) {
                mc.setScreen(new ChatScreen(""));
            } else if (tick == 142) {
                mc.setScreen(null);
            } else if (tick == 145) {
                chatBack = rts.active() && mc.screen != null && !mc.mouseHandler.isMouseGrabbed();
                mc.pauseGame(false);
            } else if (tick == 148) {
                mc.setScreen(null);
            } else if (tick == 151) {
                pauseBack = rts.active() && mc.screen != null && !mc.mouseHandler.isMouseGrabbed();
                KeyMapping attackMove = mapping(mc, "key.strategos.attack_move");
                attackMove.setKey(InputConstants.Type.KEYSYM.getOrCreate('G'));
                KeyMapping.resetMapping();
                rts.leave();
            } else if (tick == 153) {
                rts.enter();
            } else if (tick == 156) {
                screen(mc).keyPressed('G', 0, 0);
                screen(mc).keyReleased('G', 0, 0);
                require(rts.lastAction() == Action.ATTACK_MOVE, "G fired " + rts.lastAction());
                mapping(mc, "key.strategos.attack_move").setToDefault();
                KeyMapping.resetMapping();
            } else if (tick == 160) {
                finish(ctx);
                return true;
            }
        } catch (IllegalStateException e) {
            failure = e.getMessage();
            finish(ctx);
            return true;
        }
        return false;
    }

    /** One click through the RTS screen on the k-th gold block's projected top, in the screen's GUI pixels. */
    private void click(Minecraft mc, RtsMode rts, int k) {
        BlockPos target = new BlockPos(GOLD_X[k % GOLD_X.length], floor - 1, GOLD_Z[k / GOLD_X.length]);
        View view = rts.view();
        require(view != null, "no frame's view yet");
        double[] window = Picking.project(view, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5);
        require(window != null, target.toShortString() + " is behind the camera");
        Screen screen = screen(mc);
        double gx = window[0] * screen.width / mc.getWindow().getScreenWidth();
        double gy = window[1] * screen.height / mc.getWindow().getScreenHeight();
        screen.mouseClicked(gx, gy, 0);
        screen.mouseReleased(gx, gy, 0);
        RtsMode.Pick pick = rts.lastPick();
        JsonObject row = new JsonObject();
        row.addProperty("target", target.toShortString());
        row.addProperty("gui", gx + ", " + gy);
        if (pick != null) {
            double dx = pick.x() - target.getX();
            double dy = pick.y() - target.getY();
            double dz = pick.z() - target.getZ();
            double error = Math.sqrt(dx * dx + dy * dy + dz * dz);
            row.addProperty("picked", pick.x() + ", " + pick.y() + ", " + pick.z());
            row.addProperty("error", error);
            if (error <= 1) {
                hits++;
            }
        }
        clicks.add(row);
    }

    private static Screen screen(Minecraft mc) {
        Screen screen = mc.screen;
        require(screen != null, "no RTS screen");
        return screen;
    }

    private static double[] rig(RtsMode rts) {
        var rig = rts.rig();
        require(rig != null, "no rig");
        return new double[] {rig.focusX(), rig.focusZ()};
    }

    private static KeyMapping mapping(Minecraft mc, String name) {
        for (KeyMapping m : mc.options.keyMappings) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalStateException("no key mapping " + name);
    }

    private static void require(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
    }

    private void finish(ScenarioContext ctx) {
        ctx.result.add("clicks", clicks);
        ctx.result.addProperty("hits", hits);
        ctx.result.addProperty("chatThenBack", chatBack);
        ctx.result.addProperty("pauseThenBack", pauseBack);
        ctx.result.addProperty("attacksAndUsesOnTheServer", attacksAndUses.get());
        if (failure != null) {
            ctx.result.addProperty("failure", failure);
        }
        ctx.result.addProperty(
                "ok", failure == null && hits == 10 && chatBack && pauseBack && attacksAndUses.get() == 0);
    }
}
