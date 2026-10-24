package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What AI-02's acceptance measures of an economy match, per faction, sampled once a second: each worker's longest idle
 * stretch; after minute 3, the furthest any resource's share of the gatherers, a minute's mean, strays from the
 * script's ratio; the
 * longest a site of its waited with no progress and no builder ordered to it; the own units and nodes near its centre
 * the centre's ground does not reach, once a minute; whether it sent gatherers to animals; and what it gathered.
 */
final class EconomyWatch implements HeadlessMatch.Watcher {
    /** Shares are held from minute 3. */
    static final long SHARES_FROM = 3_600;

    /** Shares are a minute's mean: six samples ten seconds apart, so one worker between jobs is no stray. */
    static final int WINDOW = 6;

    /** How far from the centre a node counts as the base's. */
    static final double NEAR = 40;

    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    private final StrategyScript script;
    private final Map<Integer, Long> idleSince = new TreeMap<>();
    private final Map<Integer, long[]> sites =
            new TreeMap<>(); // a site's last progress and the tick it was seen to move
    final long[] longestIdle = new long[2];
    final String[] worstIdle = {"", ""};
    final double[] shareDeviation = new double[2];
    final String[] worstShare = {"", ""};
    private final List<List<double[]>> recent = List.of(new ArrayList<>(), new ArrayList<>());
    final long[] siteWait = new long[2];
    final List<String> walledOff = new ArrayList<>();
    final boolean[] livestock = new boolean[2];
    final List<List<Long>> gathered = new ArrayList<>(List.of(List.of(), List.of()));

    EconomyWatch(StrategyScript script) {
        this.script = script;
    }

    @Override
    public void tick(SimHarness h, TickOutputs out) {
        long t = out.tick();
        if (t % 20 != 0) {
            return;
        }
        MatchQueries q = h.queries();
        workers(q, t);
        sites(q, t);
        if (t >= SHARES_FROM && t % 200 == 0) {
            shares(q, t);
        }
        if (t % 1_200 == 0) {
            walls(q, t);
        }
        for (int f = 0; f < 2; f++) {
            gathered.set(f, q.treasury(new FactionSlot(f)).gathered());
        }
    }

    private void workers(MatchQueries q, long t) {
        Set<Integer> alive = new TreeSet<>();
        for (int handle : q.unitHandles()) {
            UnitView u = q.unit(handle);
            if (u == null || u.faction().value() >= 2 || !u.template().equals(TemplateSet.WORKER)) {
                continue;
            }
            int f = u.faction().value();
            alive.add(handle);
            if (u.state() == UnitState.IDLE) {
                long since = idleSince.computeIfAbsent(handle, k -> t);
                if (t - since > longestIdle[f]) {
                    longestIdle[f] = t - since;
                    worstIdle[f] = "from tick " + since + " at " + (int) u.x() + ", " + (int) u.z() + " carrying "
                            + q.carriedResource(handle);
                }
            } else {
                idleSince.remove(handle);
            }
            List<OrderQueues.Order> orders = q.orders(handle);
            if (!orders.isEmpty() && orders.get(0).kind() == OrderKind.GATHER) {
                for (NodeView n : q.nodes()) {
                    livestock[f] |= n.id() == orders.get(0).target() && n.kind() == MaterialKind.CARCASS;
                }
            }
        }
        idleSince.keySet().retainAll(alive);
    }

    private void sites(MatchQueries q, long t) {
        Set<Integer> builders = new TreeSet<>();
        for (int handle : q.unitHandles()) {
            List<OrderQueues.Order> orders = q.orders(handle);
            if (!orders.isEmpty() && orders.get(0).kind() == OrderKind.BUILD) {
                builders.add(orders.get(0).target());
            }
        }
        Set<Integer> open = new TreeSet<>();
        for (BuildingView b : q.buildings()) {
            if (b.faction() >= 2 || b.state() == BuildingStore.COMPLETE) {
                continue;
            }
            open.add(b.handle());
            long[] site = sites.computeIfAbsent(b.handle(), k -> new long[] {b.progress(), t});
            if (b.progress() != site[0] || builders.contains(b.handle())) {
                site[0] = b.progress();
                site[1] = t;
            }
            siteWait[b.faction()] = Math.max(siteWait[b.faction()], t - site[1]);
        }
        sites.keySet().retainAll(open);
    }

