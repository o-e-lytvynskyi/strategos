package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where a blueprint stands (BLD-01): its anchor, the match-local cell of its corner (0, 0), and a rotation in quarter
 * turns clockwise seen from above; a blueprint cell (x, z) of an {@code sizeX × sizeZ} blueprint lands at
 * {@link #worldX}, {@link #worldZ}. Block states are not rotated: M3's placeholders use only blocks without a facing.
 */
@CoreApi
public record Footprint(int anchorX, int anchorZ, int rotation, int sizeX, int sizeZ) {
    public Footprint {
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("rotation " + rotation + " outside 0..3");
        }
    }

    public static Footprint of(Blueprint b, int anchorX, int anchorZ, int rotation) {
        return new Footprint(anchorX, anchorZ, rotation, b.sizeX(), b.sizeZ());
    }

    /** The width along match X: the blueprint's X size, or its Z size when turned a quarter. */
    public int spanX() {
        return rotation % 2 == 0 ? sizeX : sizeZ;
    }

    public int spanZ() {
        return rotation % 2 == 0 ? sizeZ : sizeX;
    }

    public int worldX(int x, int z) {
        return anchorX
                + switch (rotation) {
                    case 0 -> x;
                    case 1 -> sizeZ - 1 - z;
                    case 2 -> sizeX - 1 - x;
                    default -> z;
                };
    }

    public int worldZ(int x, int z) {
        return anchorZ
                + switch (rotation) {
                    case 0 -> z;
                    case 1 -> x;
                    case 2 -> sizeZ - 1 - z;
                    default -> sizeX - 1 - x;
                };
    }

    /** The match-local columns the blueprint covers, each {@code x << 16 | z}, ascending. */
    public int[] columns(Blueprint b) {
        List<Integer> cells = new ArrayList<>();
        for (int k = 0; k < b.count(); k++) {
            int cell = (worldX(b.x(k), b.z(k)) << 16) | worldZ(b.x(k), b.z(k));
            if (!cells.contains(cell)) {
                cells.add(cell);
            }
        }
        int[] out = cells.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(out);
        return out;
    }
}
