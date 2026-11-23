package dev.strategos.core.client.territory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.TerritoryGrid;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

/**
 * TERR-03.1, .2: the borders' geometry of a fixed layout equals its stored hash, with the hand-counted facts; a changed
 * tile dirties only the section columns its strips and its neighbours' strips live in.
 */
class BorderGeometryTest {
    /** The hash of the layout's three non-empty section columns, extracted for viewer 0. */
    static final long STORED = -4359533006412219566L;

    private static final int FLOOR = 64;

    /** 8 × 8 tiles: faction 0 on (0..1, 0..1) connected and on (0, 2) unconnected; faction 1 on (2, 0..1) connected. */
    private static TerritoryGrid layout() {
        TerritoryGrid g = new TerritoryGrid(8, 8);
        for (int tz = 0; tz < 2; tz++) {
            for (int tx = 0; tx < 2; tx++) {
                g.set(tz * 8 + tx, (byte) (TerritoryGrid.CONNECTED | 0));
            }
            g.set(tz * 8 + 2, (byte) (TerritoryGrid.CONNECTED | 1));
        }
        g.set(2 * 8, (byte) 0); // faction 0, not connected
        return g;
    }

    private static BorderGeometry.Buffer extract(TerritoryGrid g, int sx, int sz, int viewer, boolean ally1) {
        BorderGeometry.Buffer out = new BorderGeometry.Buffer();
        BorderGeometry.extract(g, sx, sz, viewer, f -> f == 1 && ally1, (x, z) -> FLOOR, out);
        return out;
    }

    @Test
    void layoutGeometryEqualsItsStoredHash() {
        TerritoryGrid g = layout();
        long h = Hasher.start(0);
        for (int sz = 0; sz < 4; sz++) {
            for (int sx = 0; sx < 4; sx++) {
                BorderGeometry.Buffer b = extract(g, sx, sz, 0, false);
                h = Hasher.mix(h, b.count());
                for (int q = 0; q < b.count(); q++) {
                    h = Hasher.mix(h, b.colour(q));
                    for (int c = 0; c < 4; c++) {
                        h = Hasher.mix(h, Float.floatToIntBits(b.x(q, c)));
                        h = Hasher.mix(h, Float.floatToIntBits(b.y(q, c)));
                        h = Hasher.mix(h, Float.floatToIntBits(b.z(q, c)));
                    }
                }
            }
        }
        assertThat(Hasher.finish(h)).isEqualTo(STORED);
    }

    @Test
    void edgesAreCountedByHandAndColouredByRelation() {
        TerritoryGrid g = layout();
        // section (0,0): faction 0's 2 × 2 tiles; open edges: west 2, north 2, south 1 (tile (0,1) meets its own tile
        // (0,2)), east 2 (faction 1); each edge is four 2-block pieces
        BorderGeometry.Buffer own = extract(g, 0, 0, 0, false);
        assertThat(own.count()).isEqualTo(7 * 4);
        for (int q = 0; q < own.count(); q++) {
            assertThat(own.colour(q)).isEqualTo(BorderGeometry.OWN);
            assertThat(own.y(q, 0)).isEqualTo(FLOOR + BorderGeometry.LIFT);
        }
        // section (1,0): faction 1's two tiles, an enemy of viewer 0 (6 edges); an ally to the same viewer when allied
        BorderGeometry.Buffer enemy = extract(g, 1, 0, 0, false);
        assertThat(enemy.count()).isEqualTo(6 * 4);
        for (int q = 0; q < enemy.count(); q++) {
            assertThat(enemy.colour(q)).isEqualTo(BorderGeometry.ENEMY);
        }
        assertThat(extract(g, 1, 0, 0, true).colour(0)).isEqualTo(BorderGeometry.ALLY);
        assertThat(extract(g, 1, 0, 1, false).colour(0)).isEqualTo(BorderGeometry.OWN);
    }

    @Test
    void anUnconnectedTileIsDashed() {
        // section (0,1): the unconnected tile (0,2) has three open edges (its north meets its own tile) of two kept
        // pieces each
        BorderGeometry.Buffer b = extract(layout(), 0, 1, 0, false);
        assertThat(b.count()).isEqualTo(3 * 2);
    }

    @Test
    void neutralLandDrawsNothing() {
        assertThat(extract(layout(), 3, 3, 0, false).count()).isZero();
        assertThat(extract(new TerritoryGrid(8, 8), 0, 0, 0, false).count()).isZero();
    }

    @Test
    void quadsAreCounterClockwiseFromAbove() {
        BorderGeometry.Buffer b = extract(layout(), 0, 0, 0, false);
        for (int q = 0; q < b.count(); q++) {
            float e1x = b.x(q, 1) - b.x(q, 0);
            float e1z = b.z(q, 1) - b.z(q, 0);
            float e2x = b.x(q, 2) - b.x(q, 1);
            float e2z = b.z(q, 2) - b.z(q, 1);
            assertThat(e1z * e2x - e1x * e2z).as("the normal's Y of quad %d", q).isPositive();
        }
    }

    private static BitSet sections(int tx, int tz) {
        BitSet changed = new BitSet();
        changed.set(tz * 8 + tx);
        return BorderGeometry.sectionsTouched(changed, 8, 8);
    }

    @Test
    void aChangedTileDirtiesOnlyTheSectionsItsEdgesTouch() {
        // 2 × 2 tiles to a section: a tile has a neighbour in one other column per side that crosses a section edge
        assertThat(sections(0, 0).cardinality()).isEqualTo(1); // the map's corner: both neighbours inside
        assertThat(sections(0, 0).get(0)).isTrue();
        assertThat(sections(1, 0).cardinality()).isEqualTo(2); // its east neighbour is in column 1
        assertThat(sections(1, 0).get(1)).isTrue();
        assertThat(sections(0, 2).cardinality()).isEqualTo(2); // its north neighbour is in row 0
        assertThat(sections(1, 1).cardinality()).isEqualTo(3); // the section's far corner: itself, east and south
        assertThat(sections(2, 2).cardinality()).isEqualTo(3);
        BitSet two = new BitSet();
        two.set(0);
        two.set(63);
        assertThat(BorderGeometry.sectionsTouched(two, 8, 8).cardinality()).isEqualTo(1 + 1);
    }
}
