package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.QueueEntry;
import dev.strategos.core.sim.prod.Techs;
import org.jspecify.annotations.Nullable;

/** INGEST's template swap (DATA-03) and its guard: every template the live state refers to must survive it unchanged. */
final class TemplateSwap {
    private TemplateSwap() {}

    /**
     * The stats and the spatial index take the new set's values; refused, keeping the set, when a live unit's template
     * is missing from it or holds another net index there.
     */
    static void apply(MatchState state, TemplateSet set) {
        String problem = problem(state, set);
        if (problem != null) {
            state.log().warn(state.tick(), "template set {} refused: {}", set, problem);
            return;
        }
        TemplateSet old = state.templates();
        state.setTemplates(set);
        state.mobilityCache().rebuild();
        state.stats().swap(set);
        state.progress().swap(set);
        Techs.reapply(state, old); // the researched techs' effects, from the new set (spec row 22)
        Techs.dropRetired(state, old);
        state.costs().swap(state.stats());
        state.gatherRates().swap(state.stats());
        state.spatial().swap(set);
    }

    /** Why {@code next} cannot replace the match's set, or null when it can. */
    static @Nullable String problem(MatchState state, TemplateSet next) {
        EntityStore entities = state.entities();
        for (int i = 0; i < entities.highWater(); i++) {
            if (entities.aliveAt(i)) {
                String problem = referenceProblem(state, next, state.identity().templateIndex(i));
                if (problem != null) {
                    return problem;
                }
            }
        }
        var buildings = state.buildings();
        for (int handle : buildings.handles()) {
            String problem = referenceProblem(state, next, buildings.template(handle));
            if (problem != null) {
                return problem;
            }
            for (int queued : buildings.queue(handle)) {
                problem = referenceProblem(state, next, queued);
                if (problem != null) {
                    return problem;
                }
            }
        }
        var nodes = state.nodes();
        for (int id = 1; id <= nodes.count(); id++) {
            int template = nodes.template(id);
            if (template >= 0) {
                String problem = referenceProblem(state, next, template);
                if (problem != null) {
                    return problem;
                }
            }
        }
        return null;
    }

    private static @Nullable String referenceProblem(MatchState state, TemplateSet next, int index) {
        if (QueueEntry.isTech(index)) {
            // Queued research (-1 - tech net index) does not refuse a swap; Techs.dropRetired drops what the set lacks.
            return null;
        }
        UnitTemplate now = state.templates().byIndex(index);
        UnitTemplate replacement = next.byId(now.id());
        return replacement == null || replacement.netIndex() != index || replacement.isBuilding() != now.isBuilding()
                ? "template " + now.id() + " is in use and must keep its index and kind"
                : null;
    }
}