    private void shares(MatchQueries q, long t) {
        List<String> resources = new ArrayList<>();
        for (var r : q.config().rules().economy().resources()) {
            resources.add(r.id());
        }
        double weights = 0;
        for (String r : resources) {
            weights += script.ratio().getOrDefault(r, 0);
        }
        for (int f = 0; f < 2; f++) {
            int[] w = q.workers(new FactionSlot(f));
            int total = 0;
            for (int r = 0; r < resources.size(); r++) {
                total += w[r];
            }
            if (total == 0) {
                continue;
            }
            double[] share = new double[resources.size()];
            for (int r = 0; r < resources.size(); r++) {
                share[r] = (double) w[r] / total;
            }
            List<double[]> window = recent.get(f);
            window.add(share);
            if (window.size() > WINDOW) {
                window.remove(0);
            }
            if (window.size() < WINDOW) {
                continue;
            }
            for (int r = 0; r < resources.size(); r++) {
                double mean = 0;
                for (double[] sample : window) {
                    mean += sample[r] / WINDOW;
                }
                double target = script.ratio().getOrDefault(resources.get(r), 0) / weights;
                double off = Math.abs(mean - target);
                if (off > shareDeviation[f]) {
                    shareDeviation[f] = off;
                    worstShare[f] = "tick " + t + " " + resources.get(r) + " " + java.util.Arrays.toString(w);
                }
            }
        }
    }

    /** Own units and the base's nodes the ground round the civic centre does not reach. */
    private void walls(MatchQueries q, long t) {
        SurfaceGrid grid = q.surfaces();
        int minY = q.config().bounds().minY();
        for (BuildingView c : q.buildings()) {
            if (c.faction() >= 2 || !c.template().equals(CENTRE) || c.state() != BuildingStore.COMPLETE) {
                continue;
            }
            Footprint f = c.footprint();
            Set<Integer> home = new TreeSet<>();
            for (int x = f.anchorX() - 1; x <= f.anchorX() + f.spanX(); x++) {
                for (int z = f.anchorZ() - 1; z <= f.anchorZ() + f.spanZ(); z++) {
                    home.addAll(components(grid, x, z));
                }
            }
            double cx = f.anchorX() + f.spanX() / 2.0;
            double cz = f.anchorZ() + f.spanZ() / 2.0;
            for (int handle : q.unitHandlesOf(new FactionSlot(c.faction()))) {
                UnitView u = q.unit(handle);
                if (u == null) {
                    continue;
                }
                int node = PathService.startNode(grid, u.x(), u.y(), u.z(), minY);
                if (node >= 0 && !home.contains(grid.component(node))) {
                    walledOff.add("tick " + t + ": faction " + c.faction() + "'s unit at " + u.x() + ", " + u.z());
                }
            }
            for (NodeView n : q.nodes()) {
                if (n.amount() <= 0
                        || n.kind() == MaterialKind.CARCASS
                        || DetMath.length(n.anchorX() - cx, n.anchorZ() - cz) > NEAR) {
                    continue;
                }
                boolean reached = false;
                int nx = (int) Math.floor(n.anchorX());
                int nz = (int) Math.floor(n.anchorZ());
                for (int x = nx - 2; x <= nx + 2 && !reached; x++) {
                    for (int z = nz - 2; z <= nz + 2 && !reached; z++) {
                        for (int k : components(grid, x, z)) {
                            reached |= home.contains(k);
                        }
                    }
                }
                if (!reached) {
                    walledOff.add("tick " + t + ": faction " + c.faction() + "'s " + n.resource() + " node " + n.id());
                }
            }
        }
    }

    private static List<Integer> components(SurfaceGrid grid, int x, int z) {
        List<Integer> out = new ArrayList<>();
        if (!grid.inside(x, z)) {
            return out;
        }
        for (int layer = 0; layer < grid.count(x, z); layer++) {
            if (Surface.passable(grid.surface(x, z, layer))) {
                out.add(grid.component(grid.node(x, z, layer)));
            }
        }
        return out;
    }
}
