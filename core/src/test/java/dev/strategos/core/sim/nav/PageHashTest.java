package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/** PATH-02.3: equal slices give equal pages and hashes; slices round-trip through their run-length form. */
class PageHashTest {
    private static final PageCoord ORIGIN = new PageCoord(0, 0);

    @Test
    void equalSlicesGiveEqualPageHashes() {
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        SurfacePage a = builder.build(hills().slice(ORIGIN), NavOverlay.EMPTY, ORIGIN);
        SurfacePage b = new PageBuilder(MobilityClass.INFANTRY).build(hills().slice(ORIGIN), NavOverlay.EMPTY, ORIGIN);

        assertThat(b.hash()).isEqualTo(a.hash());
        assertThat(b).isEqualTo(a);
        SurfacePage elsewhere = builder.build(hills().slice(ORIGIN), NavOverlay.EMPTY, new PageCoord(3, 1));
        assertThat(elsewhere.hash()).as("the hash ignores the coordinate").isEqualTo(a.hash());
    }

    @Test
    void oneChangedBlockChangesTheHash() {
        VoxelWorld changed = hills();
        changed.set(7, 6, 7, VoxelFixtures.SOLID);
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);

        assertThat(builder.build(changed.slice(ORIGIN), NavOverlay.EMPTY, ORIGIN)
                        .hash())
                .isNotEqualTo(builder.build(hills().slice(ORIGIN), NavOverlay.EMPTY, ORIGIN)
                        .hash());
        assertThat(changed.slice(ORIGIN).hash())
                .isNotEqualTo(hills().slice(ORIGIN).hash());
    }

    @Test
    void slicesRoundTripThroughTheirRunLengthForm() {
        VoxelSlice slice = hills().slice(ORIGIN);

        byte[] encoded = slice.encode();

        assertThat(VoxelSlice.decode(encoded)).isEqualTo(slice);
        assertThat(encoded.length).as("a hilly page in a 32-high band").isLessThan(2_000);
    }

    @Test
    void refusesAMalformedSlice() {
        byte[] encoded = hills().slice(ORIGIN).encode();
        encoded[encoded.length - 1] = 9;
        assertThatThrownBy(() -> VoxelSlice.decode(encoded)).isInstanceOf(WireException.class);
        assertThatThrownBy(() -> new VoxelSlice(0, new short[0])).isInstanceOf(IllegalArgumentException.class);
    }

    private static VoxelWorld hills() {
        VoxelWorld w = new VoxelWorld(16, 32, 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int ground = 3 + (x * 7 + z * 3) % 5;
                w.fill(x, 0, z, x, ground, z, VoxelFixtures.SOLID);
            }
        }
        w.set(4, 5, 4, VoxelFixtures.LEAVES).set(9, 8, 2, VoxelFixtures.SLAB);
        return w;
    }
}
