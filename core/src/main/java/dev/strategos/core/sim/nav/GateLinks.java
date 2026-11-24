package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.Allies;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the gates join on one grid (PATH-12): per gate, the surfaces standing beside its columns and the components
 * they are in. A crossing joins two such surfaces at least two columns apart, at the door's cost of 1.5 a block, when
 * their floors differ by a block at most. The gate graph is tiny (spec §6: at most 64 gates), so reachability over
 * it is a few loops; the per-faction {@link Pass} is made once per grid and gate-set version.
 */
@CoreApi
public final class GateLinks {
    /** The door's cost per block, as {@link GridAStar#step} charges for a door surface. */
    static final double DOOR_COST = 1.5;

    private static final int MAX_STEP = 16; // a block, in sixteenths

    private final SurfaceGrid grid;
    private final int[] owners;
    private final int[][] sides; // per gate: the nodes beside it, ascending
    private final int[][] columns; // per gate: its columns packed x << 16 | z, ascending
    private final Map<Integer, Pass> passes = new TreeMap<>();

    private GateLinks(SurfaceGrid grid, int[] owners, int[][] sides, int[][] columns) {
        this.grid = grid;
        this.owners = owners;
        this.sides = sides;
        this.columns = columns;
    }

    static GateLinks of(SurfaceGrid grid, int[] ids, int[] owners, int[][] columns) {
        int[][] sides = new int[ids.length][];
        int[][] sorted = new int[ids.length][];
        for (int g = 0; g < ids.length; g++) {
            java.util.TreeSet<Integer> nodes = new java.util.TreeSet<>();
            java.util.Set<Integer> own = new java.util.TreeSet<>();
            for (int c : columns[g]) {
                own.add(c);
            }
            for (int c : columns[g]) {
                int x = c >>> 16;
                int z = c & 0xFFFF;
                for (int dir = 0; dir < Dir.COUNT; dir++) {
                    if (Dir.diagonal(dir)) {
                        continue;
                    }
                    int nx = x + Dir.dx(dir);
                    int nz = z + Dir.dz(dir);
                    if (nx < 0 || nz < 0 || nx >= grid.sizeX() || nz >= grid.sizeZ() || own.contains(nx << 16 | nz)) {
                        continue;
                    }
                    for (int layer = 0; layer < grid.count(nx, nz); layer++) {
                        nodes.add(grid.node(nx, nz, layer));
                    }
                }
            }
            sides[g] = nodes.stream().mapToInt(Integer::intValue).toArray();
            sorted[g] = columns[g].clone();
            Arrays.sort(sorted[g]);
        }
        return new GateLinks(grid, owners, sides, sorted);
    }

    SurfaceGrid grid() {
        return grid;
    }

    /** The crossings a unit of {@code faction} may make: those of the gates whose owner is allied with it. */
    Pass pass(int faction, Allies allies) {
        return passes.computeIfAbsent(faction, f -> buildPass(f, allies));
    }

    private Pass buildPass(int faction, Allies allies) {
        TreeMap<Integer, java.util.List<long[]>> byNode = new TreeMap<>(); // node -> {target, cost bits}
        java.util.TreeSet<Long> componentPairs = new java.util.TreeSet<>();
        for (int g = 0; g < owners.length; g++) {
            if (!allies.allied(faction, owners[g])) {
                continue;
            }
            int[] nodes = sides[g];
            for (int a : nodes) {
                for (int b : nodes) {
                    int ax = grid.nodeX(a);
                    int az = grid.nodeZ(a);
                    int bx = grid.nodeX(b);
                    int bz = grid.nodeZ(b);
                    if (Math.max(Math.abs(ax - bx), Math.abs(az - bz)) < 2) {
                        continue; // beside each other: walked, not crossed
                    }
                    if (!crosses(columns[g], ax, az, bx, bz)) {
                        continue; // the same side of the gate: nothing to cross
                    }
                    int rise = Math.abs(Surface.floor(grid.surface(ax, az, SurfaceGrid.nodeLayer(a)))
                            - Surface.floor(grid.surface(bx, bz, SurfaceGrid.nodeLayer(b))));
                    if (rise > MAX_STEP) {
                        continue;
                    }
                    double cost = DOOR_COST * GridAStar.octile(ax, az, bx, bz);
                    byNode.computeIfAbsent(a, k -> new java.util.ArrayList<>())
                            .add(new long[] {b, Double.doubleToLongBits(cost)});
                    int ca = grid.component(a);
                    int cb = grid.component(b);
                    if (ca != cb) {
                        componentPairs.add((long) ca << 32 | cb);
                    }
                }
            }
        }
        int[] from = new int[byNode.size()];
        int[][] to = new int[from.length][];
        double[][] cost = new double[from.length][];
        int k = 0;
        for (Map.Entry<Integer, java.util.List<long[]>> e : byNode.entrySet()) {
            from[k] = e.getKey();
            to[k] = new int[e.getValue().size()];
            cost[k] = new double[to[k].length];
            for (int j = 0; j < to[k].length; j++) {
                to[k][j] = (int) e.getValue().get(j)[0];
                cost[k][j] = Double.longBitsToDouble(e.getValue().get(j)[1]);
            }
            k++;
        }
        long[] pairs = componentPairs.stream().mapToLong(Long::longValue).toArray();
        return new Pass(from, to, cost, pairs);
    }

    /** Whether the straight segment between two columns passes through a column of the gate (sorted, packed). */
    private static boolean crosses(int[] gate, int ax, int az, int bx, int bz) {
        int dx = Math.abs(bx - ax);
        int dz = Math.abs(bz - az);
        int sx = Integer.compare(bx, ax);
        int sz = Integer.compare(bz, az);
        int err = dx - dz;
        int x = ax;
        int z = az;
        while (true) {
            if (Arrays.binarySearch(gate, x << 16 | z) >= 0) {
                return true;
            }
            if (x == bx && z == bz) {
                return false;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    /** One faction's crossings: from a node beside an allied gate to the nodes across it, and the same between components. */
    @CoreApi
    public static final class Pass {
        private final int[] from;
        private final int[][] to;
        private final double[][] cost;
        private final long[] components;

        private Pass(int[] from, int[][] to, double[][] cost, long[] components) {
            this.from = from;
            this.to = to;
            this.cost = cost;
            this.components = components;
        }

        public boolean empty() {
            return from.length == 0;
        }

        /** The index of {@code node}'s crossings, or a negative number when it has none. */
        int indexOf(int node) {
            return Arrays.binarySearch(from, node);
        }

        int[] targets(int index) {
            return to[index];
        }

        double[] costs(int index) {
            return cost[index];
        }

        /** Adds to {@code reach} what a unit that reaches those components also reaches across these gates. */
        void extend(Components c, BitSet reach) {
            boolean grown = true;
            while (grown) {
                grown = false;
                for (long pair : components) {
                    if (reach.get((int) (pair >>> 32)) && !reach.get((int) pair)) {
                        reach.or(c.reachable((int) pair));
                        grown = true;
                    }
                }
            }
        }
    }
}
