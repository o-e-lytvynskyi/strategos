package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.nav.BlockClass;
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
 * logs, diagonals included, is a tree (2 × 2 trunks, acacia and fancy oak too); every forage block is a node of its own;
 * every rock outcrop above its surrounding ground is one (ECO-07). Ore waits for ECO-08's deposits, leaves stay terrain (the canopy is the slice's, ECO-03), and a material whose
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
        outcrops(store, economy, pagesX, pagesZ, minY, slices);
        store.markDiscovered();
    }

    /**
     * ECO-07, 04 §11.2: every group of columns, side by side, whose top block is rock is an outcrop, its floor the
     * lowest top of the columns round it; its node is the rock above that floor, ordered top-down and then outside-in
     * from the group's middle, so mining lowers a hill and never digs below the ground round it. A group with no rock
     * above its floor (flat rock ground) is no node. Deterministic: columns in x then z order.
     */
    private static void outcrops(
            NodeStore store,
            EconomyRules economy,
            int pagesX,
            int pagesZ,
            int minY,
            IntFunction<@Nullable VoxelSlice> slices) {
        int sizeX = pagesX * VoxelSlice.SIDE;
        int sizeZ = pagesZ * VoxelSlice.SIDE;
        int[] top = new int[sizeX * sizeZ]; // each column's top solid band, -1 for none
        boolean[] rock = new boolean[top.length];
        VoxelSlice[] bySide = new VoxelSlice[pagesX * pagesZ];
        Arrays.fill(top, -1);
        boolean any = false;
        for (int pz = 0; pz < pagesZ; pz++) {
            for (int px = 0; px < pagesX; px++) {
                VoxelSlice slice = slices.apply(pz * pagesX + px);
                bySide[pz * pagesX + px] = slice;
                if (slice == null) {
                    continue;
                }
                for (int cx = 0; cx < VoxelSlice.SIDE; cx++) {
                    for (int cz = 0; cz < VoxelSlice.SIDE; cz++) {
                        int band = slice.height() - 1;
                        while (band >= 0 && BlockClass.is(slice.get(cx, band, cz), BlockClass.PASSABLE)) {
                            band--;
                        }
                        int column = (px * VoxelSlice.SIDE + cx) * sizeZ + pz * VoxelSlice.SIDE + cz;
                        top[column] = band;
                        if (band >= 0) {
                            Material m = slice.materials().at(VoxelSlice.index(cx, band, cz, slice.height()));
                            rock[column] = m != null && m.kind() == MaterialKind.STONE;
                            any |= rock[column];
                        }
                    }
                }
            }
        }
        if (!any) {
            return;
        }
        boolean[] seen = new boolean[top.length];
        int[] queue = new int[top.length];
        for (int start = 0; start < top.length; start++) {
            if (!rock[start] || seen[start]) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            int floor = Integer.MAX_VALUE;
            while (head < tail) {
                int c = queue[head++];
                int x = c / sizeZ;
                int z = c % sizeZ;
                int[][] sides = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
                for (int[] n : sides) {
                    if (n[0] < 0 || n[1] < 0 || n[0] >= sizeX || n[1] >= sizeZ) {
                        continue;
                    }
                    int nc = n[0] * sizeZ + n[1];
                    if (rock[nc]) {
                        if (!seen[nc]) {
                            seen[nc] = true;
                            queue[tail++] = nc;
                        }
                    } else if (top[nc] >= 0) {
                        floor = Math.min(floor, top[nc]);
                    }
                }
            }
            if (floor != Integer.MAX_VALUE) {
                outcrop(store, economy, Arrays.copyOf(queue, tail), floor, sizeZ, pagesX, minY, top, bySide);
            }
        }
    }

    /** The node of the rock columns {@code columns} above band {@code floor}, if there is any rock above it. */
    private static void outcrop(
            NodeStore store,
            EconomyRules economy,
            int[] columns,
            int floor,
            int sizeZ,
            int pagesX,
            int minY,
            int[] top,
            @Nullable VoxelSlice[] bySide) {
        List<Found> rock = new ArrayList<>();
        double cx = 0;
        double cz = 0;
        for (int c : columns) {
            int x = c / sizeZ; // a column's x and z, whole blocks
            int z = c % sizeZ;
            cx += x + 0.5;
            cz += z + 0.5;
        }
        cx /= columns.length;
        cz /= columns.length;
        for (int c : columns) {
            int x = c / sizeZ;
            int z = c % sizeZ;
            VoxelSlice slice = bySide[(z / VoxelSlice.SIDE) * pagesX + x / VoxelSlice.SIDE];
            if (slice == null) {
                continue;
            }
            for (int band = floor + 1; band <= top[c]; band++) {
                Material m = slice.materials()
                        .at(VoxelSlice.index(x % VoxelSlice.SIDE, band, z % VoxelSlice.SIDE, slice.height()));
                if (m == null || m.kind() != MaterialKind.STONE) {
                    continue;
                }
                int resource = economy.indexOf(m.resource());
                int subtype = resource < 0
                        ? -1
                        : economy.resources().get(resource).subtypes().indexOf(m.subtype());
                if (subtype >= 0) {
                    rock.add(new Found(key(x, band, z), x, minY + band, z, resource, subtype, m.amount(), 0));
                }
            }
        }
        if (rock.isEmpty()) {
            return;
        }
        double mx = cx;
        double mz = cz;
        rock.sort((a, b) -> {
            if (a.y() != b.y()) {
                return Integer.compare(b.y(), a.y()); // top-down
            }
            double da = (a.x() + 0.5 - mx) * (a.x() + 0.5 - mx) + (a.z() + 0.5 - mz) * (a.z() + 0.5 - mz);
            double db = (b.x() + 0.5 - mx) * (b.x() + 0.5 - mx) + (b.z() + 0.5 - mz) * (b.z() + 0.5 - mz);
            if (da != db) {
                return Double.compare(db, da); // outside-in
            }
            return a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z());
        });
        long amount = 0;
        long[] blocks = new long[rock.size()];
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < blocks.length; i++) {
            Found f = rock.get(i);
            amount += f.amount();
            blocks[i] = NodeStore.pack(f.x(), f.y(), f.z());
            lowest = Math.min(lowest, f.y());
        }
        int[] cells = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            cells[i] = NodeStore.cell(columns[i] / sizeZ, columns[i] % sizeZ);
        }
        Found first = rock.get(0);
        store.add(
                MaterialKind.STONE,
                first.resource(),
                first.subtype(),
                first.amount(),
                amount,
                cx,
                cz,
                lowest,
                blocks,
                ring(cells));
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
