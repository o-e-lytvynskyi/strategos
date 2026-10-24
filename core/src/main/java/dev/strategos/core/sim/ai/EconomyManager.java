package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Sends idle workers to gather (04 §14's EconomyManager): each to the resource furthest under its share of the
 * script's ratio (weighed by the stock: doubled while it runs low, halved while it piles up) that has a node left, at
 * the nearest node of it with a free slot, from one central count, so no queue overshoots. A field
 * is gathered only near its own base; animals are left alone (AI-02 brings livestock in).
 */
final class EconomyManager implements AiModule {
    /** How far from its centre a field is its own. */
    static final double OWN_FIELD = 32;

    /** A stock under this is low, over that piled up, in thousandths. */
    static final long LOW = 100_000;

    static final long HIGH = 400_000;

    /** The most gatherers moved a think. */
    static final int MOVED = 2;

    private final StrategyScript script;
    private final Map<String, Long> stocks = new TreeMap<>(); // this think's, by resource

    EconomyManager(StrategyScript script) {
        this.script = script;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        Base base = Base.of(view);
        List<String> resources = view.resources();
        stocks.clear();
        for (String r : resources) {
            stocks.put(r, view.stock(r));
        }
        int[] gatherers = view.gatherers();
        List<NodeView> nodes = view.nodes();
        Map<Integer, Integer> free = new TreeMap<>(); // a node's free slots, less this think's sends
        for (UnitView u : view.units()) {
            if (budget.exhausted()) {
                return;
            }
            if (u.state() != UnitState.IDLE || !view.is(u.template(), "Gatherer")) {
                continue;
            }
            NodeView target = null;
            int r = -1;
            for (int choice : byNeed(resources, gatherers)) { // the most lacking resource that has a node
                target = nearest(nodes, resources.get(choice), u, base, free);
                if (target != null) {
                    r = choice;
                    break;
                }
            }
            if (target == null) {
                continue;
            }
            free.merge(target.id(), -1, Integer::sum);
            gatherers[r]++;
            budget.spend(10);
            sink.send(Gather.replace(UnitSet.of(u.handle()), target.topX(), target.topY(), target.topZ()));
        }
        rebalance(view, budget, sink, nodes, base);
    }

    /** The resources the script gathers, the furthest under its share of the ratio first. */
    private List<Integer> byNeed(List<String> resources, int[] gatherers) {
        List<Integer> out = new java.util.ArrayList<>();
        for (int r = 0; r < resources.size(); r++) {
            Integer weight = script.ratio().get(resources.get(r));
            if (weight != null && weight > 0) {
                out.add(r);
            }
        }
        out.sort((a, b) -> {
            double sa = gatherers[a] / weight(resources.get(a));
            double sb = gatherers[b] / weight(resources.get(b));
            return sa != sb ? Double.compare(sa, sb) : Integer.compare(a, b);
        });
        return out;
    }

    /** The ratio's weight of {@code resource}, doubled while its stock runs low and halved while it piles up. */
    private double weight(String resource) {
        double w = script.ratio().getOrDefault(resource, 1);
        long stock = stocks.getOrDefault(resource, 0L);
        return stock < LOW ? w * 2 : stock > HIGH ? w / 2 : w;
    }

    /**
     * While one resource piles up past {@value #HIGH} thousandths and another runs under {@value #LOW}, up to
     * {@value #MOVED} gatherers of the first are sent to the second each think.
     */
    private void rebalance(AiView view, AiBudget budget, CommandSink sink, List<NodeView> nodes, @Nullable Base base) {
        String rich = null;
        String poor = null;
        for (String r : script.ratio().keySet()) {
            long stock = stocks.getOrDefault(r, 0L);
            if (stock > HIGH && (rich == null || stock > stocks.getOrDefault(rich, 0L))) {
                rich = r;
            }
            if (stock < LOW && (poor == null || stock < stocks.getOrDefault(poor, 0L))) {
                poor = r;
            }
        }
        if (rich == null || poor == null) {
            return;
        }
        int moved = 0;
        Map<Integer, Integer> free = new TreeMap<>();
        for (UnitView u : view.units()) {
            if (moved == MOVED || budget.exhausted()) {
                return;
            }
            if (u.state() == UnitState.GATHERING && rich.equals(view.gathering(u))) {
                NodeView target = nearest(nodes, poor, u, base, free);
                if (target == null) {
                    return;
                }
                free.merge(target.id(), -1, Integer::sum);
                moved++;
                budget.spend(10);
                sink.send(Gather.replace(UnitSet.of(u.handle()), target.topX(), target.topY(), target.topZ()));
            }
        }
    }

    private static @Nullable NodeView nearest(
            List<NodeView> nodes, String resource, UnitView u, @Nullable Base base, Map<Integer, Integer> free) {
        NodeView best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (NodeView n : nodes) {
            if (!n.resource().equals(resource)
                    || n.kind() == MaterialKind.CARCASS // livestock is AI-02's (M4 T19): M3's bot leaves it
                    || n.amount() <= 0
                    || n.ripening() > 0
                    || n.topY() < 0
                    || n.freeSlots() + free.getOrDefault(n.id(), 0) <= 0) {
                continue;
            }
            if (n.kind() == MaterialKind.CROP
                    && (base == null || DetMath.length(n.anchorX() - base.x(), n.anchorZ() - base.z()) > OWN_FIELD)) {
                continue;
            }
            double d = DetMath.length(n.anchorX() - u.x(), n.anchorZ() - u.z());
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }
}
