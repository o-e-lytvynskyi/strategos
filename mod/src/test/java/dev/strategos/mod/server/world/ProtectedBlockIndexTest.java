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
        Probe small = probe(4);
        Probe large = probe(64);
        long[] smallRuns = new long[7];
        long[] largeRuns = new long[7];
        for (int r = 0; r < smallRuns.length + 3; r++) { // three to warm up; the sizes alternate, so load hits both
            long s = small.run();
            long l = large.run();
            if (r >= 3) {
                smallRuns[r - 3] = s;
                largeRuns[r - 3] = l;
            }
        }
        // the fastest run: load from other processes only ever adds time, so it is the closest to the lookup's own cost
        double smallNs = Arrays.stream(smallRuns).min().orElseThrow() / 1e6;
        double largeNs = Arrays.stream(largeRuns).min().orElseThrow() / 1e6;
        assertThat(largeNs / smallNs)
                .as("%.1f ns at 64 sections, %.1f at 4", largeNs, smallNs)
                .isLessThan(3);
    }

    /** An index of {@code sections} full sections and probes spread over every section. */
    private record Probe(ProtectedBlockIndex index, BlockPos[] probes) {
        /** The nanoseconds of a million lookups. */
        long run() {
            long sink = 0;
            long start = System.nanoTime();
            for (int k = 0; k < 1_000_000; k++) {
                sink += index.owner(probes[(k * 40503) & (probes.length - 1)]);
            }
            long took = System.nanoTime() - start;
            assertThat(sink).isPositive();
            return took;
        }
    }

    private static Probe probe(int sections) {
        ProtectedBlockIndex index = new ProtectedBlockIndex();
        BlockPos[] probes = new BlockPos[1 << 16];
        int stride = Math.max(1, sections * 4096 / probes.length);
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
        return new Probe(index, probes);
    }
}
