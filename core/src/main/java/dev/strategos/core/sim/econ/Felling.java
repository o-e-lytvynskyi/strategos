package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.BlockCracked;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.world.WritePriority;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Felling a real tree (ECO-03, ADR-0024): what a gatherer takes leaves the tree in thousandths; whenever a log's worth
 * has gone, the highest standing log goes too, through the block writer and the core's own terrain at once. Then the
 * canopy thins by vanilla's own rule, which the writer's flags keep vanilla from applying (LeavesBlock.java:29, 105–125):
 * a leaf more than six leaf steps (the six faces) from every standing log of any tree decays, so leaves a neighbouring
 * tree still holds stay. Decayed leaves go as cosmetic writes, under the writer's budget.
 */
@CoreApi
public final class Felling {
    /** Vanilla's {@code LeavesBlock.DECAY_DISTANCE}: a leaf at this distance from every log decays. */
    public static final int DECAY_DISTANCE = 7;

    private Felling() {}

    /**
     * Takes up to {@code want} thousandths from tree {@code node}; what it gave. Logs and leaves follow, and the next log's
     * crack shows how much of it has gone (AUD-04).
     */
    public static long take(MatchState s, int node, long want) {
        NodeStore nodes = s.nodes();
        int crackBefore = crack(nodes, node);
        int goneBefore = nodes.gone(node);
        long given = nodes.take(node, want);
        long[] blocks = nodes.blocks(node);
        long per = Math.max(1, nodes.perBlock(node));
        long keep = (nodes.amount(node) + per - 1) / per; // the logs a tree with this much left still shows
        boolean felled = false;
        while (blocks.length - nodes.gone(node) > keep) {
            long top = blocks[nodes.gone(node)];
            remove(s, node, top, WritePriority.NODE_REMOVAL);
            nodes.removeTop(node);
            felled = true;
        }
        if (felled) {
            thin(s, node, blocks);
        }
        int crackAfter = crack(nodes, node);
        if ((crackAfter != crackBefore || nodes.gone(node) != goneBefore)
                && crackAfter != BlockCracked.NONE
                && nodes.gone(node) < blocks.length) {
            long top = blocks[nodes.gone(node)];
            s.outputs().add(new BlockCracked(NodeStore.x(top), NodeStore.y(top), NodeStore.z(top), crackAfter));
        }
        return given;
    }

    /**
     * The crack of tree {@code node}'s next log, 0 to 9 by how much of its share has gone, or none when its share is
     * whole or nothing is left. A felled log's crack goes with the block.
     */
    public static int crack(NodeStore nodes, int node) {
        long amount = nodes.amount(node);
        long per = Math.max(1, nodes.perBlock(node));
        if (amount <= 0) {
            return BlockCracked.NONE;
        }
        long inTop = amount - ((amount + per - 1) / per - 1) * per; // what the next log still holds: (0, per]
        if (inTop >= per) {
            return BlockCracked.NONE;
        }
        return (int) Math.min(9, (per - inTop) * 10 / per);
    }

    /** The leaves round tree {@code node} that no standing log holds any more go. */
    static void thin(MatchState s, int node, long[] treeBlocks) {
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        for (long b : treeBlocks) {
            x0 = Math.min(x0, NodeStore.x(b));
            x1 = Math.max(x1, NodeStore.x(b));
            z0 = Math.min(z0, NodeStore.z(b));
            z1 = Math.max(z1, NodeStore.z(b));
            y0 = Math.min(y0, NodeStore.y(b));
            y1 = Math.max(y1, NodeStore.y(b));
        }
        int r = DECAY_DISTANCE;
        List<long[]> leaves = s.materialsIn(x0 - r, y0 - r, z0 - r, x1 + r, y1 + r, z1 + r, MaterialKind.LEAVES);
        if (leaves.isEmpty()) {
            return;
        }
        // logs out to twice the reach hold leaves inside it: a leaf six steps from a log can be six from the box
        List<long[]> logs =
                s.materialsIn(x0 - 2 * r, y0 - 2 * r, z0 - 2 * r, x1 + 2 * r, y1 + 2 * r, z1 + 2 * r, MaterialKind.LOG);
        for (long p : decaying(positions(logs), positions(leaves))) {
            remove(s, node, p, WritePriority.COSMETIC);
        }
    }

    /**
     * The leaves (packed, ascending) farther than six leaf steps from every log: a search outward from the logs through
     * the six faces of leaves, as vanilla's distance spreads. Ascending, so the writes go in a fixed order.
     */
    public static long[] decaying(long[] logs, long[] leaves) {
        int[] distance = new int[leaves.length];
        Arrays.fill(distance, DECAY_DISTANCE);
        int[] queue = new int[leaves.length];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < leaves.length; i++) {
            if (touches(leaves[i], logs)) {
                distance[i] = 1;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++];
            if (distance[i] >= DECAY_DISTANCE - 1) {
                continue;
            }
            for (long n : faces(leaves[i])) {
                int j = Arrays.binarySearch(leaves, n);
                if (j >= 0 && distance[j] > distance[i] + 1) {
                    distance[j] = distance[i] + 1;
                    queue[tail++] = j;
                }
            }
        }
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < leaves.length; i++) {
            if (distance[i] >= DECAY_DISTANCE) {
                out.add(leaves[i]);
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    private static boolean touches(long leaf, long[] logs) {
        for (long n : faces(leaf)) {
            if (Arrays.binarySearch(logs, n) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static long[] faces(long p) {
        int x = NodeStore.x(p);
        int y = NodeStore.y(p);
        int z = NodeStore.z(p);
        return new long[] {
            NodeStore.pack(x + 1, y, z),
            NodeStore.pack(Math.max(0, x - 1), y, z),
            NodeStore.pack(x, y + 1, z),
            NodeStore.pack(x, y - 1, z),
            NodeStore.pack(x, y, z + 1),
            NodeStore.pack(x, y, Math.max(0, z - 1))
        };
    }

    private static long[] positions(List<long[]> found) {
        long[] out = new long[found.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = found.get(i)[0];
        }
        Arrays.sort(out);
        return out;
    }

    private static void remove(MatchState s, int node, long p, WritePriority priority) {
        int x = NodeStore.x(p);
        int y = NodeStore.y(p);
        int z = NodeStore.z(p);
        s.outputs().add(new WorldEffect(node, priority, x, y, z, WorldEffect.AIR, 0));
        s.editVoxel(x, y, z, BlockClass.AIR);
    }
}
