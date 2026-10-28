package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;

/**
 * Keeps the queues full within the stock (04 §14's ProductionManager): its civic centre trains workers until the
 * script's number, counting those queued; every building that trains its soldiers keeps a few queued, and under AI-02's
 * {@code reserve} only while the stock also covers the building the build order waits on; a field reaped empty is
 * sown again.
 */
final class ProductionManager implements AiModule {
    /** What a field's reseed costs in M3, in thousandths; the server's refusal stands if it costs more. */
    static final long RESEED_WOOD = 40_000;

    private final StrategyScript script;
    private final AiMemory memory;

    ProductionManager(StrategyScript script, AiMemory memory) {
        this.script = script;
        this.memory = memory;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        TemplateId worker = new TemplateId(script.worker());
        TemplateId soldier = new TemplateId(script.soldier());
        StrategyScript.Battle battle = script.battle();
        boolean compose = battle.replan() > 0;
        int[] have = new int[4]; // own soldiers by role, queued ones too (AI-03)
        int workers = 0;
        for (UnitView u : view.units()) {
            if (u.template().equals(worker)) {
                workers++;
            }
            have[view.role(u)]++;
        }
        if (compose) {
            Composition.replan(view, battle, memory);
        }
        for (BuildingView b : view.buildings()) {
            if (b.state() != 2) {
                continue;
            }
            for (int t : b.queue()) {
                if (view.templates().has(t) && view.templates().byIndex(t).id().equals(worker)) {
                    workers++;
                }
                have[view.roleOf(t)]++;
            }
        }
        TemplateId due = BuildManager.due(view, script, memory);
        for (BuildingView b : view.buildings()) {
            if (budget.exhausted()) {
                return;
            }
            if (b.state() != 2) {
                continue;
            }
            if (b.fieldAmount() == 0 && b.fieldRipening() == 0 && view.stock("wood") >= RESEED_WOOD) {
                budget.spend(10);
                sink.send(new Reseed(b.handle())); // a field reaped empty is sown again (ECO-09, manual in M3)
                continue;
            }
            if (view.trains(b.template(), worker)
                    && b.queue().isEmpty()
                    && workers < script.maxWorkers()
                    && view.affords(worker)) {
                budget.spend(10);
                workers++;
                sink.send(new Train(b.handle(), worker));
            } else if (b.queue().size() < script.soldierQueue()) {
                TemplateId unit = compose ? Composition.pick(view, script, memory, b.template(), have) : soldier;
                if (unit != null
                        && view.trains(b.template(), unit)
                        && view.affords(unit)
                        && (due == null || affordsBoth(view, unit, due))) { // AI-02: the build order first
                    budget.spend(10);
                    have[
                            view.roleOf(java.util.Objects.requireNonNull(
                                            view.templates().byId(unit))
                                    .netIndex())]++;
                    sink.send(new Train(b.handle(), unit));
                }
            }
        }
    }

    /** Whether the stock covers {@code a}'s cost and {@code b}'s together. */
    private static boolean affordsBoth(AiView view, TemplateId a, TemplateId b) {
        for (String r : view.resources()) {
            if (view.stock(r) < view.cost(a, r) + view.cost(b, r)) {
                return false;
            }
        }
        return true;
    }
}
