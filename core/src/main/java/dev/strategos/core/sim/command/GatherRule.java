package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderReceived;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.econ.Felling;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.core.sim.vision.VisionSystem;

/**
 * Gather (ECO-05): the server resolves the node the block belongs to, the one that holds it, else the nearest of the
 * block's subtype within {@value #NEAR} blocks, or for a leaf the tree whose log is nearest within vanilla's six leaf
 * steps; no node is refused as an unknown target. Each owned unit that gathers that subtype gets a gather order on it,
 * joined to its queue by the command's mode; a unit that does not, a soldier say, is refused as unsupported.
 */
final class GatherRule implements CommandRule<Gather> {
    /** How far from the block the nearest node of its subtype may be, in blocks. */
    static final double NEAR = 3;

    @Override
    public CommandKind<Gather> kind() {
        return CommandKinds.GATHER;
    }

    @Override
    public CommandResult apply(CommandEnvelope envelope, Gather gather, MatchState state) {
        UnitSet units = gather.units();
        if (units.size() == 0) {
            return Tally.rejectAll(envelope, kind(), 0, RejectReason.INVALID_ARGUMENT);
        }
        int node = resolve(state, gather.x(), gather.y(), gather.z());
        if (node == 0) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.UNKNOWN_TARGET);
        }
        NodeStore nodes = state.nodes();
        double x = nodes.anchorX(node);
        double z = nodes.anchorZ(node);
        if (!explored(state, envelope.issuer(), x, z)) {
            return Tally.rejectAll(envelope, kind(), units.size(), RejectReason.NOT_EXPLORED);
        }
        int resource = nodes.resource(node);
        int subtype = nodes.subtype(node);
        OrderQueues orders = state.orders();
        Tally tally = new Tally();
        for (int k = 0; k < units.size(); k++) {
            int handle = units.get(k);
            RejectReason reason = Ownership.check(envelope.issuer(), handle, state);
            if (reason != null) {
                tally.reject(reason);
                continue;
            }
            int i = Handle.index(handle);
            if (!state.gatherRates().gathers(state.stats(), i, resource, subtype)) {
                tally.reject(RejectReason.UNSUPPORTED);
                continue;
            }
            int before = orders.size(i);
            boolean joined = switch (gather.mode()) {
                case REPLACE -> {
                    orders.replace(i, OrderKind.GATHER, x, z, 0, 0, node);
                    yield true;
                }
                case QUEUE -> orders.queue(i, OrderKind.GATHER, x, z, 0, 0, node);
                case PUSH_FRONT -> orders.pushFront(i, OrderKind.GATHER, x, z, 0, 0, node);
            };
            if (!joined) {
                tally.reject(RejectReason.QUEUE_FULL);
                continue;
            }
            if (gather.mode() != QueueMode.QUEUE || before == 0) {
                state.bus().publish(new OrderReceived(handle));
            }
            tally.accept();
        }
        return tally.result(envelope, kind());
    }

    /** Whether the issuer's faction has explored the node's cell (ADR-0025 / MATCH-07). */
    static boolean explored(MatchState state, PlayerSlot issuer, double x, double z) {
        if (issuer.isAdmin()) {
            return true;
        }
        PlayRules play = state.config().rules().play();
        if (play.revealMap() || play.explored()) {
            return true;
        }
        VisionSystem vision = state.visionSystem();
        int faction = issuer.value();
        if (vision == null || faction < 0 || faction >= state.config().factions()) {
            return true;
        }
        return vision.grid(faction).isExplored(VisionGrid.cellOf(x), VisionGrid.cellOf(z));
    }

    /** The node the block at (x, y, z) belongs to, or 0. */
    static int resolve(MatchState state, int x, int y, int z) {
        NodeStore nodes = state.nodes();
        if (nodes.count() == 0) {
            return 0;
        }
        long packed = NodeStore.pack(x, y, z);
        int held = nodes.holding(packed);
        if (held != 0) {
            return held;
        }
        Material m = state.materialAt(x, y, z);
        if (m == null) {
            return 0;
        }
        if (m.kind() == MaterialKind.LEAVES) {
            return nodes.nearestBlock(packed, MaterialKind.LOG, -1, 0, Felling.DECAY_DISTANCE - 1);
        }
        int resource = state.config().rules().economy().indexOf(m.resource());
        int subtype = resource < 0
                ? -1
                : state.config()
                        .rules()
                        .economy()
                        .resources()
                        .get(resource)
                        .subtypes()
                        .indexOf(m.subtype());
        if (subtype < 0) {
            return 0;
        }
        return nodes.nearestBlock(packed, m.kind(), resource, subtype, NEAR);
    }
}
