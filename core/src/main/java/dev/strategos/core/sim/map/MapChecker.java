package dev.strategos.core.sim.map;

import dev.strategos.core.content.MapWaypoint;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.TerrainJob;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.econ.Deposits;
import dev.strategos.core.sim.econ.Livestock;
import dev.strategos.core.sim.nav.Dir;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The map checker (MAP-02, spec §2.10): fairness between the starts and the terrain's documented jobs, from the
 * terrain's surfaces and its resources. Resources within {@value #RESOURCE_RADIUS} blocks of each start agree within
 * 5 % per resource; the walk from each start to its dropsite sites agrees within 10 % per kind of site; every start
 * reaches every other for every mobility class; and each {@link TerrainJob} holds. Pure: the generator's voxels in L1,
 * an imported map's pages later.
 */
@CoreApi
public final class MapChecker {
    public static final int RESOURCE_RADIUS = 48;
    public static final double RESOURCE_TOLERANCE = 0.05;
    public static final double DISTANCE_TOLERANCE = 0.10;

    private static final int EAST = direction(1, 0);
    private static final int SOUTH = direction(0, 1);

    private MapChecker() {}

    /** A resource where it lies: a tree's logs, a bush, a deposit or animals, in the templates' amounts. */
    public record Site(String resource, int x, int z, double amount) {}

    /** The ground's top block of a column, and whether nothing stands on it. */
    public interface Columns {
        int groundTop(int x, int z);

        boolean clear(int x, int z);
    }

    /**
     * What the checker reads: the starts; a surface grid per mobility class; the ground; the resources; per start, its
     * dropsite sites by kind (the same kinds at every start); the terrain's jobs.
     */
    public record Input(
            List<StartSlot> starts,
            Map<MobilityClass, SurfaceGrid> grids,
            Columns columns,
            List<Site> resources,
            List<Map<String, MapWaypoint>> dropsites,
            List<TerrainJob> jobs) {
        public Input {
            starts = List.copyOf(starts);
            EnumMap<MobilityClass, SurfaceGrid> byClass = new EnumMap<>(MobilityClass.class);
            byClass.putAll(grids);
            grids = Collections.unmodifiableMap(byClass);
            resources = List.copyOf(resources);
            dropsites = List.copyOf(dropsites);
            jobs = List.copyOf(jobs);
        }
    }

    /** The problems found (none: the map passes) and the numbers measured, one line each. */
    public record Report(List<String> problems, List<String> measures) {
        public Report {
            problems = List.copyOf(problems);
            measures = List.copyOf(measures);
        }

        public boolean ok() {
            return problems.isEmpty();
        }
    }

    public static Report check(Input in) {
        List<String> problems = new ArrayList<>();
        List<String> measures = new ArrayList<>();
        if (in.starts().size() < 2) {
            problems.add("fewer than two starts");
            return new Report(problems, measures);
        }
        resources(in, problems, measures);
        reach(in, problems, measures);
        dropsites(in, problems, measures);
        for (TerrainJob job : in.jobs()) {
            switch (job.kind()) {
                case TerrainJob.HILL -> hill(in, job, problems, measures);
                case TerrainJob.FOREST_LINE, TerrainJob.RIVER -> crossings(in, job, problems, measures);
                case TerrainJob.RESOURCE -> resourceJob(in, job, problems, measures);
                case TerrainJob.ROOM -> room(in, job, problems, measures);
                default -> problems.add("unknown terrain job " + job.kind());
            }
        }
        return new Report(problems, measures);
    }

    /**
     * The resources a scenario puts at the starts: its deposits (their {@code Deposit/amount} of their resource) and
     * its animals ({@code Fauna/meat} of food each), where {@code ScenarioSetup} spawns them.
     */
    public static List<Site> scenarioSites(Scenario scenario, List<StartSlot> starts, TemplateSet templates) {
        List<Site> sites = new ArrayList<>();
        for (int k = 0; k < scenario.slots().size() && k < starts.size(); k++) {
            StartSlot at = starts.get(k);
            for (Scenario.Entity e : scenario.slots().get(k).entities()) {
                UnitTemplate t = templates.byId(e.template());
                if (t == null) {
                    throw new IllegalArgumentException(
                            "no template " + e.template().value());
                }
                int x = at.x() + e.dx();
                int z = at.z() + e.dz();
                if (Deposits.isDeposit(templates, t.netIndex())) {
                    String resource = text(templates, t.netIndex(), Deposits.RESOURCE);
                    sites.add(new Site(resource, x, z, number(templates, t.netIndex(), Deposits.AMOUNT)));
                } else if (Livestock.isFauna(templates, t.netIndex())) {
                    sites.add(new Site("food", x, z, number(templates, t.netIndex(), Livestock.MEAT) * e.count()));
                }
            }
        }
        return sites;
    }

