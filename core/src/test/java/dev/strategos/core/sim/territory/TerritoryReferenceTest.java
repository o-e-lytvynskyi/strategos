package dev.strategos.core.sim.territory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** TERR-01 acceptance 1: ownership and connectivity equal the reference on random layouts (M9 spec §2.2). */
class TerritoryReferenceTest {
    @Test
    void theKernelEqualsTheReferenceOnFiveHundredRandomLayouts() {
        TerritoryKernel kernel = new TerritoryKernel(); // reused across layouts, as on a worker thread
        for (int seed = 1; seed <= 500; seed++) {
            SplittableRandom r = new SplittableRandom(seed);
            int blocks = 64 + r.nextInt(961); // 64² to 1,024²
            int tiles = TerritoryGrid.tilesFor(blocks);
            int factions = 1 + r.nextInt(4);
            TerritorySources s = new TerritorySources();
            int n = 1 + r.nextInt(60);
            for (int i = 0; i < n; i++) {
                boolean root = r.nextInt(5) == 0;
                s.add(
                        r.nextInt(factions),
                        r.nextInt(2 * blocks),
                        r.nextInt(2 * blocks),
                        root ? 65_535 : 1 + r.nextInt(400),
                        root ? 56 : 8 + r.nextInt(40),
                        root);
            }
            TerritoryGrid out = new TerritoryGrid(tiles, tiles);
            kernel.compute(s, factions, out);
            assertThat(out).as("seed %d", seed).isEqualTo(TerritoryReference.compute(s, factions, tiles, tiles));
        }
    }

    @Test
    void aCivicCentreClaimsItsRadiusAndIsConnected() {
        TerritorySources s = new TerritorySources();
        s.add(0, 2 * 100, 2 * 100, 65_535, 56, true); // centre at block 100: tile 12
        TerritoryGrid g = new TerritoryGrid(48, 48);
        new TerritoryKernel().compute(s, 2, g);
        assertThat(g.ownerAt(100, 100)).isZero();
        assertThat(g.connectedAt(100, 100)).isTrue();
        assertThat(g.ownerAt(100 + 50, 100)).isZero(); // tile 18, centre 148: 48 blocks, inside 56
        assertThat(g.ownerAt(100 + 64, 100)).isEqualTo(-1); // tile 20, centre 164: 64 blocks, outside
    }

    @Test
    void aTieAtTheTopIsNeutralAndAnIslandIsNotConnected() {
        TerritorySources s = new TerritorySources();
        s.add(0, 2 * 100, 2 * 100, 1_000, 40, true);
        s.add(1, 2 * 164, 2 * 100, 1_000, 40, true); // mirror image: the tile column at block 132 ties
        s.add(0, 2 * 300, 2 * 300, 500, 20, false); // a house far from any root
        TerritoryGrid g = new TerritoryGrid(48, 48);
        new TerritoryKernel().compute(s, 2, g);
        assertThat(g.ownerAt(132, 100)).isEqualTo(-1);
        assertThat(g.ownerAt(300, 300)).isZero();
        assertThat(g.connectedAt(300, 300)).isFalse();
        assertThat(g.connectedAt(100, 100)).isTrue();
        assertThat(g.connectedAt(164, 100)).isTrue();
    }

    @Test
    void theIntegerSquareRootIsExact() {
        SplittableRandom r = new SplittableRandom(7);
        for (int i = 0; i < 10_000; i++) {
            long v = r.nextLong(Long.MAX_VALUE / 4);
            long q = TerritorySources.isqrt(v);
            assertThat(q * q).isLessThanOrEqualTo(v);
            assertThat((q + 1) * (q + 1)).isGreaterThan(v);
        }
    }
}
