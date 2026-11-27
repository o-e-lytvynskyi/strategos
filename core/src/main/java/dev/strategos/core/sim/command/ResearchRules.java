package dev.strategos.core.sim.command;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.shared.QueueEntry;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.prod.Techs;
import org.jspecify.annotations.Nullable;

/**
 * Research (TECH-01, spec §2.1): an own finished building whose {@code Researcher/techs} holds the tech queues it beside
 * unit batches, paying its cost, unless the faction has the tech or any of its buildings queues it, has or queues a
 * tech it excludes (TECH-05, a pair: {@code LIMIT}), its requirements are unmet, the queue is full or the stock falls
 * short.
 */
final class ResearchRules {
    private ResearchRules() {}

    static final class ResearchRule implements CommandRule<Research> {
        @Override
        public CommandKind<Research> kind() {
            return CommandKinds.RESEARCH;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Research research, MatchState state) {
            RejectReason reason = TrainRules.own(envelope, research.building(), state);
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            BuildingStore b = state.buildings();
            if (b.faction(research.building()) == state.gaia()) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNSUPPORTED); // Gaia has no progress
            }
            TechDef tech = state.templates().tech(research.tech());
            if (tech == null) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNKNOWN_TEMPLATE);
            }
            RejectReason refusal = Techs.refusal(state, research.building(), tech);
            if (refusal != null) {
                return Tally.rejectAll(envelope, kind(), 1, refusal);
            }
            int faction = b.faction(research.building());
            if (b.queues().queueSize(research.building()) >= BuildingStore.QUEUE_MAX) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.QUEUE_FULL);
            }
            long[] cost = state.costs().ofTech(tech);
            long @Nullable [] missing = state.treasury().missing(faction, cost);
            if (missing != null) {
                return Tally.unaffordable(envelope, kind(), 1, missing);
            }
            state.treasury().pay(faction, cost);
            b.queues().enqueue(research.building(), QueueEntry.techEntry(tech.netIndex()));
            return Tally.acceptAll(envelope, kind(), 1);
        }
    }
}
