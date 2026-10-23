package dev.strategos.core.sim;

import dev.strategos.core.sim.econ.CarryStore;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitAi;
import java.util.Arrays;

/**
 * P7, WORLD: each unit's gear (ECO-05), its task's tool and its load, posted as {@link UnitGear} when it changes. A
 * unit at work on a tree, on its way to one or back from one holds an axe; at a bush nothing; a builder on its way to
 * a site or at work there the builder's tool (BLD-03); any unit carrying a load shows it. The last gear posted is the view's, not the match's: it is neither hashed nor saved, so a restored core
 * posts every unit's gear that is not plain once.
 */
final class GearSystem implements MatchSystem {
    private static final int UNKNOWN = -1;
    private static final int PLAIN = 0;

    private int[] posted = new int[0];

    @Override
    public Phase phase() {
        return Phase.WORLD;
    }

    @Override
    public void run(MatchState s) {
        EntityStore entities = s.entities();
        int high = entities.highWater();
        if (posted.length < high) {
            int old = posted.length;
            posted = Arrays.copyOf(posted, Math.max(high, posted.length * 2));
            Arrays.fill(posted, old, posted.length, UNKNOWN); // a fresh or restored core knows nothing posted
        }
        CarryStore carry = s.carry();
        for (int i = 0; i < high; i++) {
            if (!entities.aliveAt(i)) {
                posted[i] = PLAIN; // a unit made here later starts with nothing in its hands
                continue;
            }
            int tool = tool(s, i);
            int resource = carry.resource(i);
            int subtype = resource == CarryStore.NONE ? 0 : carry.subtype(i);
            int gear = (tool << 16) | ((resource + 1) << 8) | subtype;
            if (gear != posted[i] && !(posted[i] == UNKNOWN && gear == PLAIN)) {
                s.outputs().add(new UnitGear(entities.handleAt(i), tool, resource, resource < 0 ? -1 : subtype));
            }
            posted[i] = gear;
        }
    }

    /** The tool of unit {@code i}'s task: an axe for a tree it works or walks to or from; the builder's at a site. */
    private static int tool(MatchState s, int i) {
        int leaf = s.ai().code(i);
        if (s.orders().kind(i) == OrderKind.BUILD
                && (leaf == UnitAi.BUILD_APPROACHING.code() || leaf == UnitAi.BUILDING.code())) {
            return UnitGear.BUILDING;
        }
        if (s.orders().kind(i) != OrderKind.GATHER) {
            return UnitGear.NONE;
        }
        if (leaf != UnitAi.GATHER_APPROACHING.code()
                && leaf != UnitAi.GATHERING.code()
                && leaf != UnitAi.GATHER_RETURNING.code()) {
            return UnitGear.NONE;
        }
        int node = s.orders().target(i);
        if (node < 1 || node > s.nodes().count()) {
            return UnitGear.NONE;
        }
        return switch (s.nodes().kind(node)) {
            case LOG -> UnitGear.AXE;
            case CROP -> UnitGear.HOE;
            default -> UnitGear.NONE;
        };
    }
}
