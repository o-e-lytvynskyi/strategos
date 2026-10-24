package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;

/**
 * Keeps the queues full within the stock (04 §14's ProductionManager): its civic centre trains workers until the
 * script's number, counting those queued; every building that trains its soldiers keeps a few queued, always; a field
 * reaped empty is sown again.
 */
final class ProductionManager implements AiModule {
    /** What a field's reseed costs in M3, in thousandths; the server's refusal stands if it costs more. */
    static final long RESEED_WOOD = 40_000;

    private final StrategyScript script;

    ProductionManager(StrategyScript script) {
        this.script = script;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        TemplateId worker = new TemplateId(script.worker());
        TemplateId soldier = new TemplateId(script.soldier());
        int workers = 0;
        for (UnitView u : view.units()) {
            if (u.template().equals(worker)) {
                workers++;
            }
        }
        for (BuildingView b : view.buildings()) {
            if (b.state() != 2) {
                continue;
            }
            for (int t : b.queue()) {
                if (view.templates().has(t) && view.templates().byIndex(t).id().equals(worker)) {
                    workers++;
                }
            }
        }
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
            } else if (view.trains(b.template(), soldier)
                    && b.queue().size() < script.soldierQueue()
                    && view.affords(soldier)) {
                budget.spend(10);
                sink.send(new Train(b.handle(), soldier));
            }
        }
    }
}
