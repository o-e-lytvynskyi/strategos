package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures;
import dev.strategos.core.testkit.TreeFixtures.Species;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-03 in L1: on every species' shape, 2 × 2 trunks included, a log's worth gathered takes the highest log, until
 * the tree is gone; at every stage no leaf vanilla would decay is left (a reference search over what stands); leaves a
 * neighbouring tree holds stay; a 2 × 2 tree takes its twelve workers.
 */
class TreeFellingTest {
    private static MatchState match(VoxelWorld w) {
        SimHarness h = SimHarness.builder()
                .size(w.sizeX(), w.sizeZ())
                .yBand(0, w.height() - 1, 1)
                .build();
        h.publishAll(w);
        h.step();
        return KernelAccess.state(h.core());
    }

    private static long[] standing(MatchState s, MaterialKind kind) {
        List<long[]> found = s.materialsIn(0, 0, 0, 63, 63, 63, kind);
        long[] out = new long[found.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = found.get(i)[0];
        }
        Arrays.sort(out);
        return out;
    }

    /** The reference: vanilla's distances by plain relaxation until nothing changes; the leaves at 7. */
    private static List<Long> decayable(long[] logs, long[] leaves) {
        int[] d = new int[leaves.length];
        Arrays.fill(d, 7);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < leaves.length; i++) {
                int x = NodeStore.x(leaves[i]);
                int y = NodeStore.y(leaves[i]);
                int z = NodeStore.z(leaves[i]);
                int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] f : faces) {
                    long n = NodeStore.pack(x + f[0], y + f[1], z + f[2]);
                    int nd = Arrays.binarySearch(logs, n) >= 0 ? 0 : 7;
                    int j = Arrays.binarySearch(leaves, n);
                    if (j >= 0) {
                        nd = d[j];
                    }
                    if (nd + 1 < d[i]) {
                        d[i] = nd + 1;
                        changed = true;
                    }
                }
            }
        }
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < leaves.length; i++) {
            if (d[i] >= 7) {
                out.add(leaves[i]);
            }
        }
        return out;
    }

    @Test
    void everySpeciesFallsTopDownLogByLogAndNoDecayableLeafIsLeft() {
        for (Species species : Species.values()) {
            MatchState s = match(TreeFixtures.one(species));
            assertThat(s.nodes().count()).as(species + " is one tree").isEqualTo(1);
            assertThat(decayable(standing(s, MaterialKind.LOG), standing(s, MaterialKind.LEAVES)))
                    .as(species + "'s canopy is whole to start with")
                    .isEmpty();
            int logs = s.nodes().blocks(1).length;
            assertThat(s.nodes().amount(1)).isEqualTo(logs * TreeFixtures.PER_LOG);
            for (int k = 0; k < logs; k++) {
                long[] before = standing(s, MaterialKind.LOG);
                int top = Arrays.stream(before).mapToInt(NodeStore::y).max().orElseThrow();
                int out = s.outputs().size();
                assertThat(Felling.take(s, 1, TreeFixtures.PER_LOG)).isEqualTo(TreeFixtures.PER_LOG);
                List<WorldEffect> writes = new ArrayList<>();
                for (OutputEvent e : s.outputs().subList(out, s.outputs().size())) {
                    if (e instanceof WorldEffect w) {
                        writes.add(w);
                    }
                }
                List<WorldEffect> felled = writes.stream()
                        .filter(w -> w.priority() == WritePriority.NODE_REMOVAL)
                        .toList();
                assertThat(felled).as(species + " log " + k).singleElement().satisfies(w -> {
                    assertThat(w.removes()).isTrue();
                    assertThat(w.y()).as("the highest log first").isEqualTo(top);
                });
                long[] logsLeft = standing(s, MaterialKind.LOG);
                assertThat(logsLeft).hasSize(before.length - 1);
                assertThat(decayable(logsLeft, standing(s, MaterialKind.LEAVES)))
                        .as(species + " after log " + k)
                        .isEmpty();
            }
            assertThat(s.nodes().amount(1)).isZero();
            assertThat(standing(s, MaterialKind.LEAVES))
                    .as(species + ": no log holds a leaf")
                    .isEmpty();
        }
    }

    @Test
    void leavesANeighbouringTreeHoldsStay() {
        VoxelWorld w = dev.strategos.core.testkit.VoxelFixtures.flat(32, 32, 32, 1);
        TreeFixtures.draw(w, Species.OAK, 10, 1, 12);
        TreeFixtures.draw(w, Species.OAK, 14, 1, 12); // the canopies touch between them
        MatchState s = match(w);
        assertThat(s.nodes().count()).isEqualTo(2);
        int felled = s.nodes().anchorX(1) < s.nodes().anchorX(2) ? 1 : 2;
        int kept = 3 - felled;
        long[] keptLogs = s.nodes().blocks(kept);
        Arrays.sort(keptLogs);
        long[] leavesBefore = standing(s, MaterialKind.LEAVES);
        List<Long> heldByKept = new ArrayList<>();
        List<Long> notHeld = decayable(keptLogs, leavesBefore);
        for (long leaf : leavesBefore) {
            if (!notHeld.contains(leaf)) {
                heldByKept.add(leaf);
            }
        }

        while (s.nodes().amount(felled) > 0) {
            Felling.take(s, felled, TreeFixtures.PER_LOG);
        }

        long[] leavesAfter = standing(s, MaterialKind.LEAVES);
        assertThat(Arrays.stream(leavesAfter).boxed().toList()).containsExactlyElementsOf(heldByKept);
        assertThat(heldByKept)
                .as("some of the felled tree's leaves were within the other's reach")
                .isNotEmpty();
    }

    @Test
    void aLogSomeoneElseBreaksLeavesTheTreeAndCreditsNobody() {
        VoxelWorld w = TreeFixtures.one(Species.OAK);
        SimHarness h = SimHarness.builder().size(32, 32).yBand(0, 31, 1).build();
        h.publishAll(w);
        h.step();
        MatchState s = KernelAccess.state(h.core());
        long before = s.nodes().amount(1);
        int logs = s.nodes().blocks(1).length;
        w.set(12, 1, 12, dev.strategos.core.sim.nav.BlockClass.AIR).material(12, 1, 12, null); // the trunk's foot
        dev.strategos.core.sim.nav.PageCoord page = new dev.strategos.core.sim.nav.PageCoord(0, 0);
        h.publish(dev.strategos.core.sim.TerrainPage.build(
                page,
                w.slice(page),
                new dev.strategos.core.sim.nav.PageBuilder(dev.strategos.core.sim.nav.MobilityClass.INFANTRY),
                h.tick()));
        h.step();

        assertThat(s.nodes().amount(1)).isEqualTo(before - TreeFixtures.PER_LOG);
        assertThat(s.nodes().blocks(1)).hasSize(logs - 1);
        assertThat(s.treasury().view(0).gathered()).as("nobody is credited").containsOnly(0L);
    }

    @Test
    void aTwoByTwoTreeTakesItsTwelveWorkers() {
        MatchState s = match(TreeFixtures.one(Species.DARK_OAK));
        assertThat(s.nodes().slotCount(1)).isEqualTo(12);
        for (int worker = 1; worker <= 12; worker++) {
            assertThat(s.nodes().claimNearest(1, worker, 0, 0))
                    .as("worker " + worker)
                    .isNotNegative();
        }
        assertThat(s.nodes().claimNearest(1, 13, 0, 0)).as("a thirteenth").isEqualTo(-1);
    }
}
