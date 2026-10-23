package dev.strategos.mod.client.rts;

import dev.strategos.core.client.camera.CameraInput;
import dev.strategos.core.client.camera.CameraLimits;
import dev.strategos.core.client.camera.CameraPose;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.InputState;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.entity.UnitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * RTS mode on this client (CAM-02, CAM-04; ADR-0017): the commander's own player, a spectator, is the camera, moved to
 * the rig's eye in {@code PlayerTickEvent.Pre} (after the old position is saved, so frames interpolate) and turned per
 * frame in {@code ViewportEvent.ComputeCameraAngles}, with no camera mixin; {@link RtsScreen} owns input while it is
 * on and comes back on the next tick after chat, the pause menu or any other screen closes over it. Each frame's matrices are kept for picking. Until CAM-03 the
 * server does not make the player a spectator, so RTS mode starts only for a player who already is one. Client thread
 * only.
 */
public final class RtsMode {
    private static final double EDGE_POINTS = 6;
    private static final double DRAG_DEGREES_PER_PIXEL = 0.4;
    private static final int TRACKING_REACH = 192;

    private final RtsKeys keys = new RtsKeys();
    private @Nullable CameraRig rig;
    private @Nullable InputState input;
    private @Nullable View view;
    private double zoom;
    private @Nullable Pick lastPick;
    private @Nullable Action lastAction;

    /** What the last pick hit: a body's entity id, or a block, and where. */
    public record Pick(int entityId, double x, double y, double z) {}

