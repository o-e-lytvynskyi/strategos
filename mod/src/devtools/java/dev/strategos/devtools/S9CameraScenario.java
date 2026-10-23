package dev.strategos.devtools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import dev.strategos.mod.entity.UnitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Spike S9's client (M2 spec §5, ADR-0017's decided body): it joins the spike server, which puts it, a spectator, at the
 * picking pose above the world's centre. First it checks picking on this display: for each of the server's 20 gold
 * blocks it projects the block with the frame's own matrices, puts the OS cursor there, reads it back from GLFW (in
 * window points, which a Retina display doubles in the framebuffer), unprojects that into a ray and clips it against
 * the level; the frame's pixel at the projected point must show gold. Then it pans as ADR-0017's body does, the player
 * itself moved each tick, at 10, 30, 60, 100 and 160 blocks a second, and keeps panning at 60 while the server jumps it
 * 300 blocks; a jump is the server moving the player more than 50 blocks, after which it pans on from there. Any other
 * difference between where it put the player and where the player is counts as a correction. It ends when the server
 * disconnects it. Spike code, never promoted.
 */
final class S9CameraScenario implements Scenario {
    /** The picking pose's height above the floor and pitch, as the server places the player. */
    static final int HEIGHT = 24;

    static final float PITCH = 55;

    private static final int[] GOLD_X = {-8, -4, 0, 4, 8};
    private static final int[] GOLD_Z = {10, 14, 18, 22};
    private static final int PICK_FROM = 40;
    private static final int PICK_EVERY = 8;
    private static final int PAN_FROM = PICK_FROM + PICK_EVERY * 20 + 20;
    private static final double[] SPEEDS = {0.5, 1.5, 3.0, 5.0, 8.0};
    private static final int[] SPEED_TICKS = {300, 300, 300, 200, 200};
    private static final double LAST_SPEED = 3.0;
    private static final double EDGE = 140;

    private final JsonArray picks = new JsonArray();
    private final JsonObject correctionsBySpeed = new JsonObject();
    private @Nullable Vec3 commanded;
    private double direction = 1;
    private int corrections;
    private int rebases;
    private boolean joinedWorld;
    private int floor;
    private @Nullable Matrix4f projection;
    private @Nullable Matrix4f modelView;
    private @Nullable Vec3 eye;
    private @Nullable BlockPos picking;
    private double @Nullable [] pickedAt;
    private boolean sample;
    private int pixelsGold;
    private int picksWithin;
    private int tick;
    private int jumpedAt = -1;
    private final JsonArray unitsAfterJumps = new JsonArray();

    @Override
    public String name() {
        return "s9";
    }

    @Override
    public String server() {
        return System.getProperty("strategos.scenario.server", "localhost:25598");
    }

