package dev.strategos.core.sim.ai;

import dev.strategos.core.content.CivRoles;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.civ.AiHint;
import java.util.List;

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
        TemplateId worker = script.worker().isEmpty() ? view.civRole(CivRoles.WORKER) : new TemplateId(script.worker());
        TemplateId soldier =
                script.soldier().isEmpty() ? view.civRole(CivRoles.INFANTRY) : new TemplateId(script.soldier());
        TemplateId ram = view.civRole(CivRoles.SIEGE); // the siege unit (QA-12); none: no rams are wanted
        StrategyScript.Battle battle = script.battle();
        boolean compose = battle.replan() > 0;
        int[] have = new int[4]; // own soldiers by role, queued ones too (AI-03)
        int workers = 0;
        int rams = 0;
        for (UnitView u : view.units()) {
            if (u.template().equals(worker)) {
                workers++;
            }
            if (u.template().equals(ram)) {
                rams++;
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
                if (view.templates().has(t) && view.templates().byIndex(t).id().equals(ram)) {
                    rams++;
                }
                have[view.roleOf(t)]++;
            }
        }
        // a fixed cap is refused with POPULATION_CAP and no house lifts it: at the cap nothing more is queued (CIV-05)
        boolean capped = !view.needs(AiHint.NEEDS_HOUSING) && view.populationUsed() >= view.populationCap();
        BuildManager.Due due = BuildManager.due(view, script, memory);
        boolean ramWanted = false; // a ram is wanted and an arsenal stands: soldiers leave its price in the stock
        if (ram != null && battle.rams() > 0 && rams < battle.rams()) {
            for (BuildingView b : view.buildings()) {
                ramWanted |= b.state() == 2 && view.trains(b.template(), ram);
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
            if (ram != null
                    && battle.rams() > 0
                    && view.trains(b.template(), ram)) { // an arsenal trains rams, one at a time
                if (!capped
                        && b.queue().isEmpty()
                        && rams < battle.rams()
                        && view.affords(ram)
                        && (due == null || affordsBoth(view, ram, due))) {
                    budget.spend(10);
                    rams++;
                    sink.send(new Train(b.handle(), ram));
                }
                continue;
            }
            if (capped) {
                continue; // the cap is refused whatever is queued: back off until a unit dies
            }
            if (worker != null
                    && view.trains(b.template(), worker)
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
                        && (due == null
                                || affordsBoth(view, unit, due) // AI-02: the build order first
                                || early(script.early(), have))
                        && (!ramWanted
                                || ram == null
                                || affordsBoth(view, unit, ram))) { // QA-12: the rams' price is kept
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

    /** QA-12: whether fewer than the script's {@code early} soldiers stand or queue: they pass the reserve. */
    private static boolean early(int guard, int[] have) {
        return early(guard, have[AiView.ROLE_SPEAR] + have[AiView.ROLE_ARCHER] + have[AiView.ROLE_HORSE]);
    }

    /** The guard's rule: fewer than {@code guard} soldiers, standing or queued; none when it is 0. */
    static boolean early(int guard, int soldiers) {
        return soldiers < guard;
    }

    /** Whether the stock covers {@code a}'s cost and what is due's together. */
    private static boolean affordsBoth(AiView view, TemplateId a, TemplateId b) {
        for (String r : view.resources()) {
            if (view.stock(r) < view.cost(a, r) + view.cost(b, r)) {
                return false;
            }
        }
        return true;
    }

    private static boolean affordsBoth(AiView view, TemplateId a, BuildManager.Due due) {
        List<String> resources = view.resources();
        long[] stock = new long[resources.size()];
        long[] first = new long[stock.length];
        long[] second = new long[stock.length];
        for (int r = 0; r < stock.length; r++) {
            stock[r] = view.stock(resources.get(r));
            first[r] = view.cost(a, resources.get(r));
            second[r] = due.cost(view, resources.get(r));
        }
        return covers(stock, first, second);
    }

    /** Whether {@code stock} pays {@code first} and {@code second} together, in every resource (thousandths). */
    static boolean covers(long[] stock, long[] first, long[] second) {
        for (int r = 0; r < stock.length; r++) {
            if (stock[r] < first[r] + second[r]) {
                return false;
            }
        }
        return true;
    }
}
