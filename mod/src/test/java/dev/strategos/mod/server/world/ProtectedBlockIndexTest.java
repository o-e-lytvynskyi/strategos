package dev.strategos.mod.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The protected-block index (BLD-07.4): a position maps to its owner and match and back to nothing; and a lookup
 * consults one section map of at most 4,096 positions whether the index holds four sections or sixty-four. Its time
 * is measured in the perf batch (`PerfTests.protectedLookupCost`), under the foreign-load guard: a wall-clock ratio
 * in every verify failed under parallel Gradle workers (#22).
 */
class ProtectedBlockIndexTest {
    @Test
    void aPositionMapsToItsOwnerAndBack() {
        ProtectedBlockIndex index = new ProtectedBlockIndex();
        BlockPos pos = new BlockPos(-17, 70, 33);
        assertThat(index.owner(pos)).isZero();
        index.protect(pos, 3, 0x1234);
        assertThat(index.owner(pos)).isEqualTo(0x1234);
        assertThat(index.matchOf(pos)).isEqualTo(3);
        assertThat(index.owner(pos.above())).as("its neighbour").isZero();
        index.protect(new BlockPos(500, 70, 500), 4, 7);
        index.releaseMatch(3);
        assertThat(index.owner(pos)).isZero();
        assertThat(index.size()).isOne();
        index.release(new BlockPos(500, 70, 500));
        assertThat(index.size()).isZero();
    }

    @Test
    void aLookupConsultsOneBoundedSectionAtAnySize() {
        for (int sections : new int[] {4, 64}) {
            ProtectedBlockIndex index = new ProtectedBlockIndex();
            for (int s = 0; s < sections; s++) {
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            index.protect(new BlockPos(s * 16 + x, 64 + y, z), 1, 1 + x);
                        }
                    }
                }
            }
            assertThat(index.size()).isEqualTo(sections * 4096);
            for (int s = 0; s < sections; s++) {
                BlockPos pos = new BlockPos(s * 16 + 5, 73, 3);
                assertThat(index.sectionSize(pos))
                        .as("section %d of %d", s, sections)
                        .isEqualTo(4096);
                assertThat(index.owner(pos)).isEqualTo(6);
            }
            assertThat(index.sectionSize(new BlockPos(sections * 16 + 1, 64, 0)))
                    .isZero();
        }
    }
}
