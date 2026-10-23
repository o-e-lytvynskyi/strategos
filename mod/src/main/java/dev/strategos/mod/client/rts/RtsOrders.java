package dev.strategos.mod.client.rts;

import dev.strategos.core.client.input.Action;
import dev.strategos.core.client.order.OrderIntents;
import dev.strategos.core.client.order.SlotPreview;
import dev.strategos.core.client.pick.CrosshairPick;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.client.rts.RtsMode.Pick;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.registry.ModDataMaps;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The orders RTS mode sends (UI-05, ORD-04, CAM-07): the right click's context order with its preview and markers,
 * stop, and the first-person keys, each one command through the {@code commands} payload. Split out of {@link RtsMode}
 * in M3 T1; client thread only.
 */
final class RtsOrders {
    private static final DustParticleOptions PREVIEW = new DustParticleOptions(new Vector3f(0.35f, 0.65f, 1f), 3f);
    private static final DustParticleOptions MARKER = new DustParticleOptions(new Vector3f(1f, 1f, 1f), 4f);
    private static final DustParticleOptions ATTACK_MARKER = new DustParticleOptions(new Vector3f(1f, 0.2f, 0.15f), 4f);
    private static final DustParticleOptions ATTACK_MOVE_MARKER =
            new DustParticleOptions(new Vector3f(1f, 0.6f, 0.1f), 4f);
    private static final DustParticleOptions GATHER_MARKER =
            new DustParticleOptions(new Vector3f(0.3f, 0.9f, 0.3f), 4f);

    private final RtsMode mode;
    private boolean previewing;
    private int clientSeq = 1;
    private int ordersSent;
    private final List<double[]> markers =
            new ArrayList<>(); // world x, y, z, ticks left, kind (0 move, 1 attack, 2 attack-move)
    private boolean attackMove;
    private @Nullable Command lastCommand;

    RtsOrders(RtsMode mode) {
        this.mode = mode;
    }

    /** Attack-move arms for the next right click; cancelling or selecting disarms it. */
    void fired(Action action) {
        if (action == Action.ATTACK_MOVE) {
            attackMove = true; // for the next right click
        } else if (action == Action.CANCEL || action == Action.SELECT || action == Action.BOX_SELECT) {
            attackMove = false;
        }
    }

    void preview(boolean on) {
        previewing = on;
    }

    /** Orders sent from the RTS screen, for scenarios. */
    int ordersSent() {
        return ordersSent;
    }

    /** The last command a right click sent, for scenarios; null before any. */
    @Nullable Command lastCommand() {
        return lastCommand;
    }

    /** Whether the next right click attack-moves (ORD-04): its key was pressed since the last order. */
    boolean attackMoveArmed() {
        return attackMove;
    }

    /** STOP: the selection's orders end (ORD-02). */
    void stop() {
        if (mode.selection().handles().isEmpty()) {
            return;
        }
        int[] handles =
                mode.selection().handles().stream().mapToInt(Integer::intValue).toArray();
        if (ClientPayloads.toServer(
                new CommandsPayload(new CommandBatch(clientSeq++, List.of(new Stop(UnitSet.of(handles)))), ""))) {
            ordersSent++;
        }
    }

    /**
     * The context order for the selection to what the cursor at window points picks (UI-05, ORD-04): an Attack on an
     * enemy's body, else a Move to the block or unit there, or with attack-move armed an AttackMove there, match-local,
     * queued with Shift; one command per click. Attack-move disarms with the click.
     */
    void order(double x, double y, boolean shift) {
        previewing = false;
        boolean armed = attackMove;
        attackMove = false;
        Pick pick = mode.pick(x, y);
        MatchJoin join = StrategosClient.match();
        Minecraft mc = Minecraft.getInstance();
        if (pick == null || join == null || mc.level == null) {
            return;
        }
        double wx = pick.entityId() < 0 ? pick.x() + 0.5 : pick.x();
        double wz = pick.entityId() < 0 ? pick.z() + 0.5 : pick.z();
        int enemy = 0;
        if (pick.entityId() >= 0
                && mc.level.getEntity(pick.entityId()) instanceof UnitEntity body
                && body.spawnData() != null
                && body.spawnData().matchId() == join.matchId()
                && body.spawnData().faction() != join.slot().value()) {
            enemy = body.spawnData().handle();
        }
        boolean resource = !armed && pick.entityId() < 0 && yieldsResource(mc, pick);
        Command command = resource
                ? OrderIntents.gather(
                        mode.selection().handles(),
                        (int) Math.floor(pick.x()) - join.originX(),
                        (int) Math.floor(pick.y()),
                        (int) Math.floor(pick.z()) - join.originZ(),
                        shift)
                : OrderIntents.context(
                        mode.selection().handles(), enemy, wx - join.originX(), wz - join.originZ(), shift, armed);
        if (command == null) {
            return;
        }
        if (ClientPayloads.toServer(new CommandsPayload(new CommandBatch(clientSeq++, List.of(command)), ""))) {
            ordersSent++;
            lastCommand = command;
            int kind = armed ? 2 : enemy != 0 ? 1 : resource ? 3 : 0;
            double my = pick.entityId() < 0 ? pick.y() + 1 : pick.y() + (enemy != 0 ? 2.2 : 0); // over a target's head
            markers.add(new double[] {wx, my, wz, shift ? 200 : 40, kind});
        }
    }

