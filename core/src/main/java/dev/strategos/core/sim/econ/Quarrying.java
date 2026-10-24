package dev.strategos.core.sim.econ;

import dev.strategos.core.sim.BlockCracked;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.world.WritePriority;

/**
 * Mining an outcrop (ECO-07, 04 §11.2): what a quarry worker takes comes off the outcrop's next block in its order,
 * top-down and outside-in; once a block's share is gone the block goes, through the writer, and the core's terrain
 * gets the air at once, as felling does, so no page is rebuilt from the world. The order never reaches below the
 * ground round the outcrop, so the hill lowers and no pit opens. The next block shows its crack.
 */
public final class Quarrying {
    private Quarrying() {}

    /** Takes up to {@code want} thousandths from outcrop {@code node}, removing every block that has given its share. */
    public static long take(MatchState s, int node, long want) {
        NodeStore nodes = s.nodes();
        int crackBefore = Felling.crack(nodes, node);
        int goneBefore = nodes.gone(node);
        long given = nodes.take(node, want);
        long[] blocks = nodes.blocks(node);
        long per = Math.max(1, nodes.perBlock(node));
        long keep = (nodes.amount(node) + per - 1) / per; // the blocks an outcrop with this much left still shows
        while (blocks.length - nodes.gone(node) > keep) {
            long next = blocks[nodes.gone(node)];
            int x = NodeStore.x(next);
            int y = NodeStore.y(next);
            int z = NodeStore.z(next);
            s.outputs().add(new WorldEffect(node, WritePriority.NODE_REMOVAL, x, y, z, WorldEffect.AIR, 0));
            s.editVoxel(x, y, z, BlockClass.AIR);
            nodes.removeTop(node);
        }
        int crackAfter = Felling.crack(nodes, node);
        if ((crackAfter != crackBefore || nodes.gone(node) != goneBefore)
                && crackAfter != BlockCracked.NONE
                && nodes.gone(node) < blocks.length) {
            long next = blocks[nodes.gone(node)];
            s.outputs().add(new BlockCracked(NodeStore.x(next), NodeStore.y(next), NodeStore.z(next), crackAfter));
        }
        return given;
    }
}
