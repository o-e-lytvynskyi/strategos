package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.build.ClientPlacement;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * BLD-02.2 in L1: the client's placement, read from its {@code building_state}, its faction's stock and its blocks,
 * gives the server's verdict for every footprint of a house, a barracks and a civic centre, every rotation, every
 * third cell of a 64² match with a civic centre, a house's foundation, trees, a bush, a pond and a raised step.
 */
class PlacementParityTest {
    @Test
    void theClientGivesTheServersVerdict() {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 32, 1);
        TreeFixtures.draw(w, TreeFixtures.Species.OAK, 40, 1, 12);
        TreeFixtures.draw(w, TreeFixtures.Species.BIRCH, 8, 1, 44);
        w.set(30, 1, 50, VoxelFixtures.BUSH)
                .material(30, 1, 50, new Material(MaterialKind.FORAGE, "food", "fruit", 60_000));
        for (int x = 50; x < 56; x++) {
            for (int z = 40; z < 46; z++) {
                w.set(x, 0, z, VoxelFixtures.WATER); // a pond, its surface the floor's height
            }
        }
        for (int x = 20; x < 26; x++) {
            for (int z = 40; z < 64; z++) {
                w.set(x, 1, z, VoxelFixtures.SOLID); // a step up
                if (x == 23) {
                    w.set(x, 2, z, VoxelFixtures.SOLID); // and a second, three high in all
                    w.set(x, 3, z, VoxelFixtures.SOLID);
                }
            }
        }
        SimHarness h = ConstructionTest.match(w);
        h.submit(
                ConstructionTest.P0,
                new PlaceBuilding(ConstructionTest.HOUSE, 30, 20, 1, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        h.runTicks(4); // the pages rebuilt round the centre
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.buildings().handles())
                .as("the centre and the house's foundation")
                .hasSize(2);

        TemplateSet templates = s.templates();
        BuildingState buildings = ReplicationService.buildingState(h.queries(), 1, new FactionSlot(0));
        FactionState faction = ReplicationService.factionState(h.queries(), 1, new FactionSlot(0));
        List<String> resources = s.config().rules().economy().resources().stream()
                .map(r -> r.id())
                .toList();
        PlacementRules.World client =
                ClientPlacement.world(templates, buildings.buildings(), 64, 64, new ClientPlacement.Columns() {
                    @Override
                    public double floor(int x, int z) {
                        return Placing.floor(
                                s.surfaces(), s.config().bounds(), x, z); // what its own blocks say, equally
                    }

                    @Override
                    public boolean node(int x, int z) {
                        for (int y = 0; y < 32; y++) {
                            Material m = w.materialAt(x, y, z);
                            if (m != null && (m.kind() == MaterialKind.LOG || m.kind() == MaterialKind.FORAGE)) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
        PlacementRules.World server = Placing.world(s);
        int compared = 0;
        int refused = 0;
        for (String id :
                List.of("strategos:building/house", "strategos:building/barracks", "strategos:building/civic_centre")) {
            UnitTemplate t = Objects.requireNonNull(templates.byId(new dev.strategos.core.shared.TemplateId(id)));
            Blueprint b = Objects.requireNonNull(templates.blueprint(t.netIndex()));
            PlacementRules.Params serverParams = Placing.params(s, t.netIndex(), 0);
            PlacementRules.Params clientParams =
                    ClientPlacement.params(templates, t.netIndex(), resources, faction.stock());
            assertThat(clientParams).as(id).isEqualTo(serverParams);
            for (int rotation = 0; rotation < 4; rotation++) {
                for (int x = -3; x < 64; x += 3) {
                    for (int z = -3; z < 64; z += 3) {
                        Footprint at = Footprint.of(b, x, z, rotation);
                        PlacementRules.Verdict expected = PlacementRules.check(server, b, at, 0, serverParams);
                        PlacementRules.Verdict got = PlacementRules.check(client, b, at, 0, clientParams);
                        assertThat(got)
                                .as("%s at %d, %d turned %d", id, x, z, rotation)
                                .isEqualTo(expected);
                        compared++;
                        refused += expected.ok() ? 0 : 1;
                    }
                }
            }
        }
        assertThat(compared).isGreaterThan(5_000);
        assertThat(refused).as("refusals of every kind among them").isBetween(compared / 4, compared - 100);
    }
}
