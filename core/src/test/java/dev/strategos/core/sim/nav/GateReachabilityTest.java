package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.Allies;
import dev.strategos.core.testkit.FakeAllies;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.BitSet;
import org.junit.jupiter.api.Test;

/**
 * PATH-12 acceptance 1 (M9 spec §2.9): a gate splits the components in the base grid, its owner and the owner's allies
 * reach across it and an enemy does not, a path crosses it at the door's cost, and without gates nothing changes.
 */
class GateReachabilityTest {
    private static final int SIZE = 48;
    private static final int[] GATE = {24 << 16 | 20, 24 << 16 | 21};
    private static final Allies ALLIES = FakeAllies.twoAgainstRest(3); // 0 and 1 allied, 2 against them
    private final GridAStar search = new GridAStar();

    /** A wall along x = 24 from z 0 to {@code last}, with the gate's two columns left open in the voxels. */
    private static VoxelWorld wall(int last) {
        VoxelWorld w = VoxelFixtures.flat(SIZE, SIZE, 16, 1);
        w.fill(24, 1, 0, 24, 5, last, VoxelFixtures.SOLID);
        w.fill(24, 1, 20, 24, 5, 21, VoxelFixtures.AIR);
        return w;
    }

    private static SurfaceGrid grid(VoxelWorld w, boolean gateStands) {
        NavOverlay.Builder b = NavOverlay.builder();
        if (gateStands) {
            for (int c : GATE) {
                b.block(c >>> 16, c & 0xFFFF);
            }
        }
        return w.grid(MobilityClass.INFANTRY, b.build());
    }

    private static GateOverlay gate(int owner) {
        GateOverlay gates = new GateOverlay();
        gates.add(owner, GATE);
        return gates;
    }

    @Test
    void theGateSplitsTheComponentsAndItsOwnerAndAlliesCrossIt() {
        SurfaceGrid g = grid(wall(SIZE - 1), true);
        int west = g.component(g.node(10, 20, 0));
        int east = g.component(g.node(40, 20, 0));
        GateOverlay gates = gate(0);

        assertThat(west).as("split in the base grid").isNotEqualTo(east);
        assertThat(Reach.reachable(g, gates, ALLIES, 0, west, east)).as("owner").isTrue();
        assertThat(Reach.reachable(g, gates, ALLIES, 0, east, west))
                .as("owner, back")
                .isTrue();
        assertThat(Reach.reachable(g, gates, ALLIES, 1, west, east)).as("ally").isTrue();
        assertThat(Reach.reachable(g, gates, ALLIES, 2, west, east)).as("enemy").isFalse();
        assertThat(Reach.reachable(g, gates, ALLIES, 2, east, west))
                .as("enemy, back")
                .isFalse();
        assertThat(Reach.reachable(g, gates, ALLIES, 2, west, west))
                .as("its own side")
                .isTrue();
        assertThat(Reach.reachable(g, new GateOverlay(), ALLIES, 0, west, east))
                .as("no gates: the grid's own components")
                .isFalse();
    }

    @Test
    void takingTheGateAwayMergesTheComponents() {
        SurfaceGrid split = grid(wall(SIZE - 1), true);
        GateOverlay gates = gate(0);
        int version = gates.version();
        assertThat(split.component(split.node(10, 20, 0))).isNotEqualTo(split.component(split.node(40, 20, 0)));

        assertThat(gates.remove(0)).isTrue();
        SurfaceGrid open = grid(wall(SIZE - 1), false); // the footprint's release, the page rebuilt

        assertThat(gates.version()).isGreaterThan(version);
        assertThat(gates.empty()).isTrue();
        assertThat(open.component(open.node(10, 20, 0))).isEqualTo(open.component(open.node(40, 20, 0)));
        assertThat(Reach.reachable(
                        open,
                        gates,
                        ALLIES,
                        2,
                        open.component(open.node(10, 20, 0)),
                        open.component(open.node(40, 20, 0))))
                .as("an enemy walks through the opening")
                .isTrue();
    }

    @Test
    void theOwnersPathCrossesTheGateAtTheDoorCostAndTheEnemysDoesNot() {
        SurfaceGrid g = grid(wall(SIZE - 1), true);
        GateOverlay gates = gate(0);
        int from = g.node(10, 20, 0);

        GridAStar.Result owner = search.search(g, from, 40, 20, 20_000, gates.passFor(g, 0, ALLIES));
        GridAStar.Result ally = search.search(g, from, 40, 20, 20_000, gates.passFor(g, 1, ALLIES));
        GridAStar.Result enemy = search.search(g, from, 40, 20, 20_000, gates.passFor(g, 2, ALLIES));
        GridAStar.Result plain = search.search(g, from, 40, 20, 20_000);

        assertThat(owner.status()).isEqualTo(PathStatus.OK);
        assertThat(owner.nodes()).contains(g.node(23, 20, 0), g.node(25, 20, 0));
        assertThat(owner.cost()).as("30 blocks, the two across the gate at 1.5").isBetween(31.0, 31.9);
        assertThat(ally.nodes()).isEqualTo(owner.nodes());
        assertThat(enemy.status()).as("no way round").isNotEqualTo(PathStatus.OK);
        assertThat(plain.status()).isEqualTo(enemy.status());
        assertThat(plain.nodes()).isEqualTo(enemy.nodes());
        assertThat(gates.passFor(g, 2, ALLIES))
                .as("a pass without a gate of its own")
                .isNull();
    }

