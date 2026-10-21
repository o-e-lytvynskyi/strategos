package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.MatchId;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** CORE-01.3: routing a block to its match through its chunk, and the 2-block overlap rule. */
class MatchRegionIndexTest {
    private static final MatchId ONE = new MatchId(1);
    private static final MatchId TWO = new MatchId(2);

    private static MatchSpace box(int x, int z, int sizeX, int sizeZ) {
        return new MatchSpace(Level.OVERWORLD, new BlockPos(x, 0, z), sizeX, sizeZ, 0, 31);
    }

    @Test
    void atFindsTheMatchThroughItsChunk() {
        MatchRegionIndex index = new MatchRegionIndex();
        index.add(ONE, box(0, 0, 40, 20));

        assertThat(index.at(new BlockPos(0, 5, 0))).isEqualTo(ONE);
        assertThat(index.at(new BlockPos(39, 5, 19)))
                .as("the far corner, in chunk (2, 1)")
                .isEqualTo(ONE);
        assertThat(index.at(new BlockPos(40, 5, 0))).as("x is half-open").isNull();
        assertThat(index.at(new BlockPos(5, 32, 5))).as("above the band").isNull();
        assertThat(index.at(new BlockPos(-1, 5, 0))).isNull();
    }

    @Test
    void spacesSharingAChunkBothRoute() {
        MatchRegionIndex index = new MatchRegionIndex();
        index.add(ONE, box(0, 0, 8, 8));
        index.add(TWO, box(10, 0, 4, 8)); // the same chunk (0, 0), a gap of 2

        assertThat(index.at(new BlockPos(3, 5, 3))).isEqualTo(ONE);
        assertThat(index.at(new BlockPos(11, 5, 3))).isEqualTo(TWO);
        assertThat(index.at(new BlockPos(9, 5, 3))).as("in the gap").isNull();
    }

    @Test
    void withMargin2AGapOf1OverlapsAndAGapOf2DoesNot() {
        MatchRegionIndex index = new MatchRegionIndex();
        index.add(ONE, box(0, 0, 16, 16));

        assertThat(index.overlapping(box(17, 0, 16, 16), MatchRegistry.MARGIN)).isEqualTo(ONE);
        assertThat(index.overlapping(box(18, 0, 16, 16), MatchRegistry.MARGIN)).isNull();
        assertThat(index.overlapping(box(0, -17, 16, 16), MatchRegistry.MARGIN))
                .as("across a chunk border, below")
                .isEqualTo(ONE);
        assertThat(index.overlapping(box(0, -18, 16, 16), MatchRegistry.MARGIN)).isNull();
        assertThat(index.overlapping(box(4, 4, 8, 8), MatchRegistry.MARGIN))
                .as("inside")
                .isEqualTo(ONE);
    }

    @Test
    void overlappingNamesTheLowestId() {
        MatchRegionIndex index = new MatchRegionIndex();
        index.add(TWO, box(20, 0, 8, 8));
        index.add(ONE, box(0, 0, 8, 8));

        assertThat(index.overlapping(box(0, 0, 32, 8), MatchRegistry.MARGIN)).isEqualTo(ONE);
    }

    @Test
    void removeFreesTheRegion() {
        MatchRegionIndex index = new MatchRegionIndex();
        index.add(ONE, box(0, 0, 40, 40));
        index.add(TWO, box(42, 0, 8, 8));

        index.remove(ONE);

        assertThat(index.at(new BlockPos(5, 5, 5))).isNull();
        assertThat(index.overlapping(box(0, 0, 40, 40), MatchRegistry.MARGIN)).isNull();
        assertThat(index.at(new BlockPos(43, 5, 3))).as("the other match stays").isEqualTo(TWO);
        index.remove(ONE); // twice is harmless
        assertThatThrownBy(() -> index.add(TWO, box(100, 100, 8, 8))).isInstanceOf(IllegalArgumentException.class);
    }
}
