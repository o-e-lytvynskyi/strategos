package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * The {@code vision_delta} payload (FOW-07, 04 §12.2): the viewer's faction's visible bitmap and explored cells,
 * run-length encoded. A full frame at join, resume and the first send; after that, only the cells that changed since
 * the last send. Sent at 2 Hz to the faction's seated players.
 *
 * <p>Wire: matchId (varint), tick (varlong), cellsX (varint), cellsZ (varint), full (byte).
 * If full: visibleCount (varint), each index (varint delta); exploredCount (varint), each index (varint delta).
 * If delta: visibleFlipCount (varint), each flipped index (varint delta); newExploredCount (varint),
 * each newly explored index (varint delta).
 */
@CoreApi
public final class VisionDelta {
    private static final int MAX_CELLS = 256 * 256;

    private final int matchId;
    private final long tick;
    private final int cellsX;
    private final int cellsZ;
    private final boolean full;
    private final int[] visible;
    private final int[] explored;

    public VisionDelta(int matchId, long tick, int cellsX, int cellsZ, boolean full, int[] visible, int[] explored) {
        this.matchId = matchId;
        this.tick = tick;
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
        this.full = full;
        this.visible = visible;
        this.explored = explored;
    }

    public int matchId() {
        return matchId;
    }

    public long tick() {
        return tick;
    }

    public int cellsX() {
        return cellsX;
    }

    public int cellsZ() {
        return cellsZ;
    }

    public boolean full() {
        return full;
    }

    public int[] visible() {
        return visible;
    }

    public int[] explored() {
        return explored;
    }

    public static final WireCodec<VisionDelta> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, VisionDelta v) {
            out.writeVarInt(v.matchId).writeVarLong(v.tick);
            out.writeVarInt(v.cellsX).writeVarInt(v.cellsZ);
            out.writeBoolean(v.full);
            writeIndices(out, v.visible);
            writeIndices(out, v.explored);
        }

        @Override
        public VisionDelta read(WireReader in) {
            int matchId = in.readVarInt();
            long tick = in.readVarLong();
            int cellsX = in.readVarInt(256);
            int cellsZ = in.readVarInt(256);
            boolean full = in.readBoolean();
            int[] visible = readIndices(in);
            int[] explored = readIndices(in);
            return new VisionDelta(matchId, tick, cellsX, cellsZ, full, visible, explored);
        }

        @Override
        public int maxSize() {
            return 4 + 10 + 2 + 2 + 1 + 5 + MAX_CELLS * 3 + 5 + MAX_CELLS * 3;
        }

        private void writeIndices(WireWriter out, int[] indices) {
            out.writeVarInt(indices.length);
            int prev = -1;
            for (int idx : indices) {
                out.writeVarInt(idx - prev - 1);
                prev = idx;
            }
        }

        private int[] readIndices(WireReader in) {
            int count = in.readVarInt(MAX_CELLS);
            int[] indices = new int[count];
            int prev = -1;
            for (int i = 0; i < count; i++) {
                prev = prev + 1 + in.readVarInt();
                indices[i] = prev;
            }
            return indices;
        }
    };
}
