package dev.strategos.core.sim.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.shared.Hasher;
import java.util.PriorityQueue;

/**
 * The order a blueprint's blocks fall in as its building loses hit points (BLD-05, 04 §11.8): every block but the two
 * lowest layers of the footprint's outer ring, which stay so no passage opens, ranked by a hash of the blueprint's id
 * and the block's index, under one rule: no block falls before the block standing on it. So the order is the same
 * every time, scattered over the building, and never leaves a block in the air.
 */
public final class DamageRanks {
    private DamageRanks() {}

    /** Blueprint {@code b}'s falling blocks, by index, the first to fall first. */
    public static int[] of(Blueprint b) {
        int sx = b.sizeX();
        int sy = b.sizeY();
        int sz = b.sizeZ();
        int[] at = new int[sx * sy * sz]; // index + 1 of the block in each cell, 0 for none
        boolean[] column = new boolean[sx * sz];
        for (int k = 0; k < b.count(); k++) {
            at[(b.x(k) * sy + b.y(k)) * sz + b.z(k)] = k + 1;
            column[b.x(k) * sz + b.z(k)] = true;
        }
        long seed = seed(b.id());
        PriorityQueue<long[]> ready =
                new PriorityQueue<>((p, q) -> p[0] != q[0] ? Long.compare(p[0], q[0]) : Long.compare(p[1], q[1]));
        int falling = 0;
        for (int k = 0; k < b.count(); k++) {
            if (kept(b, k, column)) {
                continue;
            }
            falling++;
            int y = b.y(k);
            if (y + 1 >= sy || at[(b.x(k) * sy + y + 1) * sz + b.z(k)] == 0) {
                ready.add(new long[] {hash(seed, k), k}); // nothing stands on it
            }
        }
        int[] out = new int[falling];
        int n = 0;
        while (!ready.isEmpty()) {
            int k = (int) ready.poll()[1];
            out[n++] = k;
            int y = b.y(k);
            if (y > 0) {
                int below = at[(b.x(k) * sy + y - 1) * sz + b.z(k)] - 1;
                if (below >= 0 && !kept(b, below, column)) {
                    ready.add(new long[] {hash(seed, below), below});
                }
            }
        }
        if (n != falling) {
            throw new IllegalStateException(b.id() + ": " + (falling - n) + " blocks never ranked");
        }
        return out;
    }

    /** Whether block {@code k} is in the two lowest layers of the outer ring: a column with a side open to the outside. */
    static boolean kept(Blueprint b, int k, boolean[] column) {
        if (b.y(k) > 1) {
            return false;
        }
        int x = b.x(k);
        int z = b.z(k);
        return !covered(b, column, x - 1, z)
                || !covered(b, column, x + 1, z)
                || !covered(b, column, x, z - 1)
                || !covered(b, column, x, z + 1);
    }

    private static boolean covered(Blueprint b, boolean[] column, int x, int z) {
        return x >= 0 && z >= 0 && x < b.sizeX() && z < b.sizeZ() && column[x * b.sizeZ() + z];
    }

    private static long seed(String id) {
        long h = Hasher.start(0x5354524154L);
        for (int c = 0; c < id.length(); c++) {
            h = Hasher.mix(h, id.charAt(c));
        }
        return h;
    }

    private static long hash(long seed, int k) {
        return Hasher.finish(Hasher.mix(seed, k));
    }
}
