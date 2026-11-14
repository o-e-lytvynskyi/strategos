package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The mop-up (QA-12, M6 §2.21): once an enemy centre has fallen, the army no longer marches as a wave on one target. Its
 * idle soldiers, in squads of {@value #SQUAD} by handle, each burn a different enemy building (the nearest one no one
 * burns yet), one squad hunts the enemy's units in sight, and, with no building or unit in sight, the squads sweep the
 * resource nodes near the enemy's start, where its last workers and storehouses stand, a different one each; the rams
 * go one to a building too. A pure function of the match in a think: no squad is remembered, a squad is whoever stands
 * idle. Without it the winner's army stood idle at the fallen centre for the rest of the match.
 */
final class MopUp {
    /** Soldiers to a squad. */
    static final int SQUAD = 4;

    /** The resource nodes within this of the enemy's start are the sweep's places. */
    static final double SWEEP_REACH = 160;

    /** How many places a sweep has, and how long a squad stays at one: ticks. */
    static final int SWEEP_PLACES = 40;

    static final long SWEEP_STAY = 400;

    private MopUp() {}

    /** Thinks the mop-up; true when it gave orders (the think is done). */
    static boolean run(AiView view, AiBudget budget, CommandSink sink, Base base, List<UnitView> soldiers) {
        List<BuildingView> buildings = new ArrayList<>();
        for (BuildingView b : view.enemyBuildings()) {
            if (!view.is(b.template(), "Field") && !view.is(b.template(), "Decoration")) {
                buildings.add(b);
            }
        }
        List<UnitView> enemies = new ArrayList<>();
        for (UnitView e : view.enemyUnits()) {
            if (e.state() != UnitState.GARRISONED) {
                enemies.add(e);
            }
        }
        List<UnitView> idle = new ArrayList<>();
        List<UnitView> rams = new ArrayList<>();
        Map<Integer, Integer> burners = new TreeMap<>(); // building -> own units on it
        for (UnitView u : view.units()) {
            boolean ram = view.is(u.template(), "Ram");
            if (!ram && !soldiers.contains(u)) {
                continue;
            }
            if (u.state() == UnitState.GARRISONED) {
                continue;
            }
            if (u.state() == UnitState.IDLE) {
                (ram ? rams : idle).add(u);
            }
            for (BuildingView b : buildings) {
                if (view.burning(u, b.handle()) || view.attackTarget(u) == b.handle()) {
                    burners.merge(b.handle(), 1, Integer::sum);
                }
            }
        }
        budget.spend(soldiers.size() + buildings.size());
        if (idle.isEmpty() && rams.isEmpty()) {
            return false;
        }
        idle.sort(Comparator.comparingInt(UnitView::handle));
        rams.sort(Comparator.comparingInt(UnitView::handle));
        boolean sent = false;
        for (UnitView ram : rams) {
            BuildingView b = nearest(buildings, burners, ram.x(), ram.z());
            if (b == null || budget.exhausted()) {
                break;
            }
            burners.merge(b.handle(), 1, Integer::sum);
            budget.spend(10);
            sink.send(Attack.replace(UnitSet.of(ram.handle()), b.handle()));
            sent = true;
        }
        List<NodeView> places = null;
        for (int from = 0, squad = 0; from < idle.size(); from += SQUAD, squad++) {
            if (budget.exhausted()) {
                break;
            }
            List<UnitView> group = idle.subList(from, Math.min(idle.size(), from + SQUAD));
            double cx = 0;
            double cz = 0;
            for (UnitView u : group) {
                cx += u.x();
                cz += u.z();
            }
            cx /= group.size();
            cz /= group.size();
            UnitView prey = squad == 0 ? nearestUnit(enemies, cx, cz) : null;
            BuildingView b = prey == null ? nearest(buildings, burners, cx, cz) : null;
            budget.spend(10);
            if (prey != null) {
                sink.send(Attack.replace(handles(group), prey.handle()));
            } else if (b != null) {
                burners.merge(b.handle(), group.size(), Integer::sum);
                sendAtBuilding(sink, group, view, b);
            } else {
                if (places == null) {
                    places = sweepPlaces(view, base);
                }
                Vec2d at = sweepAt(places, base, view.tick(), squad);
                sink.send(AttackMove.replace(handles(group), at));
            }
            sent = true;
        }
        return sent;
    }