    @Override
    public void joined(Minecraft mc, ScenarioContext ctx) {
        joinedWorld = true;
        mc.options.hideGui = true;
        mc.options.guiScale().set(2);
        mc.options.renderDistance().set(10); // a player's usual distance; the harness's options say 5
        mc.options.broadcastOptions();
        mc.resizeDisplay();
        floor = mc.level == null ? 0 : mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::onStage);
        ctx.result.addProperty("sodium", ModList.get().isLoaded("sodium"));
        ctx.result.addProperty("iris", ModList.get().isLoaded("iris"));
    }

    private void onStage(RenderLevelStageEvent event) {
        if (event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)) {
            projection = new Matrix4f(event.getProjectionMatrix());
            modelView = new Matrix4f(event.getModelViewMatrix());
            eye = event.getCamera().getPosition();
        }
    }

    /** The rig, before the player ticks and sends its position: a pan step, or a jump taken over. */
    @Override
    public void clientTickStart() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !joinedWorld) {
            return;
        }
        Vec3 actual = mc.player.position();
        Vec3 at = commanded;
        if (at == null || actual.distanceTo(at) > 50) {
            if (at != null) {
                rebases++;
                jumpedAt = tick;
            }
            at = actual;
        } else if (actual.distanceTo(at) > 0.05) {
            corrections++;
            String phase = speed(tick - PAN_FROM - 1) + " blocks a tick";
            correctionsBySpeed.addProperty(
                    phase,
                    correctionsBySpeed.has(phase)
                            ? correctionsBySpeed.get(phase).getAsInt() + 1
                            : 1);
            at = actual;
        }
        double speed = speed(tick - PAN_FROM);
        double x = at.x + direction * speed;
        if (Math.abs(x) > EDGE) {
            direction = -direction;
            x = at.x + direction * speed;
        }
        Vec3 next = new Vec3(x, at.y, at.z);
        commanded = next;
        mc.player.moveTo(next.x, next.y, next.z, 0, PITCH);
        mc.player.setDeltaMovement(Vec3.ZERO);
    }

    /** Blocks a tick at {@code t} ticks into the pans: 0 before them, then each speed in turn, then the last. */
    private static double speed(int t) {
        if (t < 0) {
            return 0;
        }
        int left = t;
        for (int k = 0; k < SPEEDS.length; k++) {
            if (left < SPEED_TICKS[k]) {
                return SPEEDS[k];
            }
            left -= SPEED_TICKS[k];
        }
        return LAST_SPEED;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        this.tick = tick;
        if (mc.level == null || mc.player == null) {
            finish(ctx);
            return true;
        }
        if (jumpedAt >= 0 && (tick - jumpedAt == 20 || tick - jumpedAt == 60)) {
            int bodies = 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity && e.distanceTo(mc.player) <= 160) {
                    bodies++;
                }
            }
            unitsAfterJumps.add((tick - jumpedAt) + " ticks: " + bodies);
        }
        int t = tick - PICK_FROM;
        if (t >= 0 && t < PICK_EVERY * GOLD_X.length * GOLD_Z.length) {
            int k = t / PICK_EVERY;
            switch (t % PICK_EVERY) {
                case 0 -> aim(mc, gold(k));
                case 4 -> check(mc, gold(k));
                default -> {}
            }
        }
        return false;
    }

    /** The k-th gold block, found in its column: the client has no motion-blocking heightmap to read the floor from. */
    private BlockPos gold(int k) {
        int x = GOLD_X[k % GOLD_X.length];
        int z = GOLD_Z[k / GOLD_X.length];
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (int y = mc.level.getMaxBuildHeight() - 1; y >= mc.level.getMinBuildHeight(); y--) {
                if (mc.level.getBlockState(new BlockPos(x, y, z)).is(Blocks.GOLD_BLOCK)) {
                    return new BlockPos(x, y, z);
                }
            }
        }
        return new BlockPos(x, floor - 1, z);
    }

    /** Puts the OS cursor on the block's projected top centre and asks for the next frame's pixel there. */
    private void aim(Minecraft mc, BlockPos target) {
        double[] screen =
                project(mc.getWindow(), new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5));
        if (screen == null) {
            pickedAt = null;
            return;
        }
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), screen[0], screen[1]);
        picking = target;
        pickedAt = screen;
        sample = true;
    }

    /** Reads the cursor back from GLFW, unprojects it and clips the ray against the level. */
    private void check(Minecraft mc, BlockPos target) {
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), x, y);
        JsonObject pick = new JsonObject();
        pick.addProperty("target", target.toShortString());
        double[] aimed = pickedAt;
        pick.addProperty("aimed", aimed == null ? "off screen" : aimed[0] + ", " + aimed[1]);
        pick.addProperty("cursor", x[0] + ", " + y[0]);
        Vec3[] ray = unproject(mc.getWindow(), x[0], y[0]);
        if (ray != null && mc.level != null && mc.player != null) {
            BlockHitResult hit = mc.level.clip(new ClipContext(
                    ray[0],
                    ray[0].add(ray[1].scale(256)),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos at = hit.getBlockPos();
                double error = Math.sqrt(at.distSqr(target));
                pick.addProperty("hit", at.toShortString());
                pick.addProperty("error", error);
                if (error <= 1) {
                    picksWithin++;
                }
            }
        }
        picks.add(pick);
    }

    @Override
    public void frameEnd() {
        if (!sample) {
            return;
        }
        sample = false;
        Minecraft mc = Minecraft.getInstance();
        double[] at = pickedAt;
        if (at == null || picking == null) {
            return;
        }
        Window window = mc.getWindow();
        double scale = (double) window.getWidth() / window.getScreenWidth();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            int px = (int) Math.round(at[0] * scale);
            int py = (int) Math.round(at[1] * scale);
            if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                int abgr = image.getPixelRGBA(px, py);
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                if (r > 150 && g > 110 && r > b + 70) {
                    pixelsGold++;
                }
            }
        }
    }

    /** Window coordinates, in points, of a world point in the last frame, or null behind the camera. */
    private double @Nullable [] project(Window window, Vec3 world) {
        Matrix4f p = projection;
        Matrix4f v = modelView;
        Vec3 e = eye;
        if (p == null || v == null || e == null) {
            return null;
        }
        Vector4f clip = new Vector4f((float) (world.x - e.x), (float) (world.y - e.y), (float) (world.z - e.z), 1);
        new Matrix4f(p).mul(v).transform(clip);
        if (clip.w <= 0) {
            return null;
        }
        double nx = clip.x / clip.w;
        double ny = clip.y / clip.w;
        return new double[] {(nx + 1) / 2 * window.getScreenWidth(), (1 - ny) / 2 * window.getScreenHeight()};
    }

    /** The ray through a cursor at window coordinates, in points: its origin and unit direction. */
    private Vec3 @Nullable [] unproject(Window window, double x, double y) {
        Matrix4f p = projection;
        Matrix4f v = modelView;
        Vec3 e = eye;
        if (p == null || v == null || e == null) {
            return null;
        }
        double nx = 2 * x / window.getScreenWidth() - 1;
        double ny = 1 - 2 * y / window.getScreenHeight();
        Matrix4f inverse = new Matrix4f(p).mul(v).invert();
        Vector4f near = inverse.transform(new Vector4f((float) nx, (float) ny, -1, 1));
        Vector4f far = inverse.transform(new Vector4f((float) nx, (float) ny, 1, 1));
        Vec3 a = new Vec3(near.x / near.w, near.y / near.w, near.z / near.w);
        Vec3 b = new Vec3(far.x / far.w, far.y / far.w, far.z / far.w);
        return new Vec3[] {e.add(a), b.subtract(a).normalize()};
    }

    private void finish(ScenarioContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        ctx.result.add("picks", picks);
        ctx.result.addProperty("picksWithinOneBlock", picksWithin);
        ctx.result.addProperty("pixelsGold", pixelsGold);
        ctx.result.addProperty("targets", GOLD_X.length * GOLD_Z.length);
        ctx.result.addProperty("corrections", corrections);
        ctx.result.add("correctionsBySpeed", correctionsBySpeed);
        ctx.result.addProperty("jumpsTakenOver", rebases);
        ctx.result.add("unitBodiesWithin160AfterJumps", unitsAfterJumps);
        ctx.result.addProperty("screen", window.getScreenWidth() + " x " + window.getScreenHeight());
        ctx.result.addProperty("framebuffer", window.getWidth() + " x " + window.getHeight());
        ctx.result.addProperty("guiScale", window.getGuiScale());
        ctx.result.addProperty("ticks", tick);
        ctx.result.addProperty("ok", picksWithin == GOLD_X.length * GOLD_Z.length);
    }
}
