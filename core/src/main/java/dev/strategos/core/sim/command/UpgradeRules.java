package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UpgradeBuilding;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.prod.Upgrades;
import org.jspecify.annotations.Nullable;

/**
 * UpgradeBuilding (BLD-12, spec §2.8): an own finished building whose template names an upgrade queues it at the head
 * of its queue, paying its cost, unless the building cannot upgrade, one is already queued, the faction lacks the
 * phase it requires, the queue is full or the stock falls short.
 */
final class UpgradeRules {
    private UpgradeRules() {}

    static final class UpgradeBuildingRule implements CommandRule<UpgradeBuilding> {
        @Override
        public CommandKind<UpgradeBuilding> kind() {
            return CommandKinds.UPGRADE_BUILDING;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, UpgradeBuilding upgrade, MatchState state) {
            int building = upgrade.building();
            RejectReason reason = TrainRules.own(envelope, building, state);
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            BuildingStore b = state.buildings();
            int faction = b.faction(building);
            int to = Upgrades.target(state, building);
            if (faction == state.gaia()
                    || b.state(building) != BuildingStore.COMPLETE
                    || to < 0
                    || !Upgrades.fits(state, building, to)) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNSUPPORTED);
            }
            if (Upgrades.pending(state, building)) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.LIMIT);
            }
            if (!Upgrades.requirementsMet(state, building)) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.REQUIREMENTS);
            }
            if (b.queues().queueSize(building) >= BuildingStore.QUEUE_MAX) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.QUEUE_FULL);
            }
            long[] cost = Upgrades.cost(state, building);
            long @Nullable [] missing = state.treasury().missing(faction, cost);
            if (missing != null) {
                return Tally.unaffordable(envelope, kind(), 1, missing);
            }
            state.treasury().pay(faction, cost);
            Upgrades.enqueue(state, building, to);
            return Tally.acceptAll(envelope, kind(), 1);
        }
    }
}
