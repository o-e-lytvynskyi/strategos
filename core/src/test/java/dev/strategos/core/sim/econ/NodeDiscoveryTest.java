package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.VoxelSlice;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * ECO-02 in L1: discovery on synthetic slices equals a reference flood fill (ECO-02.1), the nearest node equals brute
 * force (ECO-02.2), slot claims never exceed a node's slots (ECO-02.3), and the store's ring, save and hash.
 */
class NodeDiscoveryTest {
    private static final Material LOG = new Material(MaterialKind.LOG, "wood", "tree", 20_000);
    private static final Material LEAF = new Material(MaterialKind.LEAVES, "", "", 0);
    private static final Material BUSH = new Material(MaterialKind.FORAGE, "food", "fruit", 60_000);
    private static final Material GOLD = new Material(MaterialKind.LOG, "gold", "tree", 5_000);
    private static final short LEAVES = BlockClass.of(16, BlockClass.LEAVES);

    /** A 64 x 64 floor with seeded trees (1 x 1 and 2 x 2, some diagonal neighbours touching) and berry bushes. */
    private static VoxelWorld forest(long seed, Set<Long> logs, int[] bushes) {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 24, 1);
        List<int[]> bushAt = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            int x = 2 + Rng.below(seed, 3, 0, k, 1, 58);
            int z = 2 + Rng.below(seed, 3, 0, k, 2, 58);
            int height = 3 + Rng.below(seed, 3, 0, k, 3, 5);
            int wide = Rng.below(seed, 3, 0, k, 4, 4) == 0 ? 2 : 1;
            if (Rng.below(seed, 3, 0, k, 5, 5) == 0) { // a bush, placed once the trees stand
                bushAt.add(new int[] {x, z});
                continue;
            }
            for (int dx = 0; dx < wide; dx++) {
                for (int dz = 0; dz < wide; dz++) {
                    for (int y = 1; y <= height; y++) {
                        w.set(x + dx, y, z + dz, BlockClass.SOLID);
                        w.material(x + dx, y, z + dz, LOG);
                        logs.add(key(x + dx, y, z + dz));
                    }
                    if (Rng.below(seed, 3, 0, k, 6, 3) == 0) { // a branch leaning out, diagonally
                        w.set(x + dx + 1, height + 1, z + dz + 1, BlockClass.SOLID);
                        w.material(x + dx + 1, height + 1, z + dz + 1, LOG);
                        logs.add(key(x + dx + 1, height + 1, z + dz + 1));
                    }
                }
            }
            for (int dx = -2; dx <= wide + 1; dx++) {
                for (int dz = -2; dz <= wide + 1; dz++) {
                    int lx = x + dx;
                    int lz = z + dz;
                    if (lx >= 0 && lz >= 0 && lx < 64 && lz < 64 && w.get(lx, height + 2, lz) == BlockClass.AIR) {
                        w.set(lx, height + 2, lz, LEAVES);
                        w.material(lx, height + 2, lz, LEAF);
                    }
                }
            }
        }
        int n = 0;
        for (int[] b : bushAt) {
            if (w.get(b[0], 1, b[1]) == BlockClass.AIR) {
                w.set(b[0], 1, b[1], BlockClass.of(0, BlockClass.PASSABLE | BlockClass.HAZARD));
                w.material(b[0], 1, b[1], BUSH);
                n++;
            }
        }
        bushes[0] = n;
        return w;
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 32) | ((long) z << 12) | y;
    }

    /** The reference: clusters of logs joined through any of the 26 neighbours, by plain search. */
    private static List<Set<Long>> clusters(Set<Long> logs) {
        List<Set<Long>> out = new ArrayList<>();
        Set<Long> left = new TreeSet<>(logs);
        while (!left.isEmpty()) {
            long first = left.iterator().next();
            Set<Long> cluster = new HashSet<>();
            List<Long> todo = new ArrayList<>(List.of(first));
            left.remove(first);
            while (!todo.isEmpty()) {
                long k = todo.remove(todo.size() - 1);
                cluster.add(k);
                int x = (int) (k >>> 32);
                int z = (int) ((k >>> 12) & 0xFFFFF);
                int y = (int) (k & 0xFFF);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            long n = key(x + dx, y + dy, z + dz);
                            if (left.remove(n)) {
                                todo.add(n);
                            }
                        }
                    }
                }
            }
            out.add(cluster);
        }
        return out;
    }

    private static NodeStore discover(VoxelWorld w) {
        NodeStore store = new NodeStore();
        NodeDiscovery.discover(store, EconomyRules.DEFAULTS, 4, 4, 0, p -> w.slice(new PageCoord(p % 4, p / 4)));
        return store;
    }

    @Test
    void discoveryEqualsAReferenceFloodFill() {
        for (long seed = 1; seed <= 20; seed++) {
            Set<Long> logs = new HashSet<>();
            int[] bushes = new int[1];
            VoxelWorld w = forest(seed, logs, bushes);
            NodeStore store = discover(w);
            List<Set<Long>> expected = clusters(logs);

            List<Set<Long>> trees = new ArrayList<>();
            int forage = 0;
            for (int id = 1; id <= store.count(); id++) {
                if (store.kind(id) == MaterialKind.FORAGE) {
                    forage++;
                    assertThat(store.amount(id)).isEqualTo(60_000);
                    continue;
                }
                Set<Long> blocks = new HashSet<>();
                int lastY = Integer.MAX_VALUE;
                for (long b : store.blocks(id)) {
                    blocks.add(key(NodeStore.x(b), NodeStore.y(b), NodeStore.z(b)));
                    assertThat(NodeStore.y(b)).as("top-down").isLessThanOrEqualTo(lastY);
                    lastY = NodeStore.y(b);
                }
                assertThat(store.amount(id)).isEqualTo(20_000L * blocks.size());
                trees.add(blocks);
            }
            assertThat(new HashSet<>(trees)).as("seed " + seed).isEqualTo(new HashSet<>(expected));
            assertThat(forage).as("seed " + seed).isEqualTo(bushes[0]);
        }
    }

    @Test
    void aResourceTheMatchLacksIsNoNodeAndTheRingFollowsTheTrunk() {
        VoxelWorld w = VoxelFixtures.flat(64, 64, 24, 1);
        for (int y = 1; y <= 4; y++) {
            w.material(10, y, 10, LOG).material(30, y, 30, LOG).material(31, y, 30, LOG);
            w.material(30, y, 31, LOG).material(31, y, 31, LOG).material(50, y, 50, GOLD);
        }
        NodeStore store = discover(w);

        assertThat(store.count()).as("the gold tree is none").isEqualTo(2);
        assertThat(store.slotCount(1)).as("round a 1 x 1 trunk").isEqualTo(8);
        assertThat(store.slotCount(2)).as("round a 2 x 2 trunk").isEqualTo(12);
        assertThat(store.anchorX(2)).isEqualTo(31.0);
        assertThat(store.anchorZ(2)).isEqualTo(31.0);
        assertThat(store.baseY(1)).isEqualTo(1);
    }

    @Test
    void theNearestNodeEqualsBruteForce() {
        Set<Long> logs = new HashSet<>();
        NodeStore store = discover(forest(7, logs, new int[1]));
        NodeIndex index = new NodeIndex(store, EconomyRules.DEFAULTS, 4, 4);
        for (int q = 0; q < 2_000; q++) {
            double x = Rng.unit(11, 4, 0, q, 1) * 64;
            double z = Rng.unit(11, 4, 0, q, 2) * 64;
            double reach = 4 + Rng.unit(11, 4, 0, q, 3) * 40;
            int parity = Rng.below(11, 4, 0, q, 4, 3); // 0: any, 1: odd ids only, 2: even ids only
            java.util.function.IntPredicate ok = id -> parity == 0 || id % 2 == parity % 2;
            int resource = q % 2 == 0 ? 1 : 0; // wood or food
            int subtype = 0;
            int expected = 0;
            double best = Double.POSITIVE_INFINITY;
            for (int id = 1; id <= store.count(); id++) {
                if (store.resource(id) != resource || store.subtype(id) != subtype || !ok.test(id)) {
                    continue;
                }
                double d = dev.strategos.core.shared.DetMath.length(store.anchorX(id) - x, store.anchorZ(id) - z);
                if (d <= reach && d < best) {
                    best = d;
                    expected = id;
                }
            }
            assertThat(index.nearest(store, resource, subtype, x, z, reach, ok))
                    .as("query " + q)
                    .isEqualTo(expected);
        }
    }

    @Test
    void slotClaimsNeverExceedANodesSlots() {
        Set<Long> logs = new HashSet<>();
        NodeStore store = discover(forest(3, logs, new int[1]));
        Map<Integer, Integer> held = new HashMap<>(); // handle -> node
        for (int step = 0; step < 20_000; step++) {
            int handle = 1 + Rng.below(5, 5, 0, step, 1, 30);
            int node = 1 + Rng.below(5, 5, 0, step, 2, Math.min(3, store.count()));
            Integer had = held.get(handle);
            if (had != null && Rng.below(5, 5, 0, step, 3, 2) == 0) {
                store.release(had, handle);
                held.remove(handle);
            } else if (had == null) {
                int slot = store.claimNearest(node, handle, store.anchorX(node), store.anchorZ(node));
                if (slot >= 0) {
                    held.put(handle, node);
                    assertThat(store.claim(node, slot)).isEqualTo(handle);
                } else {
                    assertThat(store.freeSlots(node)).isZero();
                }
            }
            for (int id = 1; id <= Math.min(3, store.count()); id++) {
                int claimed = store.slotCount(id) - store.freeSlots(id);
                int node2 = id;
                int holders =
                        (int) held.values().stream().filter(v -> v == node2).count();
                assertThat(claimed).isEqualTo(holders).isLessThanOrEqualTo(store.slotCount(id));
            }
        }
    }

    @Test
    void aMatchFindsItsNodesOnceItsPagesAreInAndKeepsThemThroughASnapshot() {
        Set<Long> logs = new HashSet<>();
        VoxelWorld w = forest(5, logs, new int[1]);
        SimHarness h = SimHarness.builder().size(64, 64).yBand(0, 23, 1).build();
        h.publishAll(w);
        h.step();
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.nodes().discovered()).isTrue();
        int found = s.nodes().count();
        assertThat(found).isEqualTo(clusters(logs).size() + countForage(s));

        MatchSnapshot snapshot = h.core().snapshot();
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
        assertThat(decoded).isEqualTo(snapshot);
        Map<Long, byte[]> slices = new HashMap<>();
        for (PageRef ref : snapshot.pages()) {
            VoxelSlice slice = w.slice(new PageCoord(ref.x(), ref.z()));
            slices.put(slice.hash(), slice.encode());
        }
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L), slices::get);
        assertThat(KernelAccess.state(restored).nodes().count()).isEqualTo(found);
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());

        SimHarness bare = SimHarness.builder().build();
        bare.publishAll(VoxelFixtures.flat(32, 32, 16, 1));
        bare.step();
        assertThat(KernelAccess.state(bare.core()).nodes().count()).isZero();
        assertThat(MatchSnapshotCodec.encode(JsonOps.INSTANCE, bare.core().snapshot())
                        .getAsJsonObject()
                        .has("nodes"))
                .isFalse();
    }

    private static int countForage(MatchState s) {
        int n = 0;
        for (int id = 1; id <= s.nodes().count(); id++) {
            if (s.nodes().kind(id) == MaterialKind.FORAGE) {
                n++;
            }
        }
        return n;
    }
}
