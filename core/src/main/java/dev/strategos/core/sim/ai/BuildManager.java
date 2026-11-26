package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.CivRoles;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Build;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.civ.AiHint;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Builds the script's build order and houses (04 §14's BuildManager). A step starts once the population reaches its
 * number; its building is placed with the two workers nearest the site, at the next of the script's sites round the
 * civic centre (mirrored for the second start slot); a placement the server refused leaves no new building, so after
 * {@value #RETRY} ticks the next site is tried. The step is done once one more of its building stands, in any state.
 * A research step (AI-05) sends {@code Research} from an own building that holds the tech once the population is reached
 * and the stock covers its cost, and is done once the tech is researched or queued. Before that, a house goes up whenever the free population falls to the margin and none is being built: no room to
 * grow comes first. An own building left unfinished with nobody on it gets the nearest free worker back (M4 T2). Then,
 * before the build order, the script's {@code dropsite} goes up by a resource running out near its dropsites (AI-02).
 */
final class BuildManager implements AiModule {
    /** Ticks to wait before trying another site. */
    static final long RETRY = 100;

    /** How far from an own dropsite a node is near it. */
    static final double FAR = 24;

    /**
     * How far from the centre a dropsite may go up by a far grove (QA-12); its stone is Town's too (50 of it each).
     */
    static final double REMOTE_REACH = 120;

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
        TemplateId house = view.civRole(CivRoles.HOUSE); // a civilisation with no house role builds none (CIV-05)
        if (waited
                && house != null
                && view.needs(AiHint.NEEDS_HOUSING) // a fixed cap needs no house and no margin of free population
                && view.populationCap() - view.populationUsed() <= script.housingMargin()
                && !building(view, house)
                && view.affords(house)) {
            place(view, budget, sink, base, house, ""); // no room to grow comes first
            return;
        }
        if (waited
                && !script.dropsite().isEmpty()
                && view.needs(AiHint.NEEDS_DROPSITES)
                && expand(view, budget, sink, base)) {
            return; // a dropsite by a far node before the build order: placed now, or the stock kept for it
        }
        int step = (int) memory.get(Slots.STEP);
        if (step < script.build().size()) {
            StrategyScript.Step s = script.build().get(step);
            if (s.researches()) {
                research(view, budget, sink, s, step, waited);
                return;
            }
            if (script.depth().late(tick) && !permitted(view, s.building())) {
                // AI-09 (T25b): the saved phase keeps what this step costs: the order moves on (its Town buildings are
                // what the phase asks for); the step is taken up again at the order's end ({@link #catchUp})
                skip(step, count(view, s.building()));
                base(0);
                memory.set(Slots.STEP, step + 1L);
                memory.set(Slots.DUE_SINCE, 0);
                return;
            }
            if (superfluous(view, s.building())) {
                memory.set(Slots.STEP, step + 1L); // a building the rules make useless is not worth its price: skipped
                base(0);
                memory.set(Slots.DUE_SINCE, 0);
                return;
            }
            int count = count(view, s.building());
            if (base() == 0) {
                base(count + 1);
            }
            if (count > base() - 1) {
                memory.set(Slots.STEP, step + 1L);
                base(0);
                memory.set(Slots.DUE_SINCE, 0);
            } else if (script.reserve()
                    && view.populationUsed() >= s.population()
                    && memory.get(Slots.DUE_SINCE) == 0
                    && permitted(view, s.building())) {
                memory.set(Slots.DUE_SINCE, tick + 1); // due: the stock is kept for it a while (AI-02)
            }
            if (count <= base() - 1
                    && waited
                    && view.populationUsed() >= s.population()
                    && view.affords(s.building())
                    && permitted(view, s.building())) {
                place(view, budget, sink, base, s.building(), s.near());
            }
        } else if (waited) {
            if (script.depth().late(tick) && catchUp(view, budget, sink, base)) {
                return;
            }
            expandCentre(view, budget, sink, base);
        }
    }

    /** The steps whose skips {@link #skip} can record: a nibble each in {@link Slots#SKIPPED} (a longer order is refused at parse). */
    static final int SKIPPABLE = 16;

    /** The skip record: the step's building count, plus one, when each skipped step was passed over (a nibble per step). */
    private long skips() {
        return memory.get(Slots.SKIPPED);
    }

    /** The current step's base: one more than its building's count when the step began. */
    private int base() {
        return (int) memory.get(Slots.STEP_BASE);
    }

    private void base(int value) {
        memory.set(Slots.STEP_BASE, value);
    }

    /**
     * AI-09 (T25b): notes that step {@code step}, passed over while a phase is saved, began with {@code count} of its
     * building standing (the start kit's included, as the order counts: a step is done when more stand).
     */
    private void skip(int step, int count) {
        long nibble = Math.min(count, 14) + 1L;
        memory.set(Slots.SKIPPED, (skips() & ~(0xFL << (4 * step))) | (nibble << (4 * step)));
    }

    /**
     * AI-09 (T25b): at the order's end, the first step passed over while a phase was saved whose building has not been
     * raised since (more stand than when it was passed over) that may be placed now; whether a placement went out. A
     * step done is forgotten, so a building lost later is the rebuilder's.
     */
    private boolean catchUp(AiView view, AiBudget budget, CommandSink sink, Base base) {
        for (int i = 0; i < SKIPPABLE && i < script.build().size(); i++) {
            long nibble = (skips() >>> (4 * i)) & 0xF;
            StrategyScript.Step s = script.build().get(i);
            if (nibble == 0 || s.researches()) {
                continue;
            }
            int count = count(view, s.building());
            if (count > nibble - 1) {
                memory.set(Slots.SKIPPED, skips() & ~(0xFL << (4 * i)));
            } else if (!superfluous(view, s.building())
                    && !building(view, s.building())
                    && view.populationUsed() >= s.population()
                    && view.affords(s.building())
                    && permitted(view, s.building())) {
                place(view, budget, sink, base, s.building(), s.near());
                return true;
            }
        }
        return false;
    }

    /**
     * AI-09 (T25b), the depth path's late game: whether {@code building} may be placed while a phase is saved for. It may
     * not take a resource the phase still lacks, unless it is a Town-class building (what City asks for); outside the
     * late game, or with no phase saved, it always may.
     */
    private boolean permitted(AiView view, TemplateId building) {
        return permitted(view, building, null);
    }

    /** As above; a dropsite by a node of {@code resource} is also free when the phase lacks that resource (it unlocks it). */
    private boolean permitted(AiView view, TemplateId building, @Nullable String resource) {
        if (!script.depth().late(view.tick()) || view.is(building, "Town")) {
            return true;
        }
        Due saved = TechManager.due(view, script, memory);
        return saved == null
                || !TechManager.savingPhase(saved)
                || (resource != null && view.stock(resource) < saved.cost(view, resource))
                || ProductionManager.spendable(view, building, saved, true);
    }

    /**
     * Whether the rules its faction plays make {@code building} useless to it: a house under a fixed population, a
     * dropsite under direct delivery (the farmstead stays: it reseeds the fields).
     */
    private static boolean superfluous(AiView view, TemplateId building) {
        if (!view.needs(AiHint.NEEDS_HOUSING) && building.equals(view.civRole(CivRoles.HOUSE))) {
            return true;
        }
        return !view.needs(AiHint.NEEDS_DROPSITES) && view.isDropsite(building) && !view.is(building, "Farmstead");
    }

    /**
     * AI-08: once the build order is done and Town has raised the civic centre limit above the centres it has, with the
     * stock twice the price of one (so soldiers are not starved), a second civic centre goes up on the neutral land
     * nearest the base ({@link SiteFinder#neutral}). One at a time, none while one stands unfinished.
     */
    private void expandCentre(AiView view, AiBudget budget, CommandSink sink, Base base) {
        TemplateId centre = view.civRole(CivRoles.CENTRE);
        if (centre == null
                || count(view, centre) >= view.buildingLimit(centre)
                || building(view, centre)
                || !permitted(view, centre)) {
            return;
        }
        for (String r : view.resources()) {
            if (view.stock(r) < 2 * view.cost(centre, r)) {
                return;
            }
        }
        int[] at = SiteFinder.neutral(view, budget, base, centre);
        memory.set(Slots.PLACED, view.tick() + 1);
        UnitTemplate t = view.templates().byId(centre);
        Blueprint bp = t == null ? null : view.templates().blueprint(t.netIndex());
        if (at == null || bp == null) {
            return;
        }
        Footprint f = Footprint.of(bp, at[0], at[1], 0);
        UnitSet crew = crew(view, f.anchorX() + f.spanX() / 2.0, f.anchorZ() + f.spanZ() / 2.0, 2);
        if (crew == null) {
            return;
        }
        budget.spend(20);
        sink.send(new PlaceBuilding(centre, at[0], at[1], 0, crew, QueueMode.REPLACE));
    }

    /** How long the stock is kept for a due building before soldiers may have it again: a minute (AI-02). */
    static final long RESERVE_TICKS = 1_200;

    /** Whether a research step (Town) comes before step {@code step} of the build order: its building is a later one. */
    private static boolean researchBefore(StrategyScript script, int step) {
        for (int k = 0; k < step; k++) {
            if (script.build().get(k).researches()) {
                return true;
            }
        }
        return false;
    }

    /** As {@link #RESERVE_TICKS} for a due tech, whose cost is several buildings' (AI-05): five minutes. */
    static final long RESEARCH_RESERVE_TICKS = 6_000;

    /** What the stock is kept for: a building, or a tech; {@code tree}: one the priority list saves for (AI-09). */
    record Due(TemplateId building, @Nullable TechDef tech, boolean tree) {
        Due(TemplateId building, @Nullable TechDef tech) {
            this(building, tech, false);
        }

        /** Its cost in {@code resource}, thousandths. */
        long cost(AiView view, String resource) {
            return tech == null ? view.cost(building, resource) : view.cost(tech, resource);
        }
    }

    /**
     * What the stock is kept for (AI-02's {@code reserve}; null without it or with nothing due): a dropsite by a far
     * node the stock did not cover, until it is placed, or else the build order's next step ({@link #stepDue}), or else
     * the technology the priority list saves for ({@link TechManager#due}).
     */
    static @Nullable Due due(AiView view, StrategyScript script, AiMemory memory) {
        if (script.reserve() && memory.get(Slots.EXPAND_SINCE) != 0) {
            return new Due(
                    new TemplateId(script.dropsite()), null); // with its resource running out, nothing else pays more
        }
        Due step = stepDue(view, script, memory);
        return step != null ? step : TechManager.due(view, script, memory); // AI-09: then the tree's first
    }

    /**
     * The build order's part of {@link #due}: its next step, due and not yet done, a building for {@value
     * #RESERVE_TICKS} ticks, a research for {@value #RESEARCH_RESERVE_TICKS}.
     */
    static @Nullable Due stepDue(AiView view, StrategyScript script, AiMemory memory) {
        int step = (int) memory.get(Slots.STEP);
        long since = memory.get(Slots.DUE_SINCE);
        if (!script.reserve() || step >= script.build().size() || since == 0) {
            return null;
        }
        StrategyScript.Step s = script.build().get(step);
        if (s.researches()) {
            TechDef tech = view.templates().tech(new TechId(s.research()));
            return tech == null || view.tick() - (since - 1) > RESEARCH_RESERVE_TICKS
                    ? null
                    : new Due(s.building(), tech);
        }
        boolean late = view.tick() >= 20L * script.depth().techs().from(); // the late game's holds come with its minute
        long hold = late && script.depth().hold() > 0 && researchBefore(script, step)
                ? script.depth().hold()
                : RESERVE_TICKS;
        return view.tick() - (since - 1) > hold ? null : new Due(s.building(), null);
    }

    /**
     * A research step (AI-05): done once the tech is researched or queued (or the set lacks it); due once the population
     * is reached, when {@code Research} goes out from a finished own building that holds it, as soon as the stock covers
     * the cost, again after {@value #RETRY} ticks if the server refused it (a requirement not yet met).
     */
    private void research(
            AiView view, AiBudget budget, CommandSink sink, StrategyScript.Step s, int step, boolean waited) {
        TechDef tech = view.templates().tech(new TechId(s.research()));
        if (tech == null || view.hasOrQueues(tech)) {
            memory.set(Slots.STEP, step + 1L);
            base(0);
            memory.set(Slots.DUE_SINCE, 0);
            return;
        }
        if (view.populationUsed() < s.population()) {
            return;
        }
        if (memory.get(Slots.DUE_SINCE) == 0) {
            memory.set(Slots.DUE_SINCE, view.tick() + 1);
        }
        if (!waited || !view.affords(tech)) {
            return;
        }
        for (BuildingView b : view.buildings()) {
            if (b.state() == BuildingStore.COMPLETE
                    && b.queue().size() < BuildingStore.QUEUE_MAX
                    && view.researches(b.template(), tech)
                    && budget.spend(10)) {
                memory.set(Slots.PLACED, view.tick() + 1);
                sink.send(new Research(b.handle(), tech.id()));
                return;
            }
        }
    }

    /** Every own unfinished building with no worker on its site gets the nearest one not building elsewhere. */
    private static void staff(AiView view, AiBudget budget, CommandSink sink) {
        if (!view.needs(AiHint.NEEDS_BUILDERS)) {
            return; // a foundation builds itself: nobody is sent to it
        }
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
            boolean town = townQueued();
            // before Town: one storehouse beyond the usual reach at most, and never into Town's price (QA-12)
            boolean remote = town || remoteDropsites(view, base, template) == 0;
            NodeView far = takes.contains(resource) ? farNode(view, base, resource, remote) : null;
            if (far == null || (!town && !affordsWithTown(view, template)) || !permitted(view, template, resource)) {
                continue;
            }
            if (!view.affords(template)) {
                if (memory.get(Slots.EXPAND_SINCE) == 0) {
                    memory.set(Slots.EXPAND_SINCE, view.tick() + 1);
                }
                return true;
            }
            memory.set(Slots.EXPAND_SINCE, 0);
            return placeAt(view, budget, sink, base, template, far.anchorX(), far.anchorZ(), true);
        }
        memory.set(Slots.EXPAND_SINCE, 0);
        return false;
    }

    /** How many own buildings of {@code template} stand beyond the usual reach of the centre. */
    private static int remoteDropsites(AiView view, Base base, TemplateId template) {
        int n = 0;
        for (BuildingView b : view.buildings()) {
            Footprint f = b.footprint();
            if (b.template().equals(template)
                    && DetMath.length(f.anchorX() - base.x(), f.anchorZ() - base.z()) > SiteFinder.REACH) {
                n++;
            }
        }
        return n;
    }

    /** Whether the stock covers {@code template} and the build order's research step (Town) together. */
    private boolean affordsWithTown(AiView view, TemplateId template) {
        for (StrategyScript.Step s : script.build()) {
            if (s.researches()) {
                TechDef tech = view.templates().tech(new TechId(s.research()));
                if (tech == null) {
                    return true;
                }
                for (String r : view.resources()) {
                    if (view.stock(r) < view.cost(template, r) + view.cost(tech, r)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return true;
    }

    /** Whether the build order's research step (Town) is done: its stone is spent, and remote storehouses may go up (QA-12). */
    private boolean townQueued() {
        for (int k = 0; k < script.build().size(); k++) {
            if (script.build().get(k).researches()) {
                return memory.get(Slots.STEP) > k;
            }
        }
        return true;
    }

    /**
     * With {@code resource} running out near its own dropsites, its live node nearest the centre among those farther
     * from them and within {@link SiteFinder#REACH} of the centre, where a site may go; null otherwise.
     */
    private static @org.jspecify.annotations.Nullable NodeView farNode(
            AiView view, Base base, String resource, boolean remote) {
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
            if (d < bestD
                    && d <= (remote ? REMOTE_REACH : SiteFinder.REACH)
                    && view.explored(n.anchorX(), n.anchorZ())) {
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
        placeAt(view, budget, sink, base, template, want[0], want[1], false);
    }

    /** Places {@code template} by (wantX, wantZ); whether a placement went out. */
    private boolean placeAt(
            AiView view,
            AiBudget budget,
            CommandSink sink,
            Base base,
            TemplateId template,
            double wantX,
            double wantZ,
            boolean remote) {
        UnitTemplate t = view.templates().byId(template);
        Blueprint bp = t == null ? null : view.templates().blueprint(t.netIndex());
        if (bp == null) {
            return false;
        }
        if (script.sites().isEmpty()) { // no scripted sites: the player finds its own (AI-02)
            int[] at = SiteFinder.find(
                    view, budget, base, template, wantX, wantZ, memory.get(Slots.LAST_SITE) - 1, remote);
            memory.set(Slots.PLACED, view.tick() + 1);
            if (at == null) {
                return false; // nothing within reach now; the wait, then a look again
            }
            Footprint f = Footprint.of(bp, at[0], at[1], 0);
            UnitSet crew = crew(view, f.anchorX() + f.spanX() / 2.0, f.anchorZ() + f.spanZ() / 2.0, 2);
            if (crew == null) {
                return false;
            }
            memory.set(Slots.LAST_SITE, SiteFinder.pack(at[0], at[1]) + 1);
            budget.spend(20);
            sink.send(new PlaceBuilding(template, at[0], at[1], 0, crew, QueueMode.REPLACE));
            return true;
        }
        int k = (int) (memory.get(Slots.SITE) % script.sites().size());
        List<Integer> offset = script.sites().get(k);
        Footprint probe = Footprint.of(bp, 0, 0, 0);
        double sx = base.x() + base.mirror() * offset.get(0);
        double sz = base.z() + base.mirror() * offset.get(1);
        int x = (int) Math.floor(sx) - probe.spanX() / 2;
        int z = (int) Math.floor(sz) - probe.spanZ() / 2;
        UnitSet crew = crew(view, sx, sz, 2);
        if (crew == null) {
            return false;
        }
        memory.set(Slots.SITE, memory.get(Slots.SITE) + 1);
        memory.set(Slots.PLACED, view.tick() + 1);
        if ("TERRITORY".equals(view.placementRefusal(template, x, z))) {
            return false; // AI-08: a scripted site off its land is skipped, not sent to be refused
        }
        budget.spend(20);
        sink.send(new PlaceBuilding(template, x, z, 0, crew, QueueMode.REPLACE));
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

    /**
     * The builders a placement at ({@code x}, {@code z}) sends: the {@code n} nearest workers, none when the rules need
     * none (the foundation is placed and left); null when builders are needed and no worker is free.
     */
    static @org.jspecify.annotations.Nullable UnitSet crew(AiView view, double x, double z, int n) {
        if (!view.needs(AiHint.NEEDS_BUILDERS)) {
            return UnitSet.of();
        }
        List<Integer> builders = nearestWorkers(view, x, z, n);
        return builders.isEmpty()
                ? null
                : UnitSet.of(builders.stream().mapToInt(Integer::intValue).toArray());
    }

    static List<Integer> nearestWorkers(AiView view, double x, double z, int n) {
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
