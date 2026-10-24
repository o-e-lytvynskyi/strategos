package dev.strategos.mod.client.rts;

import dev.strategos.core.client.camera.CameraInput;
import dev.strategos.core.client.camera.CameraLimits;
import dev.strategos.core.client.camera.CameraPose;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.hud.IdleWorkers;
import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.input.InputState;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.client.select.ControlGroups;
import dev.strategos.core.client.select.Seen;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.CameraJump;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.Handle;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.server.net.payload.CameraJumpPayload;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
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
    /** Farther than this, centring jumps through the server instead of panning. */
    private static final double FAR = 48;
    /** The player standing farther than this from the rig's eye was teleported. */
    private static final double TELEPORTED = 16;

    private final RtsKeys keys = new RtsKeys();
    private final ControlGroups groups = new ControlGroups();
    private final RtsOrders orders = new RtsOrders(this);
    private final RtsPlacement placement = new RtsPlacement(this);
    private final RallyMarker rally = new RallyMarker(this);
    private final IdleWorkers idle = new IdleWorkers();
    /** The idle worker the key took whose body has not arrived yet, its index; -1 for none, and ticks left to wait. */
    private int pendingIdle = -1;

    private int pendingIdleTicks;
    private Selection selection = new Selection(-1);
    private @Nullable CameraRig rig;
    private @Nullable InputState input;
    private @Nullable View view;
    private double zoom;
    private @Nullable Pick lastPick;
    private @Nullable Action lastAction;
    private int autoEntered;
    private double @Nullable [] pendingJump;
    private long ticks;
    private double @Nullable [] cursor;
    /** Stepped down into first person (CAM-05): the RTS view comes back when the server makes this a spectator again. */
    private boolean firstPerson;

    private @Nullable GameType lastMode;

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
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, placement::render);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, rally::render);
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
        orders.fired(action);
    }

    /** The last command a right click or a first-person key sent, for scenarios; null before any. */
    public @Nullable Command lastCommand() {
        return orders.lastCommand();
    }

    /** Whether the next right click attack-moves (ORD-04): its key was pressed since the last order. */
    public boolean attackMoveArmed() {
        return orders.attackMoveArmed();
    }

    /** Placement mode (BLD-02). */
    RtsPlacement placement() {
        return placement;
    }

    /** STOP: the selection's orders end (ORD-02). */
    public void stop() {
        orders.stop();
    }

    /** One command from the HUD (UI-06), as a batch of its own; whether it went. */
    public boolean send(Command command) {
        return orders.send(command);
    }

    /**
     * The idle-worker key (UI-10): the next idle worker, in index order, is selected and the camera goes to it; a far
     * one is selected when its body arrives after the jump.
     */
    public void nextIdleWorker() {
        MatchJoin join = StrategosClient.match();
        if (join == null || rig == null) {
            return;
        }
        UnitSummary.Entry e =
                idle.next(StrategosClient.model().units(), join.slot().value(), StrategosClient.templates());
        if (e == null) {
            return;
        }
        centreOn(join.originX() + e.qx() / 8.0, join.originZ() + e.qz() / 8.0);
        pendingIdle = e.index();
        pendingIdleTicks = 100;
        selectPendingIdle();
    }

    private void selectPendingIdle() {
        Minecraft mc = Minecraft.getInstance();
        if (pendingIdle < 0 || mc.level == null) {
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && Handle.index(body.spawnData().handle()) == pendingIdle) {
                selection().set(List.of(body.spawnData().handle()));
                pendingIdle = -1;
                return;
            }
        }
        if (--pendingIdleTicks <= 0) {
            pendingIdle = -1;
        }
    }

    /** Where the selected building's rally marker stands this frame (PROD-03.3): world x, ground y, z; null if none. */
    public double @Nullable [] rallyShown() {
        return rally.shown();
    }

    /** The building template placement mode is armed with, its net index; -1 when it is not armed. For scenarios. */
    public int placementTemplate() {
        return placement.template();
    }

    /** The command card's build button: placement armed with building template {@code template} (BLD-02). */
    public void armPlacement(int template) {
        placement.arm(template);
    }

    /**
     * The building the pick at window points stands in, by its blueprint's columns, or null: a picked unit, the sky or
     * open ground pick none (UI-06).
     */
    BuildingState.@Nullable Entry buildingAt(@Nullable Pick pick) {
        MatchJoin join = StrategosClient.match();
        if (pick == null || pick.entityId() >= 0 || join == null) {
            return null;
        }
        return StrategosClient.model()
                .buildingAt(
                        StrategosClient.templates(),
                        (int) Math.floor(pick.x()) - join.originX(),
                        (int) Math.floor(pick.z()) - join.originZ());
    }

    /** Orders sent from the RTS screen and in first person, for scenarios. */
    public int ordersSent() {
        return orders.ordersSent();
    }

    void preview(boolean on) {
        orders.preview(on);
    }

    /** The context order for the selection to what the cursor at window points picks; see {@link RtsOrders#order}. */
    public void order(double x, double y, boolean shift) {
        orders.order(x, y, shift);
    }

    /** In first person: the match's body under the crosshair (CAM-07.2); null for none. */
    public @Nullable UnitEntity crosshairUnit() {
        return orders.crosshairUnit();
    }

    /** The player's selection; the faction it may select follows the seat. */
    public Selection selection() {
        MatchJoin join = StrategosClient.match();
        int faction = join == null ? -1 : join.slot().value();
        if (selection.faction() != faction) {
            selection = new Selection(faction);
        }
        return selection;
    }

    public ControlGroups groups() {
        return groups;
    }

    /** The unit bodies near the camera with their feet and heads projected in window points, for selection. */
    List<Seen> seen() {
        View v = view;
        Minecraft mc = Minecraft.getInstance();
        List<Seen> out = new ArrayList<>();
        if (v == null || mc.level == null) {
            return out;
        }
        MatchJoin join = StrategosClient.match();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof UnitEntity body) || body.spawnData() == null) {
                continue;
            }
            UnitSpawnData data = body.spawnData();
            if ((join != null && data.matchId() != join.matchId())
                    || e.distanceToSqr(v.camX(), v.camY(), v.camZ()) > TRACKING_REACH * TRACKING_REACH) {
                continue;
            }
            double[] feet = Picking.project(v, e.getX(), e.getY(), e.getZ());
            double[] head = Picking.project(v, e.getX(), e.getBoundingBox().maxY, e.getZ());
            out.add(new Seen(
                    data.handle(),
                    data.faction(),
                    data.templateIndex(),
                    feet == null ? Double.NaN : feet[0],
                    feet == null ? Double.NaN : feet[1],
                    head == null ? Double.NaN : head[0],
                    head == null ? Double.NaN : head[1]));
        }
        return out;
    }

    /** The seen unit whose body {@code pick} hit, or null. */
    @Nullable Seen seenAt(@Nullable Pick pick) {
        Minecraft mc = Minecraft.getInstance();
        if (pick == null || pick.entityId() < 0 || mc.level == null) {
            return null;
        }
        Entity e = mc.level.getEntity(pick.entityId());
        if (!(e instanceof UnitEntity body) || body.spawnData() == null) {
            return null;
        }
        UnitSpawnData d = body.spawnData();
        return new Seen(d.handle(), d.faction(), d.templateIndex(), 0, 0, 0, 0);
    }

    /** Pans the camera to the centre of the units {@code handles} names that have bodies here. */
    void centreOn(List<Integer> handles) {
        CameraRig r = rig;
        Minecraft mc = Minecraft.getInstance();
        if (r == null || mc.level == null || handles.isEmpty()) {
            return;
        }
        double x = 0;
        double z = 0;
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && handles.contains(body.spawnData().handle())) {
                x += e.getX();
                z += e.getZ();
                n++;
            }
        }
        if (n > 0) {
            centreOn(x / n, z / n);
        }
    }

    /**
     * The camera to a point: a pan at the rig's cap when it is near, else a {@code camera_jump} the server answers
     * with a teleport, which the rig then takes over (CAM-03).
     */
    public void centreOn(double x, double z) {
        CameraRig r = rig;
        Minecraft mc = Minecraft.getInstance();
        if (r == null) {
            return;
        }
        if (Math.hypot(x - r.focusX(), z - r.focusZ()) <= FAR || StrategosClient.match() == null) {
            r.panTo(x, z);
            return;
        }
        CameraPose eye = r.eyeFor(x, z, ground(x, z, r.pose().y()));
        if (ClientPayloads.toServer(new CameraJumpPayload(
                new CameraJump(eye.x(), eye.y(), eye.z(), (float) eye.yaw(), (float) eye.pitch())))) {
            pendingJump = new double[] {x, z};
        } else if (mc.player != null) {
            r.panTo(x, z);
        }
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
        outlines(Minecraft.getInstance());
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

    /** Where the RTS screen last saw the cursor, in GUI pixels; the HUD's hover tooltip follows it. */
    void cursor(double guiX, double guiY) {
        cursor = new double[] {guiX, guiY};
    }

    /** The cursor in GUI pixels as the RTS screen last saw it, or null before it moved. */
    public double @Nullable [] cursor() {
        return cursor;
    }

    private void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (rig != null) {
                leave();
            }
            return;
        }
        GameType mode = mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
        if (mode != lastMode) {
            lastMode = mode;
            modeChanged(mode);
        }
        if (rig == null) {
            while (keys.mapping(Action.TOGGLE_RTS).consumeClick()) {
                if (firstPerson) {
                    ClientPayloads.toServer(new ModeSwitchPayload(ModeSwitch.BACK));
                } else {
                    enter();
                }
            }
            while (keys.mapping(Action.ORDER_HERE).consumeClick()) {
                if (firstPerson) {
                    orders.orderHere(mc);
                }
            }
            while (keys.mapping(Action.FOLLOW_ME).consumeClick()) {
                if (firstPerson) {
                    orders.followMe();
                }
            }
            MatchJoin join = StrategosClient.match();
            if (join != null && join.matchId() != autoEntered && mc.player.isSpectator()) {
                autoEntered = join.matchId(); // a seat makes a commander: RTS mode, once per match joined
                enter();
            }
            return;
        }
        ticks++;
        selectPendingIdle();
        outlines(mc);
        orders.showPreview(mc, ticks);
        orders.showMarkers(mc, ticks);
        placement.tick(mc, ticks);
        if (mc.screen == null) { // chat, the pause menu or anything else closed over it: the RTS screen comes back
            mc.setScreen(new RtsScreen(this));
        }
    }

    /**
     * The server switched this player's game mode: out of spectator mode while in RTS mode is the step down into first
     * person (the selection and groups stay), and spectator mode again after it is the way back.
     */
    private void modeChanged(@Nullable GameType mode) {
        if (rig != null && mode != null && mode != GameType.SPECTATOR) {
            leave();
            firstPerson = true;
        } else if (rig == null && firstPerson && mode == GameType.SPECTATOR) {
            firstPerson = false;
            enter();
        }
    }

    /** Asks the server for first person where the camera looks (CAM-05); it answers by switching the game mode. */
    void toFirstPerson() {
        CameraRig r = rig;
        if (r != null && StrategosClient.match() != null) {
            ClientPayloads.toServer(new ModeSwitchPayload(new ModeSwitch(true, r.focusX(), r.focusZ())));
        }
    }

    /** Whether this player stepped down into first person and has not come back. */
    public boolean firstPerson() {
        return firstPerson;
    }

    /** Selected bodies glow in their faction's colour (UNIT-03), others not. */
    private void outlines(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        Selection s = selection();
        MatchJoin join = StrategosClient.match();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body) {
                UnitSpawnData data = body.spawnData();
                body.selectedOnClient(data != null
                        && rig != null
                        && (join == null || data.matchId() == join.matchId())
                        && s.contains(data.handle()));
            }
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
        CameraPose was = r.pose();
        double eyeY = player.getY() + player.getEyeHeight();
        if (Math.sqrt(player.distanceToSqr(was.x(), was.y() - player.getEyeHeight(), was.z())) > TELEPORTED) {
            double[] jump = pendingJump;
            pendingJump = null;
            if (jump != null) {
                r.jumpTo(jump[0], jump[1], eyeY - r.height());
            } else {
                r.rebaseOnEye(player.getX(), eyeY, player.getZ());
            }
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
        Pick found = find(x, y);
        lastPick = found;
        return found;
    }

    /** What {@link #pick} finds, without remembering it: the HUD's hover tooltip asks every tick. */
    public @Nullable Pick find(double x, double y) {
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
