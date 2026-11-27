package dev.strategos.core.sim.ai;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.shared.QueueEntry;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Researches along the tree (AI-09, M10 §2.12): from the script's {@code depth.techs.from} the first technology of its
 * priority list that the rules would take now (its building stands and holds it in its effective list, the roster has
 * it, its requirements are met, it is not moot or its pair's) is researched once the stock pays for it, one at a time.
 * With the script's {@code reserve} on, the stock is kept for that first technology for {@value #RESERVE_TICKS} ticks
 * ({@link BuildManager#due}), as for a build order's step; after that a cheaper one behind it may go first. The build
 * order's own due step comes before it.
 */
final class TechManager implements AiModule {
    /** How long the stock is kept for the list's first technology: five minutes, as a research step's. */
    static final long RESERVE_TICKS = BuildManager.RESEARCH_RESERVE_TICKS;

    private static long hold(StrategyScript script, TechDef tech) {
        Depth d = script.depth();
        if (d.phaseHold() > 0 && tech.classes().contains(TechDef.PHASE_CLASS)) {
            return d.phaseHold();
        }
        return d.techHold() > 0 ? d.techHold() : RESERVE_TICKS;
    }

    private final StrategyScript script;
    private final AiMemory memory;

    TechManager(StrategyScript script, AiMemory memory) {
        this.script = script;
        this.memory = memory;
    }

    /** A technology the rules would take, and the building that researches it. */
    private record Option(TechDef tech, BuildingView building) {}

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        Depth.Techs techs = script.depth().techs();
        if (techs.order().isEmpty() || view.tick() < 20L * techs.from() || budget.exhausted()) {
            return;
        }
        if (researching(view)) {
            memory.set(Slots.TECH_DUE, 0); // one at a time: nothing is waited for while a technology is being made
            return;
        }
        List<Option> options = options(view, budget, techs);
        TechDef saved = pendingPhase(view, techs, options);
        if (options.isEmpty() && saved == null) {
            memory.set(Slots.TECH_DUE, 0);
            return;
        }
        TechDef head = saved != null ? saved : options.get(0).tech();
        long due = memory.get(Slots.TECH_DUE);
        if (due == 0 || (due & 0xFFFF) != head.netIndex() + 1L) {
            due = (view.tick() + 1) << 16 | (head.netIndex() + 1L);
        }
        boolean keeping = view.tick() - ((due >> 16) - 1) <= hold(script, head);
        BuildManager.Due step = BuildManager.stepDue(view, script, memory);
        for (Option o : options) {
            if (!o.tech().equals(head) && keeping) {
                break; // the stock is kept for the first
            }
            if (view.affords(o.tech())
                    && (step == null
                            || (script.depth().late(view.tick())
                                    ? ProductionManager.spendable(view, r -> view.cost(o.tech(), r), step)
                                    : affordsBoth(view, o.tech(), step)))
                    && (saved == null
                            || !script.depth().late(view.tick())
                            || ProductionManager.spendable(
                                    view,
                                    r -> view.cost(o.tech(), r),
                                    new BuildManager.Due(StrategyScript.Step.NONE, saved, true),
                                    true))
                    && budget.spend(10)) {
                memory.set(Slots.TECH_DUE, 0);
                sink.send(new Research(o.building().handle(), o.tech().id()));
                return;
            }
        }
        memory.set(Slots.TECH_DUE, due);
    }

    /**
     * The phase the priority list saves for before its requirements stand: the first technology of {@code order} not yet
     * had or queued, when it is a phase that an own finished building holds in its effective list and would take but for its
     * requirements (its buildings; the roster is read the same way), and it stands before the first technology that is taken now.
     * Null for none.
     */
    private static @Nullable TechDef pendingPhase(AiView view, Depth.Techs techs, List<Option> options) {
        for (String id : techs.order()) {
            TechDef t = view.templates().tech(new TechId(id));
            if (t == null || view.hasOrQueues(t)) {
                continue;
            }
            if (!t.isPhase()) {
                return null; // the first not yet had is no phase to save for
            }
            for (Option o : options) {
                if (o.tech().equals(t)) {
                    return null;
                }
            }
            for (BuildingView b : view.buildings()) {
                if (view.blockedByRequirements(b, t)) {
                    return t; // an own building would research it but for its requirements (its buildings)
                }
            }
            return null; // not in the roster, or no building holds it yet: nothing to save for
        }
        return null;
    }

    /** Whether {@code due} is a phase the priority list saves for. */
    static boolean savingPhase(BuildManager.@Nullable Due due) {
        return due != null && due.tree() && due.tech() != null && due.tech().isPhase();
    }

    /** What the stock is kept for, once the build order's step is not due (see {@link BuildManager#due}); null for none. */
    static BuildManager.@Nullable Due due(AiView view, StrategyScript script, AiMemory memory) {
        long due = memory.get(Slots.TECH_DUE);
        if (!script.reserve() || due == 0) {
            return null;
        }
        TechDef tech = view.templates().techByIndex((int) (due & 0xFFFF) - 1);
        if (tech != null && view.tick() - ((due >> 16) - 1) > hold(script, tech)) {
            return null;
        }
        return tech == null ? null : new BuildManager.Due(StrategyScript.Step.NONE, tech, true);
    }

    /** Whether a technology is in any own queue. */
    private static boolean researching(AiView view) {
        for (BuildingView b : view.buildings()) {
            for (int entry : b.queue()) {
                if (QueueEntry.isTech(entry)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The list's technologies the rules would take now, in the list's order, each with its first building. */
    private static List<Option> options(AiView view, AiBudget budget, Depth.Techs techs) {
        List<Option> out = new ArrayList<>();
        List<BuildingView> own = new ArrayList<>();
        for (BuildingView b : view.buildings()) {
            if (b.state() == BuildingStore.COMPLETE && b.queue().size() < BuildingStore.QUEUE_MAX) {
                own.add(b);
            }
        }
        for (String id : techs.order()) {
            TechDef tech = view.templates().tech(new TechId(id));
            if (tech == null || !budget.spend(1)) {
                continue;
            }
            for (BuildingView b : own) {
                if (view.canResearch(b, tech)) {
                    out.add(new Option(tech, b));
                    break;
                }
            }
        }
        return out;
    }

    /** Whether the stock pays {@code tech} and {@code due} together, in every resource. */
    private static boolean affordsBoth(AiView view, TechDef tech, BuildManager.Due due) {
        for (String r : view.resources()) {
            if (view.stock(r) < view.cost(tech, r) + due.cost(view, r)) {
                return false;
            }
        }
        return true;
    }
}
