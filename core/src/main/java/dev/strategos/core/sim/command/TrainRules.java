package dev.strategos.core.sim.command;

import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.TrainBatch;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.econ.Fields;
import dev.strategos.core.sim.prod.Production;
import org.jspecify.annotations.Nullable;

/**
 * The production commands (PROD-01, PROD-02, PROD-03): Train joins an entry to a finished building's queue that trains
 * the template, paying its cost, TrainBatch an entry of up to five, or is refused (a full queue as {@code QUEUE_FULL}, a lack with the missing amounts);
 * CancelTrain refunds an entry; SetRally points buildings' new units at the ground, a node or a building; Reseed sows an
 * empty field again (ECO-09). Each building must be the issuer's faction's.
 */
final class TrainRules {
    private TrainRules() {}

    /** Why the issuer may not use building {@code handle}, or null. */
    static @Nullable RejectReason own(CommandEnvelope envelope, int handle, MatchState state) {
        if (!state.buildings().alive(handle)) {
            return RejectReason.UNKNOWN_TARGET;
        }
        if (!envelope.issuer().isAdmin()
                && envelope.issuer().value() != state.buildings().faction(handle)) {
            return RejectReason.NOT_OWNER;
        }
        return null;
    }

    static final class TrainRule implements CommandRule<Train> {
        @Override
        public CommandKind<Train> kind() {
            return CommandKinds.TRAIN;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Train train, MatchState state) {
            RejectReason reason = own(envelope, train.building(), state);
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            BuildingStore b = state.buildings();
            UnitTemplate template = state.templates().byId(train.template());
            if (template == null) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNKNOWN_TEMPLATE);
            }
            if (b.state(train.building()) != BuildingStore.COMPLETE
                    || !Production.trains(state, train.building(), template.netIndex())) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNSUPPORTED);
            }
            if (b.queueSize(train.building()) >= BuildingStore.QUEUE_MAX) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.QUEUE_FULL);
            }
            int faction = b.faction(train.building());
            long[] cost = state.costs().of(state.stats(), template.netIndex(), faction);
            long[] missing = state.treasury().missing(faction, cost);
            if (missing != null) {
                return Tally.unaffordable(envelope, kind(), 1, missing);
            }
            state.treasury().pay(faction, cost);
            b.enqueue(train.building(), template.netIndex());
            return Tally.acceptAll(envelope, kind(), 1);
        }
    }

    /**
     * TrainBatch (PROD-02): as Train, for {@code count} units of one template in a single entry, paying {@code count}
     * times the cost; a count outside 1 to 5 is an invalid argument.
     */
    static final class TrainBatchRule implements CommandRule<TrainBatch> {
        @Override
        public CommandKind<TrainBatch> kind() {
            return CommandKinds.TRAIN_BATCH;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, TrainBatch train, MatchState state) {
            RejectReason reason = own(envelope, train.building(), state);
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            if (train.count() < 1 || train.count() > TrainBatch.MOST) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.INVALID_ARGUMENT);
            }
            BuildingStore b = state.buildings();
            UnitTemplate template = state.templates().byId(train.template());
            if (template == null) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNKNOWN_TEMPLATE);
            }
            if (b.state(train.building()) != BuildingStore.COMPLETE
                    || !Production.trains(state, train.building(), template.netIndex())) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNSUPPORTED);
            }
            if (b.queueSize(train.building()) >= BuildingStore.QUEUE_MAX) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.QUEUE_FULL);
            }
            int faction = b.faction(train.building());
            long[] cost = state.costs().of(state.stats(), template.netIndex(), faction);
            for (int r = 0; r < cost.length; r++) {
                cost[r] = Math.multiplyExact(cost[r], train.count());
            }
            long[] missing = state.treasury().missing(faction, cost);
            if (missing != null) {
                return Tally.unaffordable(envelope, kind(), 1, missing);
            }
            state.treasury().pay(faction, cost);
            b.enqueue(train.building(), template.netIndex(), train.count());
            return Tally.acceptAll(envelope, kind(), 1);
        }
    }

    static final class CancelRule implements CommandRule<CancelTrain> {
        @Override
        public CommandKind<CancelTrain> kind() {
            return CommandKinds.CANCEL_TRAIN;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, CancelTrain cancel, MatchState state) {
            RejectReason reason = own(envelope, cancel.building(), state);
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            if (cancel.index() < 0 || cancel.index() >= state.buildings().queueSize(cancel.building())) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.INVALID_ARGUMENT);
            }
            Production.cancel(state, cancel.building(), cancel.index());
            return Tally.acceptAll(envelope, kind(), 1);
        }
    }

    /** Reseed (ECO-09): an own finished field reaped empty ripens again, its reseed paid, or the lack is named. */
    static final class ReseedRule implements CommandRule<Reseed> {
        @Override
        public CommandKind<Reseed> kind() {
            return CommandKinds.RESEED;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, Reseed reseed, MatchState state) {
            RejectReason reason = own(envelope, reseed.field(), state);
            if (reason != null) {
                return Tally.rejectAll(envelope, kind(), 1, reason);
            }
            if (state.buildings().field(reseed.field()) == 0) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.UNSUPPORTED);
            }
            if (!Fields.empty(state, reseed.field())) {
                return Tally.rejectAll(envelope, kind(), 1, RejectReason.INVALID_ARGUMENT); // it still holds or ripens
            }
            int faction = state.buildings().faction(reseed.field());
            long[] cost = Fields.reseedCost(state, reseed.field());
            long[] missing = state.treasury().missing(faction, cost);
            if (missing != null) {
                return Tally.unaffordable(envelope, kind(), 1, missing);
            }
            state.treasury().pay(faction, cost);
            Fields.reseed(state, reseed.field());
            return Tally.acceptAll(envelope, kind(), 1);
        }
    }

    static final class RallyRule implements CommandRule<SetRally> {
        @Override
        public CommandKind<SetRally> kind() {
            return CommandKinds.SET_RALLY;
        }

        @Override
        public CommandResult apply(CommandEnvelope envelope, SetRally rally, MatchState state) {
            if (rally.buildings().size() == 0) {
                return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
            }
            int kind = BuildingStore.RALLY_POINT;
            double x = rally.x() + 0.5;
            double z = rally.z() + 0.5;
            int target = GatherRule.resolve(state, rally.x(), rally.y(), rally.z());
            if (target != 0) {
                kind = BuildingStore.RALLY_NODE;
                x = state.nodes().anchorX(target);
                z = state.nodes().anchorZ(target);
            } else {
                for (int handle : state.buildings().handles()) {
                    var at = state.footprintOf(handle);
                    if (rally.x() >= at.anchorX()
                            && rally.x() < at.anchorX() + at.spanX()
                            && rally.z() >= at.anchorZ()
                            && rally.z() < at.anchorZ() + at.spanZ()) {
                        kind = BuildingStore.RALLY_BUILDING;
                        target = handle;
                        break;
                    }
                }
            }
            Tally tally = new Tally();
            for (int k = 0; k < rally.buildings().size(); k++) {
                int handle = rally.buildings().get(k);
                RejectReason reason = own(envelope, handle, state);
                if (reason != null) {
                    tally.reject(reason);
                    continue;
                }
                state.buildings().setRally(handle, kind, x, z, target);
                tally.accept();
            }
            return tally.result(envelope, kind());
        }
    }
}