    /** Listens on the mod bus for the key mappings and on the game bus for the rest. */
    public void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, keys::register);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Pre.class, this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, this::onAngles);
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFov.class, this::onFov);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::onStage);
    }

    public boolean active() {
        return rig != null;
    }

    /** The rig, while RTS mode is on. */
    public @Nullable CameraRig rig() {
        return rig;
    }

    /** The last frame's view, for scenarios; null before the first frame in RTS mode. */
    public @Nullable View view() {
        return view;
    }

    /** The last action the RTS screen fired, for scenarios; null before any. */
    public @Nullable Action lastAction() {
        return lastAction;
    }

    void fired(Action action) {
        lastAction = action;
    }

    /** The last pick, for scenarios and the hover outline; null before any. */
    public @Nullable Pick lastPick() {
        return lastPick;
    }

    /** Turns RTS mode on above the player, a spectator, or says why not. */
    public void enter() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || rig != null) {
            return;
        }
        if (!player.isSpectator()) {
            player.displayClientMessage(Component.literal("[Strategos] RTS mode needs spectator mode"), true);
            return;
        }
        double yaw = player.getYRot();
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        double fx = player.getX() - Math.sin(Math.toRadians(yaw)) * back;
        double fz = player.getZ() + Math.cos(Math.toRadians(yaw)) * back;
        rig = new CameraRig(limits(player), fx, fz, ground(fx, fz, player.getY()), yaw);
        input = new InputState(keys.current());
        mc.setScreen(new RtsScreen(this));
    }

    public void leave() {
        rig = null;
        input = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RtsScreen) {
            mc.setScreen(null);
        }
    }

    @Nullable InputState input() {
        return input;
    }

    RtsKeys keys() {
        return keys;
    }

    void zoom(double notches) {
        zoom += notches;
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (rig != null) {
                leave();
            }
            return;
        }
        if (rig == null) {
            while (keys.mapping(Action.TOGGLE_RTS).consumeClick()) {
                enter();
            }
            return;
        }
        if (mc.screen == null) { // chat, the pause menu or anything else closed over it: the RTS screen comes back
            mc.setScreen(new RtsScreen(this));
        }
    }

    /** The rig steps and puts the player at its eye, before the player ticks and sends its position. */
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        CameraRig r = rig;
        InputState in = input;
        Minecraft mc = Minecraft.getInstance();
        if (r == null || in == null || !(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        double[] edge = edgePan(mc);
        double forward = held(in, Action.CAMERA_PAN_FORWARD) - held(in, Action.CAMERA_PAN_BACK) + edge[1];
        double right = held(in, Action.CAMERA_PAN_RIGHT) - held(in, Action.CAMERA_PAN_LEFT) + edge[0];
        double rotate = held(in, Action.CAMERA_ROTATE_RIGHT) - held(in, Action.CAMERA_ROTATE_LEFT);
        double keyZoom = 0.25 * (held(in, Action.CAMERA_ZOOM_IN) - held(in, Action.CAMERA_ZOOM_OUT));
        StrategosClientConfig options = StrategosClientConfig.INSTANCE;
        r.tick(
                new CameraInput(
                        clamp(forward),
                        clamp(right),
                        rotate,
                        in.takeRotateDrag() * DRAG_DEGREES_PER_PIXEL,
                        zoom + keyZoom,
                        options.panSpeed.get(),
                        options.rotateSpeed.get()),
                (x, z) -> ground(x, z, r.pose().y()));
        zoom = 0;
        CameraPose eye = r.pose();
        player.setPos(eye.x(), eye.y() - player.getEyeHeight(), eye.z());
        player.setYRot((float) eye.yaw());
        player.setXRot((float) eye.pitch());
        player.setDeltaMovement(Vec3.ZERO);
    }

    private void onAngles(ViewportEvent.ComputeCameraAngles event) {
        CameraRig r = rig;
        if (r != null) {
            CameraPose pose = r.pose(event.getPartialTick());
            event.setYaw((float) pose.yaw());
            event.setPitch((float) pose.pitch());
            event.setRoll(0);
        }
    }

    private void onFov(ViewportEvent.ComputeFov event) {
        if (rig != null) {
            event.setFOV(CameraRig.FOV);
        }
    }

    /** The frame's matrices, as picking unprojects with them. */
    private void onStage(RenderLevelStageEvent event) {
        if (rig == null || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        view = new View(
                columns(event.getProjectionMatrix()),
                columns(event.getModelViewMatrix()),
                cam.x,
                cam.y,
                cam.z,
                mc.getWindow().getScreenWidth(),
                mc.getWindow().getScreenHeight());
    }

    /**
     * Picks what the cursor at window points points at: the nearest unit body along the ray within tracking reach, else
     * the block it hits. Bodies come from the level's entities near the camera for now; the client unit index comes
     * with NET-08.
     */
    @Nullable Pick pick(double x, double y) {
        View v = view;
        Minecraft mc = Minecraft.getInstance();
        if (v == null || mc.level == null || mc.player == null) {
            return null;
        }
        Picking.Ray ray = Picking.ray(v, x, y);
        double best = Double.POSITIVE_INFINITY;
        Pick found = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof UnitEntity)
                    || e.distanceToSqr(v.camX(), v.camY(), v.camZ()) > TRACKING_REACH * TRACKING_REACH) {
                continue;
            }
            var box = e.getBoundingBox();
            double t = Picking.hit(ray, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            if (t >= 0 && t < best) {
                best = t;
                found = new Pick(e.getId(), e.getX(), e.getY(), e.getZ());
            }
        }
        double[] far = ray.at(512);
        BlockHitResult hit = mc.level.clip(new ClipContext(
                new Vec3(ray.ox(), ray.oy(), ray.oz()),
                new Vec3(far[0], far[1], far[2]),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            double t = hit.getLocation().distanceTo(new Vec3(ray.ox(), ray.oy(), ray.oz()));
            if (t < best) {
                found = new Pick(
                        -1,
                        hit.getBlockPos().getX(),
                        hit.getBlockPos().getY(),
                        hit.getBlockPos().getZ());
            }
        }
        lastPick = found;
        return found;
    }

    /** -1..1 per axis when the cursor sits within a few points of the window's edge, the window focused. */
    private static double[] edgePan(Minecraft mc) {
        if (!mc.isWindowActive() || !StrategosClientConfig.INSTANCE.edgePan.get()) {
            return new double[] {0, 0};
        }
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        double w = mc.getWindow().getScreenWidth();
        double h = mc.getWindow().getScreenHeight();
        double right = x <= EDGE_POINTS ? -1 : x >= w - EDGE_POINTS ? 1 : 0;
        double forward = y <= EDGE_POINTS ? 1 : y >= h - EDGE_POINTS ? -1 : 0;
        return new double[] {right, forward};
    }

    private static double held(InputState in, Action action) {
        return in.held(action) ? 1 : 0;
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    /** The ground at a column: the client's motion-blocking heightmap where its chunk is loaded, else {@code otherwise}. */
    private static double ground(double x, double z, double otherwise) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return otherwise - CameraRig.DEFAULT_HEIGHT;
        }
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** The match space the player sits in, or a wide square round the player when it sits in none. */
    private static CameraLimits limits(LocalPlayer player) {
        MatchJoin join = StrategosClient.match();
        if (join == null) {
            return new CameraLimits(
                    player.getX() - 2_048, player.getZ() - 2_048, player.getX() + 2_048, player.getZ() + 2_048, 0);
        }
        return new CameraLimits(
                join.originX(),
                join.originZ(),
                join.originX() + join.sizeX(),
                join.originZ() + join.sizeZ(),
                CameraLimits.MARGIN);
    }

    private static double[] columns(Matrix4f m) {
        float[] f = m.get(new float[16]);
        double[] d = new double[16];
        for (int k = 0; k < 16; k++) {
            d[k] = f[k];
        }
        return d;
    }
}
