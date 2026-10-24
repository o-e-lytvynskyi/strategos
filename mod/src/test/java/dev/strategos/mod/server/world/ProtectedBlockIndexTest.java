package dev.strategos.mod.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The protected-block index (BLD-07.4): a position maps to its owner and match and back to nothing; and a lookup costs
 * the same whether the index holds four sections of blocks or sixty-four, one section map and one entry.
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
    void aLookupCostsTheSameAtAnySize() {
        double small = nanosPerLookup(4);
        double large = nanosPerLookup(64);
        assertThat(large / small)
                .as("%.1f ns at 64 sections, %.1f at 4", large, small)
                .isLessThan(3);
    }

    /** The median time of a lookup in an index of {@code sections} full sections, over a million lookups, seven times. */
    private static double nanosPerLookup(int sections) {
        ProtectedBlockIndex index = new ProtectedBlockIndex();
        BlockPos[] probes = new BlockPos[1 << 16];
        int stride = Math.max(1, sections * 4096 / probes.length); // probes spread over every section
        int seen = 0;
        int n = 0;
        for (int s = 0; s < sections; s++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos pos = new BlockPos(s * 16 + x, 64 + y, z);
                        index.protect(pos, 1, 1 + x);
                        if (seen++ % stride == 0 && n < probes.length) {
                            probes[n++] = pos;
                        }
                    }
                }
            }
        }
        for (int k = n; k < probes.length; k++) {
            probes[k] = probes[(k * 7919) % n];
        }
        long[] runs = new long[7];
        long sink = 0;
        for (int r = 0; r < runs.length + 3; r++) { // three to warm up
            long start = System.nanoTime();
            for (int k = 0; k < 1_000_000; k++) {
                sink += index.owner(probes[(k * 40503) & (probes.length - 1)]);
            }
            if (r >= 3) {
                runs[r - 3] = System.nanoTime() - start;
            }
        }
        assertThat(sink).isPositive();
        Arrays.sort(runs);
        return runs[runs.length / 2] / 1e6;
    }
}