    private static void resources(Input in, List<String> problems, List<String> measures) {
        List<Map<String, Double>> totals = new ArrayList<>();
        TreeSet<String> kinds = new TreeSet<>();
        for (StartSlot s : in.starts()) {
            Map<String, Double> t = new TreeMap<>();
            for (Site site : in.resources()) {
                double dx = site.x() - s.x();
                double dz = site.z() - s.z();
                if (dx * dx + dz * dz <= (double) RESOURCE_RADIUS * RESOURCE_RADIUS) {
                    t.merge(site.resource(), site.amount(), Double::sum);
                    kinds.add(site.resource());
                }
            }
            totals.add(t);
        }
        for (String kind : kinds) {
            double[] amounts = new double[totals.size()];
            for (int k = 0; k < amounts.length; k++) {
                amounts[k] = totals.get(k).getOrDefault(kind, 0.0);
            }
            measures.add("resource " + kind + " within " + RESOURCE_RADIUS + ": " + Arrays.toString(amounts));
            if (spread(amounts) > RESOURCE_TOLERANCE) {
                problems.add("resource " + kind + " differs between the starts by more than 5 %: "
                        + Arrays.toString(amounts));
            }
        }
    }

    private static void reach(Input in, List<String> problems, List<String> measures) {
        for (MobilityClass mobility : MobilityClass.values()) {
            SurfaceGrid grid = in.grids().get(mobility);
            if (grid == null) {
                problems.add("no surface grid for " + mobility);
                continue;
            }
            int first = node(grid, in.starts().getFirst());
            if (first < 0) {
                problems.add("start 0 stands on no surface for " + mobility);
                continue;
            }
            BitSet reached = grid.reachableFrom(first);
            int missed = 0;
            for (int k = 1; k < in.starts().size(); k++) {
                int n = node(grid, in.starts().get(k));
                if (n < 0 || !reached.get(grid.component(n))) {
                    problems.add("start " + k + " is not reached from start 0 by " + mobility);
                    missed++;
                }
            }
            measures.add("reach " + mobility + ": " + (in.starts().size() - 1 - missed) + " of "
                    + (in.starts().size() - 1) + " other starts");
        }
    }

    private static void dropsites(Input in, List<String> problems, List<String> measures) {
        if (in.dropsites().isEmpty()) {
            return;
        }
        if (in.dropsites().size() != in.starts().size()) {
            problems.add(in.dropsites().size() + " sets of dropsite sites for "
                    + in.starts().size() + " starts");
            return;
        }
        SurfaceGrid grid = in.grids().get(MobilityClass.INFANTRY);
        if (grid == null) {
            return;
        }
        List<double[]> distances = new ArrayList<>();
        for (int k = 0; k < in.starts().size(); k++) {
            int from = node(grid, in.starts().get(k));
            distances.add(from < 0 ? new double[0] : walk(grid, from));
        }
        for (String kind : new TreeSet<>(in.dropsites().getFirst().keySet())) {
            double[] d = new double[in.starts().size()];
            for (int k = 0; k < d.length; k++) {
                MapWaypoint site = in.dropsites().get(k).get(kind);
                int n = site == null ? -1 : node(grid, site.x(), site.y(), site.z());
                d[k] = n < 0 || distances.get(k).length == 0
                        ? Double.POSITIVE_INFINITY
                        : distances.get(k)[n];
            }
            measures.add("walk to " + kind + ": " + Arrays.toString(d));
            if (Arrays.stream(d).anyMatch(Double::isInfinite)) {
                problems.add("a start does not reach its " + kind + " site: " + Arrays.toString(d));
            } else if (spread(d) > DISTANCE_TOLERANCE) {
                problems.add("the walk to the " + kind + " site differs by more than 10 %: " + Arrays.toString(d));
            }
        }
    }

    private static void hill(Input in, TerrainJob job, List<String> problems, List<String> measures) {
        int top = in.columns().groundTop(job.x0(), job.z0());
        int below = in.columns().groundTop(job.x1(), job.z1());
        measures.add("hill at (" + job.x0() + ", " + job.z0() + "): " + (top - below) + " above (" + job.x1() + ", "
                + job.z1() + ")");
        if (top - below < job.size()) {
            problems.add("the hill at (" + job.x0() + ", " + job.z0() + ") rises " + (top - below) + " blocks, not "
                    + job.size());
        }
        SurfaceGrid grid = in.grids().get(MobilityClass.INFANTRY);
        if (grid != null) {
            int from = node(grid, in.starts().getFirst());
            int hill = node(grid, job.x0(), top + 1, job.z0());
            if (from < 0 || hill < 0 || !grid.reachableFrom(from).get(grid.component(hill))) {
                problems.add("no unit reaches the hill at (" + job.x0() + ", " + job.z0() + ")");
            }
        }
    }

