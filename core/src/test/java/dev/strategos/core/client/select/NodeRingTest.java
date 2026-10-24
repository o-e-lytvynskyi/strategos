package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Footprint;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * ART-02's node marker in L0: the band round a 3 × 2 footprint has a piece on each of the 14 ring cells, every piece
 * outside the footprint, touching it, a quarter block wide, and on the cell it lies in.
 */
class NodeRingTest {
    @Test
    void aPieceOnEveryRingCellHuggingTheFootprint() {
        Footprint at = new Footprint(10, 20, 0, 3, 2);
        List<NodeRing.Piece> ring = NodeRing.of(at);
        Set<Integer> cells = new TreeSet<>();
        for (NodeRing.Piece p : ring) {
            cells.add(p.cellX() * 1000 + p.cellZ());
            assertThat(p.x0()).as("its own cell").isBetween((double) p.cellX(), p.cellX() + 1.0);
            assertThat(p.z1()).isBetween((double) p.cellZ(), p.cellZ() + 1.0);
            boolean inside = p.cellX() >= 10 && p.cellX() < 13 && p.cellZ() >= 20 && p.cellZ() < 22;
            assertThat(inside).as("outside the footprint").isFalse();
            double gapX = Math.max(10 - p.x1(), p.x0() - 13);
            double gapZ = Math.max(20 - p.z1(), p.z0() - 22);
            assertThat(Math.max(gapX, gapZ)).as("touching it").isLessThanOrEqualTo(0);
            assertThat(Math.min(p.x1() - p.x0(), p.z1() - p.z0())).isEqualTo(NodeRing.WIDTH);
        }
        assertThat(ring).hasSize(14);
        assertThat(cells).as("one piece a ring cell").hasSize(14);
    }
}
