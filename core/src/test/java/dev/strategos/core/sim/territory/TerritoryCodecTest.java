package dev.strategos.core.sim.territory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.territory.ClientTerritory;
import dev.strategos.core.protocol.TerritoryUpdate;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.TerritoryGrid;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** TERR-01 acceptance 4 (L2): every territory payload stays under 8 KB; parts reassemble exactly (M9 spec §2.4). */
class TerritoryCodecTest {
    @Test
    void randomChurnOnBothMapSizesStaysUnderTheCapAndReassembles() {
        for (int blocks : new int[] {384, 1_024}) {
            int tiles = TerritoryGrid.tilesFor(blocks);
            SplittableRandom r = new SplittableRandom(blocks);
            TerritoryKernel kernel = new TerritoryKernel();
            ClientTerritory client = new ClientTerritory();
            TerritoryGrid previous = null;
            for (int version = 0; version < 40; version++) {
                TerritorySources s = new TerritorySources();
                for (int i = 0; i < 4 + r.nextInt(80); i++) {
                    boolean root = i < 4;
                    s.add(
                            i % 4,
                            r.nextInt(2 * blocks),
                            r.nextInt(2 * blocks),
                            root ? 65_535 : 400,
                            root ? 56 : 12 + r.nextInt(16),
                            root);
                }
                TerritoryGrid next = new TerritoryGrid(tiles, tiles);
                kernel.compute(s, 4, next);
                List<TerritoryUpdate> parts = TerritoryUpdates.encode(1, version, version, previous, next);
                deliver(parts, client);
                assertThat(client.grid()).as("%d², version %d", blocks, version).isEqualTo(next);
                assertThat(client.version()).isEqualTo(version);
                previous = next;
            }
        }
    }

    @Test
    void aFullCheckerboardOfTheLargestMapSplitsAndReassembles() {
        TerritoryGrid g = new TerritoryGrid(128, 128);
        for (int t = 0; t < g.size(); t++) {
            g.set(t, (t + t / 128) % 2 == 0 ? (byte) (TerritoryGrid.CONNECTED | 1) : TerritoryGrid.NEUTRAL);
        }
        List<TerritoryUpdate> parts = TerritoryUpdates.encode(7, 100, 3, null, g);
        assertThat(parts.size())
                .as("16 K runs of one tile cannot fit one payload")
                .isGreaterThan(1);
        ClientTerritory client = new ClientTerritory();
        deliver(parts, client);
        assertThat(client.grid()).isEqualTo(g);
        assertThat(client.changed().cardinality()).isEqualTo(g.size());
    }

    @Test
    void anUnchangedGridEncodesToNothingAndADeltaWithoutItsBaseWaits() {
        TerritoryGrid g = new TerritoryGrid(48, 48);
        assertThat(TerritoryUpdates.encode(1, 1, 1, g, g)).isEmpty();
        TerritoryGrid h = new TerritoryGrid(48, 48);
        h.set(5, (byte) 2);
        ClientTerritory fresh = new ClientTerritory();
        for (TerritoryUpdate u : TerritoryUpdates.encode(1, 2, 2, g, h)) {
            assertThat(fresh.accept(roundTrip(u))).isFalse();
        }
        assertThat(fresh.grid()).isNull();
    }

    private static void deliver(List<TerritoryUpdate> parts, ClientTerritory client) {
        for (int i = 0; i < parts.size(); i++) {
            TerritoryUpdate u = roundTrip(parts.get(i));
            assertThat(u.wireSize()).isLessThanOrEqualTo(TerritoryUpdate.CAP);
            assertThat(client.accept(u)).isEqualTo(i == parts.size() - 1);
        }
    }

    private static TerritoryUpdate roundTrip(TerritoryUpdate u) {
        WireWriter w = new WireWriter();
        TerritoryUpdate.WIRE.write(w, u);
        assertThat(w.size()).isLessThanOrEqualTo(TerritoryUpdate.CAP);
        TerritoryUpdate back = TerritoryUpdate.WIRE.read(new WireReader(w.toByteArray()));
        assertThat(back).isEqualTo(u);
        return back;
    }
}