    /** A band crossed only at its gaps or fords: the runs of rows a unit crosses, counted and measured. */
    private static void crossings(Input in, TerrainJob job, List<String> problems, List<String> measures) {
        SurfaceGrid grid = in.grids().get(MobilityClass.INFANTRY);
        if (grid == null) {
            return;
        }
        int x0 = Math.min(job.x0(), job.x1());
        int x1 = Math.max(job.x0(), job.x1());
        int z0 = Math.min(job.z0(), job.z1());
        int z1 = Math.max(job.z0(), job.z1());
        boolean alongZ = z1 - z0 >= x1 - x0;
        int from = alongZ ? z0 : x0;
        int to = alongZ ? z1 : x1;
        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int row = from; row <= to + 1; row++) {
            boolean open = row <= to
                    && (alongZ
                            ? crossesRow(grid, x0 - 1, x1 + 1, row, true)
                            : crossesRow(grid, z0 - 1, z1 + 1, row, false));
            if (open && start < 0) {
                start = row;
            } else if (!open && start >= 0) {
                runs.add(new int[] {start, row - 1});
                start = -1;
            }
        }
        StringBuilder widths = new StringBuilder();
        for (int[] r : runs) {
            widths.append(widths.isEmpty() ? "" : ", ")
                    .append(r[1] - r[0] + 1)
                    .append(" at ")
                    .append(r[0]);
        }
        String name = job.kind() + " (" + x0 + ", " + z0 + ")–(" + x1 + ", " + z1 + ")";
        measures.add(name + ": " + runs.size() + " crossings [" + widths + "]");
        if (runs.size() != job.count()) {
            problems.add(name + " is crossed at " + runs.size() + " places, not " + job.count() + ": [" + widths + "]");
        }
        for (int[] r : runs) {
            if (r[1] - r[0] + 1 < job.size()) {
                problems.add(
                        name + " has a crossing " + (r[1] - r[0] + 1) + " wide at " + r[0] + ", under " + job.size());
            }
        }
    }

    /**
     * Whether a unit walks across a band in one row: from any surface at {@code a} to any at {@code b}, a step east
     * (or south) at a time, over passable surfaces linked in that direction.
     */
    private static boolean crossesRow(SurfaceGrid grid, int a, int b, int row, boolean east) {
        int dir = east ? EAST : SOUTH;
        BitSet layers = new BitSet();
        int x = east ? a : row;
        int z = east ? row : a;
        if (!grid.inside(x, z)) {
            return false;
        }
        for (int l = 0; l < grid.count(x, z); l++) {
            if (Surface.passable(grid.surface(x, z, l))) {
                layers.set(l);
            }
        }
        for (int step = a; step < b && !layers.isEmpty(); step++) {
            BitSet next = new BitSet();
            for (int l = layers.nextSetBit(0); l >= 0; l = layers.nextSetBit(l + 1)) {
                int n = grid.neighbour(x, z, l, dir);
                if (n >= 0 && Surface.passable(grid.surface(x + Dir.dx(dir), z + Dir.dz(dir), n))) {
                    next.set(n);
                }
            }
            x += Dir.dx(dir);
            z += Dir.dz(dir);
            layers = next;
        }
        return !layers.isEmpty();
    }

    private static void resourceJob(Input in, TerrainJob job, List<String> problems, List<String> measures) {
        double total = 0;
        for (Site site : in.resources()) {
            double dx = site.x() - job.x0();
            double dz = site.z() - job.z0();
            if (site.resource().equals(job.resource()) && dx * dx + dz * dz <= (double) job.size() * job.size()) {
                total += site.amount();
            }
        }
        measures.add(job.resource() + " within " + job.size() + " of (" + job.x0() + ", " + job.z0() + "): " + total);
        if (total < job.count()) {
            problems.add("only " + total + " " + job.resource() + " within " + job.size() + " of (" + job.x0() + ", "
                    + job.z0() + "), not " + job.count());
        }
    }

    private static void room(Input in, TerrainJob job, List<String> problems, List<String> measures) {
        int x0 = Math.min(job.x0(), job.x1());
        int x1 = Math.max(job.x0(), job.x1());
        int z0 = Math.min(job.z0(), job.z1());
        int z1 = Math.max(job.z0(), job.z1());
        int level = in.columns().groundTop(x0, z0);
        int uneven = 0;
        int blocked = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                uneven += in.columns().groundTop(x, z) != level ? 1 : 0;
                blocked += in.columns().clear(x, z) ? 0 : 1;
            }
        }
        long area = (long) (x1 - x0 + 1) * (z1 - z0 + 1);
        long needed = (long) job.count() * job.size() * job.size();
        String name = "room (" + x0 + ", " + z0 + ")–(" + x1 + ", " + z1 + ")";
        measures.add(name + ": " + area + " level, clear columns for " + needed);
        if (uneven > 0 || blocked > 0) {
            problems.add(name + " has " + uneven + " columns off level and " + blocked + " not clear");
        }
        if (area < needed) {
            problems.add(name + " holds " + area + " columns, under the " + needed + " of " + job.count() + " fields");
        }
    }

    /** The largest relative gap between the values: (max - min) / max, 0 when all are 0. */
    private static double spread(double[] values) {
        double min = Arrays.stream(values).min().orElse(0);
        double max = Arrays.stream(values).max().orElse(0);
        return max <= 0 ? 0 : (max - min) / max;
    }

    /** Walking distance from {@code from} to every node, over passable surfaces; diagonal steps cost √2. */
    private static double[] walk(SurfaceGrid grid, int from) {
        int nodes = 0;
        for (int x = 0; x < grid.sizeX(); x++) {
            for (int z = 0; z < grid.sizeZ(); z++) {
                for (int l = 0; l < grid.count(x, z); l++) {
                    nodes = Math.max(nodes, grid.node(x, z, l) + 1);
                }
            }
        }
        double[] dist = new double[nodes];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        dist[from] = 0;
        PriorityQueue<double[]> queue = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        queue.add(new double[] {0, from});
        while (!queue.isEmpty()) {
            double[] head = queue.poll();
            int node = (int) head[1];
            if (head[0] > dist[node]) {
                continue;
            }
            int x = grid.nodeX(node);
            int z = grid.nodeZ(node);
            int layer = layerOf(grid, x, z, node);
            if (layer < 0 || !Surface.passable(grid.surface(x, z, layer))) {
                continue;
            }
            for (int dir = 0; dir < Dir.COUNT; dir++) {
                int n = grid.neighbour(x, z, layer, dir);
                if (n < 0) {
                    continue;
                }
                int next = grid.node(x + Dir.dx(dir), z + Dir.dz(dir), n);
                double d = dist[node] + (Dir.dx(dir) != 0 && Dir.dz(dir) != 0 ? Math.sqrt(2) : 1);
                if (d < dist[next]) {
                    dist[next] = d;
                    queue.add(new double[] {d, next});
                }
            }
        }
        return dist;
    }

    private static int layerOf(SurfaceGrid grid, int x, int z, int node) {
        for (int l = 0; l < grid.count(x, z); l++) {
            if (grid.node(x, z, l) == node) {
                return l;
            }
        }
        return -1;
    }

    private static int node(SurfaceGrid grid, StartSlot s) {
        return node(grid, s.x(), s.y(), s.z());
    }

    /** The node of the passable surface in column ({@code x}, {@code z}) whose floor is nearest the bottom of {@code y}. */
    private static int node(SurfaceGrid grid, int x, int y, int z) {
        if (!grid.inside(x, z)) {
            return -1;
        }
        int best = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int l = 0; l < grid.count(x, z); l++) {
            int s = grid.surface(x, z, l);
            int gap = Math.abs(Surface.floor(s) - y * 16);
            if (Surface.passable(s) && gap < bestGap) {
                bestGap = gap;
                best = l;
            }
        }
        return best < 0 ? -1 : grid.node(x, z, best);
    }

    private static int direction(int dx, int dz) {
        for (int dir = 0; dir < Dir.COUNT; dir++) {
            if (Dir.dx(dir) == dx && Dir.dz(dir) == dz) {
                return dir;
            }
        }
        throw new IllegalStateException("no direction (" + dx + ", " + dz + ")");
    }

    private static double number(TemplateSet templates, int netIndex, String path) {
        ValuePath p = templates.valuePaths().byPath(path);
        return p == null ? 0 : templates.value(netIndex, p.id());
    }

    private static String text(TemplateSet templates, int netIndex, String path) {
        ValuePath p = templates.tokenPaths().byPath(path);
        String value = p == null ? null : templates.text(netIndex, p.id());
        return value == null ? "" : value;
    }
}
