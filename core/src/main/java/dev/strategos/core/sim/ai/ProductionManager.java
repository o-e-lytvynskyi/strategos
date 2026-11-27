package dev.strategos.core.sim.ai;

import dev.strategos.core.content.CivRoles;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.civ.AiHint;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the queues full within the stock (04 §14's ProductionManager): its civic centre trains workers until the
 * script's number, counting those queued; every building that trains its soldiers keeps a few queued, and under AI-02's
 * {@code reserve} only while the stock also covers the building the build order waits on; a field reaped empty is
 * sown again. A temple trains the script's few healers ({@code depth.healers}, AI-09).
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
        TemplateId healer = view.civRole(HealerManager.HEALER); // AI-09: a temple trains a few, none without the role
        int healersWanted = script.depth().healers();
        int healers = 0;
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
            if (u.template().equals(healer)) {
                healers++;
            }
            have[view.role(u)]++;
        }
        int workerCap =
                script.maxWorkers() + script.depth().extraWorkers(view::researched); // AI-09: grows with the phase
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
                if (view.templates().has(t) && view.templates().byIndex(t).id().equals(healer)) {
                    healers++;
                }
                have[view.roleOf(t)]++;
            }
        }
        // a fixed cap is refused with POPULATION_CAP and no house lifts it: at the cap nothing more is queued (CIV-05)
        boolean capped = !view.needs(AiHint.NEEDS_HOUSING) && view.populationUsed() >= view.populationCap();
        BuildManager.Due due = BuildManager.due(view, script, memory);
        // AI-09 (T25b): in the depth path's late game no reserve holds a soldier back below the wave's size
        boolean late = script.depth().late(view.tick());
        int floorSize = late ? script.attack().min() : 0;
        BuildManager.Due phase =
                late ? TechManager.due(view, script, memory) : null; // the saved phase, whatever is due
        phase = TechManager.savingPhase(phase) ? phase : null;
        boolean phaseSaved = phase != null; // a phase's price outranks the ram's
        boolean ramWanted = false; // a ram is wanted and an arsenal stands: soldiers leave its price in the stock
        if (ram != null && battle.rams() > 0 && rams < battle.rams() && !phaseSaved) {
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
                        && (due == null || kept(view, late, ram, due, phase))) {
                    budget.spend(10);
                    rams++;
                    sink.send(new Train(b.handle(), ram));
                }
                continue;
            }
            if (capped) {
                continue; // the cap is refused whatever is queued: back off until a unit dies
            }
            if (healer != null && healers < healersWanted && view.trains(b.template(), healer)) {
                if (b.queue().isEmpty()
                        && view.affords(healer)
                        && (due == null || kept(view, late, healer, due, phase))) {
                    budget.spend(10);
                    healers++;
                    sink.send(new Train(b.handle(), healer)); // a temple's healers, a few, once it stands (AI-09)
                }
                continue;
            }
            if (worker != null
                    && view.trains(b.template(), worker)
                    && b.queue().isEmpty()
                    && workers < workerCap
                    && view.affords(worker)) {
                budget.spend(10);
                workers++;
                sink.send(new Train(b.handle(), worker));
            } else if (b.queue().size() < script.soldierQueue()) {
                TemplateId unit = compose ? Composition.pick(view, script, memory, b.template(), have) : soldier;
                boolean floor = early(floorSize, have);
                // AI-09 (T25b): below half the wave's size the cheapest soldier the stock pays passes any reserve: an
                // army never sinks to nothing while a phase is saved
                boolean critical = late && early(floorSize / 2, have);
                TemplateId cheap = critical ? cheapest(view, b.template(), soldier) : null;
                unit = cheap != null ? cheap : unit;
                if (unit != null
                        && view.trains(b.template(), unit)
                        && view.affords(unit)
                        && (due == null
                                || (cheap != null && unit.equals(cheap))
                                || (late
                                        ? spendable(view, unit, due) // the reserve keeps only what the stock lacks
                                                && (phase == null || spendable(view, unit, phase, !floor))
                                        : affordsBoth(view, unit, due) // AI-02: the build order first
                                                || early(script.early(), have)))
                        && (!ramWanted
                                || floor
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

    /** The cheapest of the foot soldier, the ranged and the cavalry that {@code building} trains and the stock pays; null for none. */
    private static @Nullable TemplateId cheapest(AiView view, TemplateId building, @Nullable TemplateId foot) {
        TemplateId best = null;
        long cheapest = Long.MAX_VALUE;
        for (TemplateId t : new TemplateId[] {foot, view.civRole(CivRoles.RANGED), view.civRole(CivRoles.CAVALRY)}) {
            if (t == null || !view.trains(building, t) || !view.affords(t)) {
                continue;
            }
            long price = 0;
            for (String r : view.resources()) {
                price += view.cost(t, r);
            }
            if (price < cheapest) {
                cheapest = price;
                best = t;
            }
        }
        return best;
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

    /** Whether {@code a} may be spent while {@code due} is kept: the late game's rule, else the whole price kept. */
    private static boolean kept(
            AiView view, boolean late, TemplateId a, BuildManager.Due due, BuildManager.@Nullable Due phase) {
        return late
                ? spendable(view, a, due) && (phase == null || spendable(view, a, phase, true))
                : affordsBoth(view, a, due);
    }

    /** As {@link #spendable(long[], long[], long[])} for {@code a} against what {@code due} keeps. */
    static boolean spendable(AiView view, TemplateId a, BuildManager.Due due) {
        return spendable(view, r -> view.cost(a, r), due);
    }

    /** As above for any price ({@code cost}, per resource, thousandths). */
    static boolean spendable(AiView view, java.util.function.ToLongFunction<String> cost, BuildManager.Due due) {
        return spendable(view, cost, due, false);
    }

    /** As {@link #spendable(AiView, TemplateId, BuildManager.Due)} for the saved phase's price: kept whole when {@code strict}. */
    static boolean spendable(AiView view, TemplateId a, BuildManager.Due due, boolean strict) {
        return spendable(view, r -> view.cost(a, r), due, strict);
    }

    /** As above for any price. {@code strict}: see {@link #keeps(long[], long[], long[])}; else the lacking rule. */
    static boolean spendable(
            AiView view, java.util.function.ToLongFunction<String> cost, BuildManager.Due due, boolean strict) {
        List<String> resources = view.resources();
        long[] stock = new long[resources.size()];
        long[] price = new long[stock.length];
        long[] held = new long[stock.length];
        for (int r = 0; r < stock.length; r++) {
            stock[r] = view.stock(resources.get(r));
            price[r] = cost.applyAsLong(resources.get(r));
            held[r] = due.cost(view, resources.get(r));
        }
        return strict ? keeps(stock, price, held) : spendable(stock, price, held);
    }

    /**
     * The stricter rule above the army's floor: a spend may not take any resource of the reserve's price below its amount
     * ({@code stock - cost >= held} for each resource the spend costs); resources outside the price are free.
     */
    static boolean keeps(long[] stock, long[] cost, long[] held) {
        for (int r = 0; r < stock.length; r++) {
            if (cost[r] > 0 && stock[r] - cost[r] < held[r]) {
                return false;
            }
        }
        return true;
    }

    /**
     * AI-09 (T25b), the depth path's late game: whether {@code cost} may be spent from {@code stock} while a reserve keeps
     * {@code held}. The stock must pay the price; a resource the reserve still lacks (the stock is under {@code held}) is
     * not spent at all, and one it does not lack is free.
     */
    static boolean spendable(long[] stock, long[] cost, long[] held) {
        for (int r = 0; r < stock.length; r++) {
            if (stock[r] < cost[r] || (stock[r] < held[r] && cost[r] > 0)) {
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
