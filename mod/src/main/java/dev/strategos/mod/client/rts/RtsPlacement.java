package dev.strategos.mod.client.rts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.build.ClientPlacement;
import dev.strategos.core.client.build.ClientWall;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.WallLine;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.PlaceWall;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.registry.ModDataMaps;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.world.ResourceYield;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Wall lines (BLD-13, M9 spec §2.10): a template with {@code Wall/segment} is placed by a drag. The press and the
 * release are the line's ends, each segment of {@link ClientWall} drawn with its own verdict (a refused one is a gap),
 * a plain click is a one-segment line, and the rotate key, which a wall does not need, toggles a gate in the line's
 * middle segment; arming the gate template itself lays gates only.
 *
 * <p>Placement mode (BLD-02, M3 spec §2.8): armed with a building template, the cursor carries its ghost, the blueprint's
 * blocks as translucent boxes, each column coloured by the verdict the client's run of {@link PlacementRules} gives it
 * (green where it may go, red where a rule fails); its build key cycles the templates, its rotate key turns it a
 * quarter, a left click sends {@link PlaceBuilding} with the selected builders (Shift queues it and keeps the mode
 * armed), a right click or Escape disarms. The command card's build page arms it (UI-06), and the build key too. The
 * verdict is recomputed on a move or a turn and once a second; the server's word stands.
 */
final class RtsPlacement {
    private static final int OK = 0x6040E040;
    private static final int REFUSED = 0x60E04040;
    private static final float INSET = 0.04F;
    /** Client sequence numbers of placements, apart from the orders' own. */
    private static final int SEQ_BASE = 1 << 30;

    private final RtsMode mode;
    private int armed = -1;
    private int rotation;
    private int seq = SEQ_BASE;
    private @Nullable Ghost ghost;
    private long checkedAt;
    private boolean gateMiddle;
    private List<ClientWall.Segment> wall = List.of();
    private int wallX1;
    private int wallZ1;
    private int wallX2;
    private int wallZ2;
    private int wallArmed = -1;

    /** The ghost: what, where, its floor layer's height, and the verdict. */
    private record Ghost(int template, Footprint at, int baseY, PlacementRules.Verdict verdict) {}

    RtsPlacement(RtsMode mode) {
        this.mode = mode;
    }

    boolean armed() {
        return armed >= 0;
    }

    /** The template armed, its net index; -1 for none. */
    int template() {
        return armed;
    }

    /** The build key: the next building template, after the last none. */
    void cycle() {
        List<Integer> buildings = CommandCard.placeable(
                StrategosClient.templates(), StrategosClient.model().faction(), StrategosClient.viewerCiv());
        int next = buildings.indexOf(armed) + 1;
        armed = next < buildings.size() ? buildings.get(next) : -1;
        ghost = null;
    }

    /** The command card's build button: placement armed with {@code template}. */
    void arm(int template) {
        armed = template;
        ghost = null;
    }

    /** Whether the armed template is a wall, placed by a drag. */
    boolean isWall() {
        return armed()
                && StrategosClient.templates().has(armed)
                && ClientWall.segmentLength(StrategosClient.templates(), armed) > 0;
    }

    void rotate() {
        if (isWall()) {
            gateMiddle = !gateMiddle;
            wall = List.of();
            wallArmed = -1;
        } else if (armed()) {
            rotation = (rotation + 1) % 4;
            ghost = null;
        }
    }

    void cancel() {
        armed = -1;
        ghost = null;
        wall = List.of();
        wallArmed = -1;
    }

