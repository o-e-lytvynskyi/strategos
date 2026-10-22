package dev.strategos.mod.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.DebugLayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * The movement overlay (PATH-10): each surface near the player as a translucent square at its floor, coloured by its
 * kind, a deeper layer darker; each component as a smaller square of its own colour on top; each unit's path as a
 * line from where it stands through its waypoints, coloured by how its search ended, grey while it waits for one; and
 * over each unit a tick, cyan while it moves, grey while idle, boxed orange after {@value #STUCK} ticks without progress
 * and red after {@value #VERY_STUCK}, or in its ladder rung's colour once the ladder (T23) reports one.
 */
final class MovementOverlay implements DebugOverlayHost.WorldOverlay {
    /** The stuck ladder's first rungs, repath and a short move (spec §5.11). */
    static final int STUCK = 40;

    static final int VERY_STUCK = 100;

    private final ClientDebugState state;

    MovementOverlay(ClientDebugState state) {
        this.state = state;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera) {
        PoseStack.Pose p = pose.last();
        if (state.draws(DebugLayer.SURFACES) || state.draws(DebugLayer.COMPONENTS)) {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            if (state.draws(DebugLayer.SURFACES)) {
                for (DebugFeedMsg.Cells cells : state.surfaces()) {
                    cells(quads, p, camera, cells, false);
                }
            }
            if (state.draws(DebugLayer.COMPONENTS)) {
                for (DebugFeedMsg.Cells cells : state.components()) {
                    cells(quads, p, camera, cells, true);
                }
            }
            buffers.endBatch(RenderType.debugQuads());
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (state.draws(DebugLayer.PATHS)) {
            paths(lines, p, camera);
        }
        if (state.draws(DebugLayer.UNITS)) {
            marks(lines, pose, camera);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void cells(
            VertexConsumer quads, PoseStack.Pose p, Vec3 camera, DebugFeedMsg.Cells cells, boolean components) {
        DebugFeedMsg.Origin o = cells.origin();
        double pageX = o.x() + cells.pageX() * 16.0 - camera.x;
        double pageZ = o.z() + cells.pageZ() * 16.0 - camera.z;
        float lift = components ? 0.04F : 0.02F;
        float inset = components ? 0.3F : 0.03F;
        for (DebugFeedMsg.Cell cell : cells.cells()) {
            float x = (float) (pageX + (cell.column() >> 4)); // columns are x * 16 + z
            float z = (float) (pageZ + (cell.column() & 15));
            float y = (float) (o.y() + cell.floor() / 16.0 - camera.y) + lift;
            int argb = components ? componentColor(cell.value()) : kindColor(cell.value());
            quads.addVertex(p, x + inset, y, z + inset).setColor(argb);
            quads.addVertex(p, x + inset, y, z + 1 - inset).setColor(argb);
            quads.addVertex(p, x + 1 - inset, y, z + 1 - inset).setColor(argb);
            quads.addVertex(p, x + 1 - inset, y, z + inset).setColor(argb);
        }
    }

    private void paths(VertexConsumer lines, PoseStack.Pose p, Vec3 camera) {
        DebugFeedMsg.Origin o = state.paths().origin();
        for (DebugFeedMsg.PathLine line : state.paths().items()) {
            int argb = line.waiting() ? 0xFFAAAAAA : statusColor(line.status());
            DebugFeedMsg.Point from = line.points().get(0);
            for (int i = 1; i < line.points().size(); i++) {
                DebugFeedMsg.Point to = line.points().get(i);
                line(
                        lines,
                        p,
                        at(o.x(), from.x(), camera.x),
                        at(o.y(), from.y(), camera.y) + 0.1F,
                        at(o.z(), from.z(), camera.z),
                        at(o.x(), to.x(), camera.x),
                        at(o.y(), to.y(), camera.y) + 0.1F,
                        at(o.z(), to.z(), camera.z),
                        argb);
                from = to;
            }
        }
    }

    private void marks(VertexConsumer lines, PoseStack pose, Vec3 camera) {
        DebugFeedMsg.Origin o = state.marks().origin();
        PoseStack.Pose p = pose.last();
        for (DebugFeedMsg.Mark mark : state.marks().items()) {
            float x = at(o.x(), mark.at().x(), camera.x);
            float y = at(o.y(), mark.at().y(), camera.y);
            float z = at(o.z(), mark.at().z(), camera.z);
            line(lines, p, x, y + 2.1F, z, x, y + 2.6F, z, mark.tier() > 0 ? 0xFF55FFFF : 0xFF888888);
            int stuck = stuckColor(mark);
            if (stuck != 0) {
                LevelRenderer.renderLineBox(
                        pose,
                        lines,
                        x - 0.45,
                        y,
                        z - 0.45,
                        x + 0.45,
                        y + 1.9,
                        z + 0.45,
                        ((stuck >> 16) & 0xFF) / 255F,
                        ((stuck >> 8) & 0xFF) / 255F,
                        (stuck & 0xFF) / 255F,
                        1F);
            }
        }
    }

    /** A coordinate of the frame: an origin's block plus a point's sixteenths, less the camera's. */
    private static float at(int origin, int sixteenths, double camera) {
        return (float) (origin + sixteenths / 16.0 - camera);
    }

    private static void line(
            VertexConsumer lines,
            PoseStack.Pose p,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-4F) {
            return;
        }
        lines.addVertex(p, x0, y0, z0).setColor(argb).setNormal(p, dx / length, dy / length, dz / length);
        lines.addVertex(p, x1, y1, z1).setColor(argb).setNormal(p, dx / length, dy / length, dz / length);
    }

    /** A surface's kind (bits 0–2 of its value, the core's {@code Surface} kinds), a deeper layer darker. */
    static int kindColor(int value) {
        int rgb = switch (value & 7) {
            case 0 -> 0x40C040; // ground
            case 1 -> 0x40C0E0; // shallow water
            case 2 -> 0x2040C0; // deep water
            case 3 -> 0xC08040; // door
            case 4 -> 0xD0C040; // slow ground
            case 5 -> 0xE02020; // hazard
            default -> 0xC0C0C0;
        };
        int alpha = ((value >> 3) & 3) == 0 ? 0x50 : 0x30;
        return (alpha << 24) | rgb;
    }

    /** A component's colour: its label's hash spread over the hues, opaque enough to read over its surface. */
    static int componentColor(int label) {
        int h = label * 0x9E3779B1;
        return (0xA0 << 24) | ((h >>> 8) & 0xFFFFFF) | 0x404040;
    }

    /** How a path's search ended: reaches the goal, partial, none, busy. */
    static int statusColor(int status) {
        return switch (status) {
            case 0 -> 0xFF60FF60;
            case 1 -> 0xFFFFFF40;
            case 2 -> 0xFFFF4040;
            default -> 0xFFFF40FF;
        };
    }

    /** A unit's box: its ladder rung's colour, or by its ticks without progress before the ladder reports rungs; 0 none. */
    static int stuckColor(DebugFeedMsg.Mark mark) {
        if (mark.rung() > 0) {
            return rungColor(mark.rung());
        }
        if (mark.stuck() >= VERY_STUCK) {
            return 0xFFFF3030;
        }
        return mark.stuck() >= STUCK ? 0xFFFFA020 : 0;
    }

    /** The ladder's rungs (spec §5.11), from repath, yellow, to failing the order, red. */
    static int rungColor(int rung) {
        return switch (rung) {
            case 1 -> 0xFFFFFF40;
            case 2 -> 0xFFFFC040;
            case 3 -> 0xFFFF8040;
            case 4 -> 0xFFFF4040;
            default -> 0xFFC00000;
        };
    }
}
