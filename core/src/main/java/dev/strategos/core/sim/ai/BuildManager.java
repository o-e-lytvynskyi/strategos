package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the script's build order and houses (04 §14's BuildManager). A step starts once the population reaches its
 * number; its building is placed with the two workers nearest the site, at the next of the script's sites round the
 * civic centre (mirrored for the second start slot); a placement the server refused leaves no new building, so after
 * {@value #RETRY} ticks the next site is tried. The step is done once one more of its building stands, in any state.
 * Before that, a house goes up whenever the free population falls to the margin and none is being built: no room to
 * grow comes first.
 */
final class BuildManager implements AiModule {
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    /** Ticks to wait before trying another site. */
    static final long RETRY = 100;

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
        long tick = view.tick();
        boolean waited = memory.get(Slots.PLACED) == 0 || tick - (memory.get(Slots.PLACED) - 1) >= RETRY;
        if (waited
                && view.populationCap() - view.populationUsed() <= script.housingMargin()
                && !building(view, HOUSE)
                && view.affords(HOUSE)) {
            place(view, budget, sink, base, HOUSE); // no room to grow comes first
            return;
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
            } else if (waited && view.populationUsed() >= s.population() && view.affords(s.building())) {
                place(view, budget, sink, base, s.building());
            }
        }
    }

    private void place(AiView view, AiBudget budget, CommandSink sink, Base base, TemplateId template) {
        UnitTemplate t = view.templates().byId(template);
        Blueprint bp = t == null ? null : view.templates().blueprint(t.netIndex());
        if (bp == null || script.sites().isEmpty()) {
            return;
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
            return;
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
    }

    private static List<Integer> nearestWorkers(AiView view, double x, double z, int n) {
        List<UnitView> workers = new ArrayList<>();
        for (UnitView u : view.units()) {
            if (view.is(u.template(), "Builder")) {
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
