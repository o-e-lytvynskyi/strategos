package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.SliceMaterials;
import dev.strategos.core.sim.nav.VoxelSlice;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;

/**
 * Finds a match's resource nodes in its slices' materials (ECO-02, 04 §11.3): every cluster of logs joined through
 * logs, diagonals included, is a tree (2 × 2 trunks, acacia and fancy oak too); every forage block is a node of its own.
 * Rock and ore wait for ECO-07 and ECO-08, leaves stay terrain (the canopy is the slice's, ECO-03), and a material whose
 * resource or subtype the match lacks is no node. Deterministic: pages in page order, voxels in index order, trees by
 * their lowest log, then forage by position.
 */
@CoreApi
public final class NodeDiscovery {
    private NodeDiscovery() {}

    /** One material block found: where, what it yields. */
    private record Found(long key, int x, int y, int z, int resource, int subtype, long amount, long regrow) {}

    /**
     * Discovers into the empty {@code store} from page {@code p}'s slice ({@code slices.apply(p)}, null for a page not
     * captured) of a grid {@code pagesX} wide, the band starting at {@code minY}.
     */
    public static void discover(
            NodeStore store,
            EconomyRules economy,
            int pagesX,
            int pagesZ,
            int minY,
            IntFunction<@Nullable VoxelSlice> slices) {
        List<Found> logs = new ArrayList<>();
        List<Found> forage = new ArrayList<>();
        for (int pz = 0; pz < pagesZ; pz++) {
            for (int px = 0; px < pagesX; px++) {
                VoxelSlice slice = slices.apply(pz * pagesX + px);
                if (slice == null || slice.materials().isEmpty()) {
                    continue;
                }
                SliceMaterials m = slice.materials();
                int height = slice.height();
                for (int k = 0; k < m.size(); k++) {
                    Material mat = m.material(k);
                    if (mat.kind() != MaterialKind.LOG && mat.kind() != MaterialKind.FORAGE) {
                        continue;
                    }
                    int resource = economy.indexOf(mat.resource());
                    int subtype = resource < 0
                            ? -1
                            : economy.resources().get(resource).subtypes().indexOf(mat.subtype());
                    if (subtype < 0) {
                        continue;
                    }
                    int column = m.voxel(k) / height;
                    int band = m.voxel(k) % height;
                    int x = px * VoxelSlice.SIDE + column / VoxelSlice.SIDE;
                    int z = pz * VoxelSlice.SIDE + column % VoxelSlice.SIDE;
                    Found f = new Found(
                            key(x, band, z), x, minY + band, z, resource, subtype, mat.amount(), mat.regrow());
                    (mat.kind() == MaterialKind.LOG ? logs : forage).add(f);
                }
            }
        }
        logs.sort((a, b) -> Long.compare(a.key(), b.key()));
        long[] keys = new long[logs.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = logs.get(i).key();
        }
        boolean[] seen = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            if (!seen[i]) {
                tree(store, logs, keys, seen, i, minY);
            }
        }
        forage.sort((a, b) -> Long.compare(a.key(), b.key()));
        for (Found f : forage) {
            int id = store.add(
                    MaterialKind.FORAGE,
                    f.resource(),
                    f.subtype(),
                    f.amount(),
                    f.amount(),
                    f.x() + 0.5,
                    f.z() + 0.5,
                    f.y(),
                    new long[] {NodeStore.pack(f.x(), f.y(), f.z())},
                    ring(new int[] {NodeStore.cell(f.x(), f.z())}));
            store.setRegrow(id, f.regrow());
        }
        store.markDiscovered();
    }

    /** The cluster of logs joined to log {@code first}, as one tree. */
    private static void tree(NodeStore store, List<Found> logs, long[] keys, boolean[] seen, int first, int minY) {
        List<Found> cluster = new ArrayList<>();
        int[] queue = new int[keys.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = first;
        seen[first] = true;
        while (head < tail) {
            Found f = logs.get(queue[head++]);
            cluster.add(f);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if ((dx | dy | dz) == 0 || f.x() + dx < 0 || f.z() + dz < 0) {
                            continue;
                        }
                        int j = Arrays.binarySearch(keys, key(f.x() + dx, f.y() - minY + dy, f.z() + dz));
                        if (j >= 0 && !seen[j]) {
                            seen[j] = true;
                            queue[tail++] = j;
                        }
                    }
                }
            }
        }
        cluster.sort((a, b) -> a.y() != b.y()
                ? Integer.compare(b.y(), a.y())
                : a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        int base = cluster.get(cluster.size() - 1).y();
        long amount = 0;
        long[] blocks = new long[cluster.size()];
        List<Integer> baseCells = new ArrayList<>();
        double sx = 0;
        double sz = 0;
        for (int i = 0; i < cluster.size(); i++) {
            Found f = cluster.get(i);
            amount += f.amount();
            blocks[i] = NodeStore.pack(f.x(), f.y(), f.z());
            if (f.y() == base) {
                baseCells.add(NodeStore.cell(f.x(), f.z()));
                sx += f.x() + 0.5;
                sz += f.z() + 0.5;
            }
        }
        Found lowest = logs.get(first);
        int[] cells = baseCells.stream().mapToInt(Integer::intValue).toArray();
        store.add(
                MaterialKind.LOG,
                lowest.resource(),
                lowest.subtype(),
                lowest.amount(),
                amount,
                sx / cells.length,
                sz / cells.length,
                base,
                blocks,
                ring(cells));
    }

    /**
     * The cells round {@code base} (8 for one column, 12 for a 2 × 2 trunk), sorted by x then z: where workers stand.
     * Whether a unit can stand there is the gathering's question (ECO-03, ECO-05).
     */
    static int[] ring(int[] base) {
        int[] sortedBase = base.clone();
        Arrays.sort(sortedBase);
        List<Integer> out = new ArrayList<>();
        for (int cell : sortedBase) {
            int x = cell >>> 16;
            int z = cell & 0xFFFF;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (x + dx < 0 || z + dz < 0) {
                        continue;
                    }
                    int c = NodeStore.cell(x + dx, z + dz);
                    if (Arrays.binarySearch(sortedBase, c) < 0 && !out.contains(c)) {
                        out.add(c);
                    }
                }
            }
        }
        int[] ring = out.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(ring);
        return ring;
    }

    private static long key(int x, int band, int z) {
        return ((long) x << 32) | ((long) z << 12) | (band & 0xFFF);
    }
}