    /**
     * While the right button is held: each selected unit's place, as dust, every few ticks; forced, since a plain
     * particle further than 32 blocks from the camera, as the RTS camera always is, is never made (LevelRenderer).
     */
    void showPreview(Minecraft mc, long ticks) {
        MatchJoin join = StrategosClient.match();
        if (!previewing || join == null || mc.level == null || ticks % 3 != 0) {
            return;
        }
        Pick pick = mode.pick(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        if (pick == null) {
            return;
        }
        double tx = (pick.entityId() < 0 ? pick.x() + 0.5 : pick.x()) - join.originX();
        double tz = (pick.entityId() < 0 ? pick.z() + 0.5 : pick.z()) - join.originZ();
        List<double[]> members = new ArrayList<>();
        double radius = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && mode.selection().contains(body.spawnData().handle())) {
                members.add(new double[] {e.getX() - join.originX(), e.getZ() - join.originZ()});
                var template =
                        StrategosClient.templates().byIndex(body.spawnData().templateIndex());
                radius = Math.max(radius, template == null ? 0.35 : template.radius());
            }
        }
        double y = pick.entityId() < 0 ? pick.y() + 1.1 : pick.y() + 0.1;
        for (double[] place : SlotPreview.places(members, radius, tx, tz, join.sizeX(), join.sizeZ())) {
            mc.level.addParticle(PREVIEW, true, place[0] + join.originX(), y, place[1] + join.originZ(), 0, 0, 0);
        }
    }

    /** Whether the block picked has a {@code strategos:resource_yield} entry, which the server syncs (ECO-05). */
    private static boolean yieldsResource(Minecraft mc, Pick pick) {
        if (mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(BlockPos.containing(pick.x(), pick.y(), pick.z()));
        return state.getBlockHolder().getData(ModDataMaps.RESOURCE_YIELD) != null;
    }

    /**
     * The clicked point and queued waypoints, as dust, for a while: white a move, red an attack, orange attack-move,
     * green gathering.
     */
    void showMarkers(Minecraft mc, long ticks) {
        if (mc.level == null) {
            return;
        }
        markers.removeIf(m -> --m[3] <= 0);
        if (ticks % 4 == 0) {
            for (double[] m : markers) {
                DustParticleOptions dust =
                        m[4] == 1 ? ATTACK_MARKER : m[4] == 2 ? ATTACK_MOVE_MARKER : m[4] == 3 ? GATHER_MARKER : MARKER;
                mc.level.addParticle(dust, true, m[0], m[1] + 0.2, m[2], 0, 0.05, 0);
            }
        }
    }

    /** In first person: the selection moves to the block the player looks at, up to 128 blocks away (CAM-05). */
    void orderHere(Minecraft mc) {
        LocalPlayer player = mc.player;
        MatchJoin join = StrategosClient.match();
        if (player == null || mc.level == null || join == null) {
            return;
        }
        UnitEntity looked = crosshairUnit();
        UnitSpawnData data = looked == null ? null : looked.spawnData();
        Command command;
        if (data != null && data.faction() != join.slot().value()) {
            command = OrderIntents.context(mode.selection().handles(), data.handle(), 0, 0, false, false); // CAM-07
        } else {
            Vec3 eye = player.getEyePosition();
            Vec3 far = eye.add(player.getViewVector(1).scale(128));
            BlockHitResult hit =
                    mc.level.clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            double wx = hit.getBlockPos().getX() + 0.5;
            double wz = hit.getBlockPos().getZ() + 0.5;
            command = OrderIntents.move(mode.selection().handles(), wx - join.originX(), wz - join.originZ(), false);
        }
        send(command);
    }

    /** In first person: the selection follows the player, keeping places behind it (CAM-07). */
    void followMe() {
        send(OrderIntents.follow(mode.selection().handles()));
    }

    private void send(@Nullable Command command) {
        if (command != null
                && ClientPayloads.toServer(new CommandsPayload(new CommandBatch(clientSeq++, List.of(command)), ""))) {
            ordersSent++;
            lastCommand = command;
        }
    }

    /**
     * In first person: the match's body under the crosshair, the nearest the look's ray enters before any block within
     * reach (CAM-07.2); null for none.
     */
    @Nullable UnitEntity crosshairUnit() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MatchJoin join = StrategosClient.match();
        if (player == null || mc.level == null || join == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Picking.Ray ray = CrosshairPick.ray(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
        Vec3 far = eye.add(player.getViewVector(1).scale(CrosshairPick.REACH));
        BlockHitResult hit =
                mc.level.clip(new ClipContext(eye, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double block =
                hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) : Double.POSITIVE_INFINITY;
        List<CrosshairPick.Body> bodies = new ArrayList<>();
        Map<Integer, UnitEntity> byHandle = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && body.spawnData().matchId() == join.matchId()
                    && !body.isDeadOrDying()
                    && e.distanceToSqr(eye) < CrosshairPick.REACH * CrosshairPick.REACH) {
                var box = e.getBoundingBox();
                bodies.add(new CrosshairPick.Body(
                        body.spawnData().handle(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
                byHandle.put(body.spawnData().handle(), body);
            }
        }
        int handle = CrosshairPick.pick(ray, bodies, block);
        return handle == 0 ? null : byHandle.get(handle);
    }
}