    /** Each tick while armed: the ghost under the cursor, its verdict recomputed when it moved or a second passed. */
    void tick(Minecraft mc, long ticks) {
        MatchJoin join = StrategosClient.match();
        ClientLevel level = mc.level;
        if (!armed() || join == null || level == null) {
            ghost = null;
            return;
        }
        TemplateSet templates = StrategosClient.templates();
        if (isWall()) {
            wallTick(mc, join, level, ticks);
            return;
        }
        Blueprint b = templates.has(armed) ? templates.blueprint(armed) : null;
        RtsMode.Pick pick = mode.find(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        if (b == null || pick == null) {
            ghost = null;
            return;
        }
        Footprint probe = Footprint.of(b, 0, 0, rotation);
        int x = (int) Math.floor(pick.x()) - join.originX() - probe.spanX() / 2;
        int z = (int) Math.floor(pick.z()) - join.originZ() - probe.spanZ() / 2;
        Footprint at = Footprint.of(b, x, z, rotation);
        Ghost last = ghost;
        if (last != null && last.at().equals(at) && last.template() == armed && ticks - checkedAt < 20) {
            return;
        }
        checkedAt = ticks;
        PlacementRules.World world = ClientPlacement.world(
                templates, StrategosClient.model().buildings(), join.sizeX(), join.sizeZ(), new Columns(level, join));
        FactionState faction = StrategosClient.model().faction();
        PlacementRules.Params params = ClientPlacement.params(
                templates, armed, StrategosClient.resources(), faction == null ? null : faction.stock());
        PlacementRules.Verdict verdict =
                PlacementRules.check(world, b, at, join.slot().value(), params);
        double stand = Double.NEGATIVE_INFINITY;
        for (int cell : verdict.cells()) {
            double f = world.floor(cell >>> 16, cell & 0xFFFF);
            if (!Double.isNaN(f)) {
                stand = Math.max(stand, f);
            }
        }
        int baseY = stand == Double.NEGATIVE_INFINITY ? join.minY() : (int) Math.round(stand) - 1;
        ghost = new Ghost(armed, at, baseY, verdict);
    }

    /** The wall's preview while armed: from the press, if the left button is down, to the cursor; else the cursor's own segment. */
    private void wallTick(Minecraft mc, MatchJoin join, ClientLevel level, long ticks) {
        ghost = null;
        RtsMode.Pick to = mode.find(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        double[] down = mode.input() == null ? new double[0] : mode.input().dragFrom();
        RtsMode.Pick from = down.length == 2
                ? mode.find(
                        Picking.toWindow(
                                down[0],
                                mc.getWindow().getGuiScaledWidth(),
                                mc.getWindow().getScreenWidth()),
                        Picking.toWindow(
                                down[1],
                                mc.getWindow().getGuiScaledHeight(),
                                mc.getWindow().getScreenHeight()))
                : to;
        if (to == null || from == null) {
            wall = List.of();
            return;
        }
        int x1 = (int) Math.floor(from.x()) - join.originX();
        int z1 = (int) Math.floor(from.z()) - join.originZ();
        int x2 = (int) Math.floor(to.x()) - join.originX();
        int z2 = (int) Math.floor(to.z()) - join.originZ();
        if (wallArmed == armed
                && x1 == wallX1
                && z1 == wallZ1
                && x2 == wallX2
                && z2 == wallZ2
                && ticks - checkedAt < 20) {
            return;
        }
        refreshWall(level, join, x1, z1, x2, z2);
        checkedAt = ticks;
    }

    /** The preview of the line between two match-local cells, kept as the one a click or a release will send. */
    private void refreshWall(ClientLevel level, MatchJoin join, int x1, int z1, int x2, int z2) {
        TemplateSet templates = StrategosClient.templates();
        wallArmed = armed;
        wallX1 = x1;
        wallZ1 = z1;
        wallX2 = x2;
        wallZ2 = z2;
        int length = ClientWall.segmentLength(templates, armed);
        PlacementRules.World world = ClientPlacement.world(
                templates, StrategosClient.model().buildings(), join.sizeX(), join.sizeZ(), new Columns(level, join));
        FactionState faction = StrategosClient.model().faction();
        wall = ClientWall.preview(
                templates,
                armed,
                x1,
                z1,
                x2,
                z2,
                gates(x1, z1, x2, z2, length),
                world,
                join.slot().value(),
                StrategosClient.resources(),
                faction == null ? null : faction.stock());
    }

    /** The gate indices of the line: its middle segment while the gate key is on and the wall has a gate template. */
    private List<Integer> gates(int x1, int z1, int x2, int z2, int length) {
        if (!gateMiddle || ClientWall.gateOf(StrategosClient.templates(), armed) < 0) {
            return List.of();
        }
        return List.of(WallLine.count(x1, z1, x2, z2, length) / 2);
    }

    /**
     * A drag released while a wall is armed: its two ends are the line, which goes to the server; Shift keeps the mode
     * armed. False when no wall is armed, and the drag is a box select's.
     */
    boolean drag(RtsMode.@Nullable Pick from, RtsMode.@Nullable Pick to, boolean shift) {
        MatchJoin join = StrategosClient.match();
        ClientLevel level = Minecraft.getInstance().level;
        if (!isWall()) {
            return false;
        }
        if (from != null && to != null && join != null && level != null) {
            refreshWall(
                    level,
                    join,
                    (int) Math.floor(from.x()) - join.originX(),
                    (int) Math.floor(from.z()) - join.originZ(),
                    (int) Math.floor(to.x()) - join.originX(),
                    (int) Math.floor(to.z()) - join.originZ());
            sendWall(shift);
        }
        return true;
    }

    /** The preview's line goes to the server with the selection as builders; false when nothing would be laid. */
    private boolean sendWall(boolean shift) {
        UnitTemplate t = StrategosClient.templates().byIndex(armed);
        if (wall.isEmpty() || wall.stream().noneMatch(s -> s.verdict().ok())) {
            return false;
        }
        int length = ClientWall.segmentLength(StrategosClient.templates(), armed);
        PlaceWall command = new PlaceWall(
                t.id(),
                wallX1,
                wallZ1,
                wallX2,
                wallZ2,
                gates(wallX1, wallZ1, wallX2, wallZ2, length),
                UnitSet.of(mode.selection().handles().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        ClientPayloads.toServer(new CommandsPayload(new CommandBatch(seq++, List.of(command)), ""));
        if (!shift) {
            cancel();
        }
        return true;
    }

    /** A left click: the placement goes to the server with the selection as builders; Shift keeps the mode armed. */
    boolean place(boolean shift) {
        if (isWall()) {
            if (wallArmed == armed) {
                sendWall(shift);
            }
            return true; // a click on a wall's ghost is its own, sent or refused
        }
        Ghost g = ghost;
        UnitTemplate t = armed() && StrategosClient.templates().has(armed)
                ? StrategosClient.templates().byIndex(armed)
                : null;
        if (g == null || t == null) {
            return false;
        }
        PlaceBuilding command = new PlaceBuilding(
                t.id(),
                g.at().anchorX(),
                g.at().anchorZ(),
                rotation,
                UnitSet.of(mode.selection().handles().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()),
                shift ? QueueMode.QUEUE : QueueMode.REPLACE);
        ClientPayloads.toServer(new CommandsPayload(new CommandBatch(seq++, List.of(command)), ""));
        if (!shift) {
            cancel();
        }
        return true;
    }

    /** The ghost, after the translucent blocks: a box per blueprint block, coloured by its column's verdict. */
    void render(RenderLevelStageEvent event) {
        MatchJoin join = StrategosClient.match();
        if (join == null || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)) {
            return;
        }
        Ghost g = ghost;
        if (g == null && wall.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose p = event.getPoseStack().last();
        Vec3 cam = event.getCamera().getPosition();
        if (g != null) {
            Blueprint b = StrategosClient.templates().blueprint(g.template());
            if (b != null) {
                ghost(quads, p, cam, join, b, g.at(), g.baseY(), g.verdict());
            }
        }
        for (ClientWall.Segment segment : wall) {
            Blueprint b = StrategosClient.templates().blueprint(segment.template());
            if (b != null) {
                int baseY = segment.line().baseY() == Integer.MIN_VALUE ? join.minY() : segment.baseY();
                ghost(quads, p, cam, join, b, segment.at(), baseY, segment.verdict());
            }
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static void ghost(
            VertexConsumer quads,
            PoseStack.Pose p,
            Vec3 cam,
            MatchJoin join,
            Blueprint b,
            Footprint at,
            int baseY,
            PlacementRules.Verdict verdict) {
        List<Integer> cells = verdict.cells();
        for (int k = 0; k < b.count(); k++) {
            int x = at.worldX(b.x(k), b.z(k));
            int z = at.worldZ(b.x(k), b.z(k));
            int cell = cells.indexOf((x << 16) | z);
            boolean ok = cell < 0 ? verdict.ok() : verdict.perCell().get(cell) == null;
            box(
                    quads,
                    p,
                    (float) (join.originX() + x - cam.x),
                    (float) (baseY + b.y(k) - cam.y),
                    (float) (join.originZ() + z - cam.z),
                    ok ? OK : REFUSED);
        }
    }

    private static void box(VertexConsumer q, PoseStack.Pose p, float x, float y, float z, int argb) {
        float x0 = x + INSET;
        float y0 = y + INSET;
        float z0 = z + INSET;
        float x1 = x + 1 - INSET;
        float y1 = y + 1 - INSET;
        float z1 = z + 1 - INSET;
        quad(q, p, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // top
        quad(q, p, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // bottom
        quad(q, p, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // north
        quad(q, p, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // south
        quad(q, p, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // west
        quad(q, p, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // east
    }

    @SuppressWarnings("ParameterNumber")
    private static void quad(
            VertexConsumer q,
            PoseStack.Pose p,
            int argb,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float cx,
            float cy,
            float cz,
            float dx,
            float dy,
            float dz) {
        q.addVertex(p, ax, ay, az).setColor(argb);
        q.addVertex(p, bx, by, bz).setColor(argb);
        q.addVertex(p, cx, cy, cz).setColor(argb);
        q.addVertex(p, dx, dy, dz).setColor(argb);
    }

    /** The client's columns: the top standing floor of its loaded blocks, leaves skipped, water no land. */
    private record Columns(ClientLevel level, MatchJoin join) implements ClientPlacement.Columns {
        @Override
        public double floor(int x, int z) {
            int wx = join.originX() + x;
            int wz = join.originZ() + z;
            if (!level.hasChunk(SectionPos.blockToSectionCoord(wx), SectionPos.blockToSectionCoord(wz))) {
                return PlacementRules.NO_LAND;
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = join.maxY(); y >= join.minY(); y--) {
                BlockState state = level.getBlockState(pos.set(wx, y, wz));
                if (state.getFluidState().is(FluidTags.WATER)) {
                    return PlacementRules.NO_LAND;
                }
                if (state.isAir() || state.is(BlockTags.LEAVES)) {
                    continue;
                }
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty()) {
                    return y + shape.max(Direction.Axis.Y);
                }
            }
            return PlacementRules.NO_LAND;
        }

        @Override
        public boolean node(int x, int z) {
            int wx = join.originX() + x;
            int wz = join.originZ() + z;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = join.minY(); y <= join.maxY(); y++) {
                BlockState state = level.getBlockState(pos.set(wx, y, wz));
                ResourceYield entry = state.getBlockHolder().getData(ModDataMaps.RESOURCE_YIELD);
                if (entry != null && (entry.kind().equals("log") || entry.kind().equals("forage"))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean explored(int x, int z) {
            return ClientPlacement.explored(StrategosClient.model(), x, z);
        }

        @Override
        public int territory(int x, int z) { // the grid the server sent (TERR-02's prediction)
            TerritoryGrid land = StrategosClient.model().territory().grid();
            return land == null ? PlacementRules.NO_GRID : land.ownerAt(x, z);
        }
    }
}
