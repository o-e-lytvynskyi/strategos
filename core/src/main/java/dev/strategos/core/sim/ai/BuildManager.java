package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the script's build order and houses (04 §14's BuildManager). A step starts once the population reaches its
 * number; its building is placed with the two workers nearest the site, at the next of the script's sites round the
 * civic centre (mirrored for the second start slot); a placement the server refused leaves no new building, so after
 * {@value #RETRY} ticks the next site is tried. The step is done once one more of its building stands, in any state.
 * Before that, a house goes up whenever the free population falls to the margin and none is being built: no room to
 * grow comes first. An own building left unfinished with nobody on it gets the nearest free worker back (M4 T2). Then,
 * before the build order, the script's {@code dropsite} goes up by a resource running out near its dropsites (AI-02).
 */
final class BuildManager implements AiModule {
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    /** Ticks to wait before trying another site. */
    static final long RETRY = 100;

    /** How far from an own dropsite a node is near it. */
    static final double FAR = 24;

    /** A resource is running out near its dropsites below this much in their nodes, in thousandths. */
    static final long RUNNING_OUT = 600_000;

    private final StrategyScript script;
    private final AiMemory memory;

    BuildManager(StrategyScript script, AiMemory memory) {
        this.script = script;
        this.memory = memory;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        Base base = Base.of(view);
        if (base == null || budget.exhausted()) {
            return;
        }
        staff(view, budget, sink);
        long tick = view.tick();
        boolean waited = memory.get(Slots.PLACED) == 0 || tick - (memory.get(Slots.PLACED) - 1) >= RETRY;
        if (waited
                && view.populationCap() - view.populationUsed() <= script.housingMargin()
                && !building(view, HOUSE)
                && view.affords(HOUSE)) {
            place(view, budget, sink, base, HOUSE, ""); // no room to grow comes first
            return;
        }
        if (waited && !script.dropsite().isEmpty() && expand(view, budget, sink, base)) {
            return; // a dropsite by a far node before the build order: placed now, or the stock kept for it
        }
        int step = (int) memory.get(Slots.STEP);
        if (step < script.build().size()) {
            StrategyScript.Step s = script.build().get(step);
            int count = count(view, s.building());
            if (memory.get(Slots.STEP_BASE) == 0) {
                memory.set(Slots.STEP_BASE, count + 1L);
            }
            if (count > memory.get(Slots.STEP_BASE) - 1) {
                memory.set(Slots.STEP, step + 1L);
                memory.set(Slots.STEP_BASE, 0);
                memory.set(Slots.DUE_SINCE, 0);
            } else if (script.reserve()
                    && view.populationUsed() >= s.population()
                    && memory.get(Slots.DUE_SINCE) == 0) {
                memory.set(Slots.DUE_SINCE, tick + 1); // due: the stock is kept for it a while (AI-02)
            }
            if (count <= memory.get(Slots.STEP_BASE) - 1
                    && waited
                    && view.populationUsed() >= s.population()
                    && view.affords(s.building())) {
                place(view, budget, sink, base, s.building(), s.near());
            }
        }
    }

    /** How long the stock is kept for a due building before soldiers may have it again: a minute (AI-02). */
    static final long RESERVE_TICKS = 1_200;

    /**
     * What the stock is kept for (AI-02's {@code reserve}; null without it or with nothing due): a dropsite by a far
     * node the stock did not cover, until it is placed, or else the building the build order waits on, its step's
     * population reached and not yet placed, for at most {@value #RESERVE_TICKS} ticks.
     */
    static @org.jspecify.annotations.Nullable TemplateId due(AiView view, StrategyScript script, AiMemory memory) {
        if (script.reserve() && memory.get(Slots.EXPAND_SINCE) != 0) {
            return new TemplateId(script.dropsite()); // with its resource running out, nothing else pays more
        }
        return stepDue(view, script, memory);
    }

    /** The build order's part of {@link #due}: its next building, due and unplaced, for {@value #RESERVE_TICKS} ticks. */
    static @org.jspecify.annotations.Nullable TemplateId stepDue(AiView view, StrategyScript script, AiMemory memory) {
        int step = (int) memory.get(Slots.STEP);
        long since = memory.get(Slots.DUE_SINCE);
        if (!script.reserve()
                || step >= script.build().size()
                || since == 0
                || view.tick() - (since - 1) > RESERVE_TICKS) {
            return null;
        }
        return script.build().get(step).building();
    }

    /** Every own unfinished building with no worker on its site gets the nearest one not building elsewhere. */
    private static void staff(AiView view, AiBudget budget, CommandSink sink) {
        for (BuildingView b : view.buildings()) {
            if (b.state() == BuildingStore.COMPLETE || budget.exhausted()) {
                continue;
            }
            boolean staffed = false;
            for (UnitView u : view.units()) {
                staffed |= view.buildingSite(u) == b.handle();
            }
            if (staffed) {
                continue;
            }
            Footprint f = b.footprint();
            List<Integer> builders =
                    nearestWorkers(view, f.anchorX() + f.spanX() / 2.0, f.anchorZ() + f.spanZ() / 2.0, 1);
            if (!builders.isEmpty()) {
                budget.spend(10);
                sink.send(Build.replace(UnitSet.of(builders.get(0)), b.handle()));
            }
        }
    }

    /**
     * AI-02's dropsites near resources: once the script's {@code dropsite} stands finished, for the first resource of
     * the ratio it takes that is running out near the own dropsites of it (under {@value #RUNNING_OUT} thousandths in
     * the nodes within {@value #FAR} blocks of them), one by the nearest node beyond within the sites' reach; with no
     * stock for it, the stock is kept for it ({@link #due}). One at a time. Whether it was placed or is waited for.
     */
    private boolean expand(AiView view, AiBudget budget, CommandSink sink, Base base) {
        TemplateId template = new TemplateId(script.dropsite());
        if (building(view, template)) {
            return false;
        }
        java.util.Set<String> takes = new java.util.TreeSet<>();
        for (BuildingView b : view.buildings()) {
            if (b.template().equals(template) && b.state() == BuildingStore.COMPLETE) {
                takes.addAll(b.dropsite());
            }
        }
        for (String resource : script.ratio().keySet()) {
            NodeView far = takes.contains(resource) ? farNode(view, base, resource) : null;
            if (far == null) {
                continue;
            }
            if (!view.affords(template)) {
                if (memory.get(Slots.EXPAND_SINCE) == 0) {
                    memory.set(Slots.EXPAND_SINCE, view.tick() + 1);
                }
                return true;
            }
            memory.set(Slots.EXPAND_SINCE, 0);
            return placeAt(view, budget, sink, base, template, far.anchorX(), far.anchorZ());
        }
        memory.set(Slots.EXPAND_SINCE, 0);
        return false;
    }

    /**
     * With {@code resource} running out near its own dropsites, its live node nearest the centre among those farther
     * from them and within {@link SiteFinder#REACH} of the centre, where a site may go; null otherwise.
     */
    private static @org.jspecify.annotations.Nullable NodeView farNode(AiView view, Base base, String resource) {
        NodeView best = null;
        double bestD = Double.POSITIVE_INFINITY;
        long near = 0;
        for (NodeView n : view.nodes()) {
            if (!n.resource().equals(resource)
                    || n.amount() <= 0
                    || n.kind() == MaterialKind.CROP
                    || n.kind() == MaterialKind.CARCASS) {
                continue;
            }
            if (toDropsite(view, resource, n.anchorX(), n.anchorZ()) <= FAR) {
                near += n.amount();
                continue;
            }
            double d = DetMath.length(n.anchorX() - base.x(), n.anchorZ() - base.z());
            if (d < bestD && d <= SiteFinder.REACH) {
                bestD = d;
                best = n;
            }
        }
        return near < RUNNING_OUT ? best : null;
    }

    /** How far (x, z) lies from the nearest own building, in any state, that takes {@code resource}: to its edge. */
    private static double toDropsite(AiView view, String resource, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (BuildingView b : view.buildings()) {
            if (b.dropsite().contains(resource)) {
                Footprint f = b.footprint();
                double dx = Math.max(0, Math.max(f.anchorX() - x, x - (f.anchorX() + f.spanX())));
                double dz = Math.max(0, Math.max(f.anchorZ() - z, z - (f.anchorZ() + f.spanZ())));
                best = Math.min(best, DetMath.length(dx, dz));
            }
        }
        return best;
    }

    private void place(AiView view, AiBudget budget, CommandSink sink, Base base, TemplateId template, String near) {
        double[] want = want(view, base, near);
        placeAt(view, budget, sink, base, template, want[0], want[1]);
    }

    /** Places {@code template} by (wantX, wantZ); whether a placement went out. */
    private boolean placeAt(
            AiView view,
            AiBudget budget,
            CommandSink sink,
            Base base,
            TemplateId template,
            double wantX,
            double wantZ) {
        UnitTemplate t = view.templates().byId(template);
        Blueprint bp = t == null ? null : view.templates().blueprint(t.netIndex());
        if (bp == null) {
            return false;
        }
        if (script.sites().isEmpty()) { // no scripted sites: the player finds its own (AI-02)
            int[] at = SiteFinder.find(view, budget, base, template, wantX, wantZ, memory.get(Slots.LAST_SITE) - 1);
            memory.set(Slots.PLACED, view.tick() + 1);
            if (at == null) {
                return false; // nothing within reach now; the wait, then a look again
            }
            Footprint f = Footprint.of(bp, at[0], at[1], 0);
            List<Integer> builders =
                    nearestWorkers(view, f.anchorX() + f.spanX() / 2.0, f.anchorZ() + f.spanZ() / 2.0, 2);
            if (builders.isEmpty()) {
                return false;
            }
            memory.set(Slots.LAST_SITE, SiteFinder.pack(at[0], at[1]) + 1);
            budget.spend(20);
            sink.send(new PlaceBuilding(
                    template,
                    at[0],
                    at[1],
                    0,
                    UnitSet.of(builders.stream().mapToInt(Integer::intValue).toArray()),
                    QueueMode.REPLACE));
            return true;
        }
        int k = (int) (memory.get(Slots.SITE) % script.sites().size());
        List<Integer> offset = script.sites().get(k);
        Footprint probe = Footprint.of(bp, 0, 0, 0);
        double sx = base.x() + base.mirror() * offset.get(0);
        double sz = base.z() + base.mirror() * offset.get(1);
        int x = (int) Math.floor(sx) - probe.spanX() / 2;
        int z = (int) Math.floor(sz) - probe.spanZ() / 2;
        List<Integer> builders = nearestWorkers(view, sx, sz, 2);
        if (builders.isEmpty()) {
            return false;
        }
        memory.set(Slots.SITE, memory.get(Slots.SITE) + 1);
        memory.set(Slots.PLACED, view.tick() + 1);
        budget.spend(20);
        sink.send(new PlaceBuilding(
                template,
                x,
                z,
                0,
                UnitSet.of(builders.stream().mapToInt(Integer::intValue).toArray()),
                QueueMode.REPLACE));
        return true;
    }

    /**
     * Where a step wants its building (AI-02): by the nearest live node of resource {@code near} to the centre, or by
     * the nearest own building of template {@code near}; by the centre itself otherwise.
     */
    private static double[] want(AiView view, Base base, String near) {
        if (near.isEmpty()) {
            return new double[] {base.x(), base.z()};
        }
        double best = Double.POSITIVE_INFINITY;
        double[] out = {base.x(), base.z()};
        for (NodeView n : view.nodes()) {
            if (n.resource().equals(near) && n.amount() > 0 && n.kind() != MaterialKind.CROP) {
                double d = DetMath.length(n.anchorX() - base.x(), n.anchorZ() - base.z());
                if (d < best) {
                    best = d;
                    out = new double[] {n.anchorX(), n.anchorZ()};
                }
            }
        }
        for (BuildingView b : view.buildings()) {
            if (b.template().value().equals(near)) {
                Footprint f = b.footprint();
                double x = f.anchorX() + f.spanX() / 2.0;
                double z = f.anchorZ() + f.spanZ() / 2.0;
                double d = DetMath.length(x - base.x(), z - base.z());
                if (d < best) {
                    best = d;
                    out = new double[] {x, z};
                }
            }
        }
        return out;
    }

    private static List<Integer> nearestWorkers(AiView view, double x, double z, int n) {
        List<UnitView> workers = new ArrayList<>();
        for (UnitView u : view.units()) {
            if (view.is(u.template(), "Builder") && !view.building(u)) { // a REPLACE would abandon its site
                workers.add(u);
            }
        }
        workers.sort((a, b) -> {
            int c = Double.compare(DetMath.length(a.x() - x, a.z() - z), DetMath.length(b.x() - x, b.z() - z));
            return c != 0 ? c : Integer.compare(a.handle(), b.handle());
        });
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k < n && k < workers.size(); k++) {
            out.add(workers.get(k).handle());
        }
        return out;
    }

    private static int count(AiView view, TemplateId template) {
        int n = 0;
        for (BuildingView b : view.buildings()) {
            if (b.template().equals(template)) {
                n++;
            }
        }
        return n;
    }

    /** Whether one of {@code template} is still being built. */
    private static boolean building(AiView view, TemplateId template) {
        for (BuildingView b : view.buildings()) {
            if (b.template().equals(template) && b.state() != 2) {
                return true;
            }
        }
        return false;
    }
}