    /** Foot soldiers burn the building; archers, which cannot, attack-move to it and shoot what defends it. */
    private static void sendAtBuilding(CommandSink sink, List<UnitView> group, AiView view, BuildingView b) {
        List<UnitView> burners = new ArrayList<>();
        List<UnitView> shooters = new ArrayList<>();
        for (UnitView u : group) {
            (view.role(u) == AiView.ROLE_ARCHER ? shooters : burners).add(u);
        }
        if (!burners.isEmpty()) {
            sink.send(Attack.replace(handles(burners), b.handle()));
        }
        if (!shooters.isEmpty()) {
            sink.send(AttackMove.replace(
                    handles(shooters),
                    new Vec2d(
                            b.footprint().anchorX() + b.footprint().spanX() / 2.0,
                            b.footprint().anchorZ() + b.footprint().spanZ() / 2.0)));
        }
    }

    private static UnitSet handles(List<UnitView> units) {
        return UnitSet.of(units.stream().mapToInt(UnitView::handle).toArray());
    }

    /** The building no own unit burns yet, nearest to (x, z); the least burned, nearest, when all are. */
    private static @org.jspecify.annotations.Nullable BuildingView nearest(
            List<BuildingView> buildings, Map<Integer, Integer> burners, double x, double z) {
        BuildingView best = null;
        int bestBurners = Integer.MAX_VALUE;
        double bestD = Double.POSITIVE_INFINITY;
        for (BuildingView b : buildings) {
            int n = burners.getOrDefault(b.handle(), 0);
            double d = DetMath.length(
                    b.footprint().anchorX() + b.footprint().spanX() / 2.0 - x,
                    b.footprint().anchorZ() + b.footprint().spanZ() / 2.0 - z);
            if (n < bestBurners || (n == bestBurners && d < bestD)) {
                bestBurners = n;
                bestD = d;
                best = b;
            }
        }
        return best;
    }

    private static @org.jspecify.annotations.Nullable UnitView nearestUnit(List<UnitView> units, double x, double z) {
        UnitView best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (UnitView u : units) {
            double d = DetMath.length(u.x() - x, u.z() - z);
            if (d < bestD) {
                bestD = d;
                best = u;
            }
        }
        return best;
    }

    /** The live nodes near the enemy's start a worker could be at, nearest first. */
    private static List<NodeView> sweepPlaces(AiView view, Base base) {
        List<NodeView> out = new ArrayList<>();
        for (NodeView n : view.nodes()) {
            if (n.amount() > 0
                    && n.kind() != MaterialKind.CROP
                    && n.kind() != MaterialKind.CARCASS
                    && DetMath.length(n.anchorX() - base.enemyX(), n.anchorZ() - base.enemyZ()) <= SWEEP_REACH) {
                out.add(n);
            }
        }
        out.sort(Comparator.comparingDouble(
                        (NodeView n) -> DetMath.length(n.anchorX() - base.enemyX(), n.anchorZ() - base.enemyZ()))
                .thenComparingInt(NodeView::id));
        return out.size() > SWEEP_PLACES ? new ArrayList<>(out.subList(0, SWEEP_PLACES)) : out;
    }

    /** Where squad {@code squad} sweeps at {@code tick}: a different place each, the next after {@value #SWEEP_STAY} ticks. */
    private static Vec2d sweepAt(List<NodeView> places, Base base, long tick, int squad) {
        if (places.isEmpty()) {
            return new Vec2d(base.enemyX(), base.enemyZ());
        }
        NodeView n = places.get((int) ((tick / SWEEP_STAY + squad) % places.size()));
        return new Vec2d(n.anchorX(), n.anchorZ());
    }
}
