package dev.strategos.mod.client.rts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.build.ClientPlacement;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
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
 * Placement mode (BLD-02, M3 spec §2.8): armed with a building template, the cursor carries its ghost, the blueprint's
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
        List<Integer> buildings = CommandCard.buildings(StrategosClient.templates());
        int next = buildings.indexOf(armed) + 1;
        armed = next < buildings.size() ? buildings.get(next) : -1;
        ghost = null;
    }

    /** The command card's build button: placement armed with {@code template}. */
    void arm(int template) {
        armed = template;
        ghost = null;
    }

    void rotate() {
        if (armed()) {
            rotation = (rotation + 1) % 4;
            ghost = null;
        }
    }

    void cancel() {
        armed = -1;
        ghost = null;
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

    /** A left click: the placement goes to the server with the selection as builders; Shift keeps the mode armed. */
    boolean place(boolean shift) {
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
        Ghost g = ghost;
        MatchJoin join = StrategosClient.match();
        if (g == null
                || join == null
                || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)) {
            return;
        }
        Blueprint b = StrategosClient.templates().blueprint(g.template());
        if (b == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose p = event.getPoseStack().last();
        Vec3 cam = event.getCamera().getPosition();
        List<Integer> cells = g.verdict().cells();
        for (int k = 0; k < b.count(); k++) {
            int x = g.at().worldX(b.x(k), b.z(k));
            int z = g.at().worldZ(b.x(k), b.z(k));
            int at = cells.indexOf((x << 16) | z);
            boolean ok = at < 0 ? g.verdict().ok() : g.verdict().perCell().get(at) == null;
            box(
                    quads,
                    p,
                    (float) (join.originX() + x - cam.x),
                    (float) (g.baseY() + b.y(k) - cam.y),
                    (float) (join.originZ() + z - cam.z),
                    ok ? OK : REFUSED);
        }
        buffers.endBatch(RenderType.debugQuads());
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
    }
}