    @Test
    void anEnemyGoesRoundWhenThereIsAGapAndTheOwnerGoesThrough() {
        SurfaceGrid g = grid(wall(SIZE - 2), true); // open at z 47
        GateOverlay gates = gate(0);
        int from = g.node(10, 20, 0);

        GridAStar.Result owner = search.search(g, from, 40, 20, 20_000, gates.passFor(g, 0, ALLIES));
        GridAStar.Result enemy = search.search(g, from, 40, 20, 20_000, gates.passFor(g, 2, ALLIES));

        assertThat(enemy.status()).isEqualTo(PathStatus.OK);
        assertThat(enemy.nodes()).as("round the wall's end").contains(g.node(24, 47, 0));
        assertThat(enemy.nodes()).doesNotContain(g.node(23, 20, 0));
        assertThat(enemy.cost()).isGreaterThan(owner.cost() + 20);
        assertThat(owner.nodes()).contains(g.node(23, 20, 0), g.node(25, 20, 0));
    }

    @Test
    void theJobsGoalIsReachableAcrossAGateForItsOwnerOnly() {
        SurfaceGrid g = grid(wall(SIZE - 1), true);
        GateOverlay gates = gate(0);
        PathRequest r = new PathRequest(1, 1, 0, g.node(10, 20, 0), 40.5, 20.5, 20_000, 0, false);
        var scratch = new dev.strategos.core.port.JobScratch();

        PathResult owner = new PathJob(g, r, gates.passFor(g, 0, ALLIES)).run(scratch);
        PathResult enemy = new PathJob(g, r, gates.passFor(g, 2, ALLIES)).run(scratch);
        PathResult none = new PathJob(g, r).run(scratch);

        assertThat(owner.status()).isEqualTo(PathStatus.OK);
        PathStore.Waypoints w = owner.waypoints();
        assertThat(w.x(w.count() - 1)).as("the owner ends at its goal").isEqualTo(40.5);
        PathStore.Waypoints e = enemy.waypoints();
        assertThat(e.x(e.count() - 1))
                .as("the enemy's goal is the nearest it can reach")
                .isLessThan(24);
        assertThat(none.waypoints().x(none.waypoints().count() - 1)).isEqualTo(e.x(e.count() - 1));
    }

    @Test
    void withoutGatesTheSearchesAreTheSearchesOfBefore() {
        SurfaceGrid g = grid(wall(SIZE - 2), false);
        GateOverlay none = new GateOverlay();
        int from = g.node(3, 3, 0);

        GridAStar.Result before = search.search(g, from, 44, 40, 20_000);
        GridAStar.Result with = search.search(g, from, 44, 40, 20_000, none.passFor(g, 0, ALLIES));

        assertThat(none.passFor(g, 0, ALLIES)).isNull();
        assertThat(with.nodes()).isEqualTo(before.nodes());
        assertThat(with.cost()).isEqualTo(before.cost());
        assertThat(with.expansions()).isEqualTo(before.expansions());
        assertThat(none.mix(7)).as("not hashed while empty").isEqualTo(7);
    }

    @Test
    void sixtyFourGatesStayCheap() {
        int side = 192;
        VoxelWorld w = VoxelFixtures.flat(side, side, 16, 1);
        NavOverlay.Builder b = NavOverlay.builder();
        GateOverlay gates = new GateOverlay();
        for (int k = 1; k <= 7; k++) {
            int x = 24 * k;
            w.fill(x, 1, 0, x, 5, side - 1, VoxelFixtures.SOLID);
            for (int j = 0; j < 9 && gates.count() < 64; j++) {
                int z = 8 + 20 * j;
                w.fill(x, 1, z, x, 5, z + 1, VoxelFixtures.AIR);
                b.block(x, z).block(x, z + 1);
                gates.add(0, new int[] {x << 16 | z, x << 16 | z + 1});
            }
        }
        SurfaceGrid g = w.grid(MobilityClass.INFANTRY, b.build());
        int west = g.component(g.node(5, 5, 0));
        int east = g.component(g.node(side - 5, 5, 0));

        long t0 = System.nanoTime();
        GateLinks.Pass pass = gates.passFor(g, 0, ALLIES);
        long links = System.nanoTime() - t0;
        t0 = System.nanoTime();
        BitSet reach = Reach.reachableFrom(g, gates, ALLIES, 0, west);
        long reachNs = System.nanoTime() - t0;
        t0 = System.nanoTime();
        GridAStar.Result path = search.search(g, g.node(5, 18, 0), side - 5, 18, 100_000, pass);
        long pathNs = System.nanoTime() - t0;
        System.out.printf(
                "GateReachabilityTest: %d gates: links+pass %.2f ms, reach %.3f ms, path %.2f ms (%d expansions)%n",
                gates.count(), links / 1e6, reachNs / 1e6, pathNs / 1e6, path.expansions());

        assertThat(gates.count()).isEqualTo(63);
        assertThat(pass).isNotNull();
        assertThat(reach.get(east)).isTrue();
        assertThat(Reach.reachable(g, gates, ALLIES, 2, west, east)).isFalse();
        assertThat(path.status()).isEqualTo(PathStatus.OK);
    }
}
