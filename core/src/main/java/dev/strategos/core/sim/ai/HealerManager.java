package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Ungarrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The healers and the hurt (AI-09, M10 §2.9): the healers, whose {@code heal} autocasts on its own, follow the army a few
 * blocks behind its middle and go home with it; a veteran that fell under the script's {@code veterans.below} is
 * sheltered, in a temple or a civic centre within {@code near} blocks if one has room for it, else it falls back to the
 * healers' station, and stays out of the fight ({@link #resting}) until it is healed to {@code resume}; the garrisoned
 * go out at the door once they are. (A temple's healers are trained by the production manager.)
 */
final class HealerManager implements AiModule {
    /** The role of the unit that heals, and of the building that trains it and heals the units inside. */
    static final String HEALER = "healer";

    static final String TEMPLE = "temple";

    /** Blocks behind the army's middle, toward the base, where the healers stand. */
    static final double REAR = 10;

    /** Blocks from the civic centre's middle to the healers' station at home: clear of its footprint. */
    static final double HOME_BESIDE = 10;

    /** A healer this near its station, or on its way to it, is left alone. */
    private static final double SETTLED = 3;

    private final StrategyScript script;

    HealerManager(StrategyScript script) {
        this.script = script;
    }

    /**
     * Whether veteran {@code u} is out of the fight to heal: it is of the rank, and its health is under {@code below}, or
     * under {@code resume} while it stands idle or is on its way into a building. A garrisoned one is out of every list.
     */
    static boolean resting(AiView view, UnitView u, Depth.Veterans v) {
        if (!v.veteran(view.rank(u)) || view.role(u) == AiView.ROLE_NONE) {
            return false;
        }
        double health = view.health(u);
        return health < v.below()
                || (health < v.resume()
                        && (u.state() == UnitState.IDLE || view.garrisoning(u) || walkingHome(view, u)));
    }

    /** Whether {@code u} is on a plain move that ends beside an own civic centre: a hurt unit on its way to heal. */
    private static boolean walkingHome(AiView view, UnitView u) {
        Vec2d t = u.target();
        if (u.state() != UnitState.WALKING || t == null) {
            return false;
        }
        for (BuildingView b : view.buildings()) {
            if (view.isCentre(b)
                    && DetMath.length(
                                    t.x()
                                            - (b.footprint().anchorX()
                                                    + b.footprint().spanX() / 2.0),
                                    t.z()
                                            - (b.footprint().anchorZ()
                                                    + b.footprint().spanZ() / 2.0))
                            <= 2 * HOME_BESIDE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        Base base = Base.of(view);
        if (base == null || budget.exhausted()) {
            return;
        }
        Depth depth = script.depth();
        if (depth.healers() <= 0 && !depth.veterans().on()) { // no depth block: inert
            return;
        }
        @Nullable TemplateId healer = view.civRole(HEALER);
        List<UnitView> healers = new ArrayList<>();
        List<UnitView> soldiers = new ArrayList<>();
        for (UnitView u : view.units()) {
            if (u.state() == UnitState.GARRISONED) {
                continue;
            }
            if (healer != null && u.template().equals(healer)) {
                healers.add(u);
            } else if (view.role(u) != AiView.ROLE_NONE) {
                soldiers.add(u);
            }
        }
        Vec2d rear = rear(base, soldiers, Tactics.home(script.attack()));
        if (depth.veterans().on()) {
            release(view, budget, sink, depth.veterans());
            shelter(view, budget, sink, base, depth.veterans(), soldiers, !healers.isEmpty() ? rear : null);
        }
        follow(budget, sink, healers, rear);
    }

    /**
     * The healers' station: {@value #REAR} blocks behind the middle of the soldiers beyond {@code home} blocks of the
     * base, toward it; the base's own middle when fewer than three stand out (no army to follow).
     */
    private static Vec2d rear(Base base, List<UnitView> soldiers, double home) {
        double cx = 0;
        double cz = 0;
        int out = 0;
        for (UnitView u : soldiers) {
            if (DetMath.length(u.x() - base.x(), u.z() - base.z()) > home) {
                cx += u.x();
                cz += u.z();
                out++;
            }
        }
        if (out < 3) { // no army out: beside the centre, toward the enemy, not on its footprint
            double ex = base.enemyX() - base.x();
            double ez = base.enemyZ() - base.z();
            double e = Math.max(1, DetMath.length(ex, ez));
            return new Vec2d(base.x() + ex / e * HOME_BESIDE, base.z() + ez / e * HOME_BESIDE);
        }
        cx /= out;
        cz /= out;
        double dx = base.x() - cx;
        double dz = base.z() - cz;
        double d = Math.max(1, DetMath.length(dx, dz));
        double back = Math.min(REAR, d);
        return new Vec2d(cx + dx / d * back, cz + dz / d * back);
    }

    private static void follow(AiBudget budget, CommandSink sink, List<UnitView> healers, Vec2d rear) {
        List<Integer> going = new ArrayList<>();
        for (UnitView u : healers) {
            Vec2d t = u.target();
            boolean there = DetMath.length(u.x() - rear.x(), u.z() - rear.z()) <= SETTLED
                    || (t != null && DetMath.length(t.x() - rear.x(), t.z() - rear.z()) <= SETTLED);
            if (!there) {
                going.add(u.handle());
            }
        }
        if (!going.isEmpty() && budget.spend(10)) {
            sink.send(Move.replace(
                    UnitSet.of(going.stream().mapToInt(Integer::intValue).toArray()), rear));
        }
    }

    /** The own finished temples and civic centres. */
    private static List<BuildingView> sites(AiView view) {
        TemplateId temple = view.civRole(TEMPLE);
        List<BuildingView> out = new ArrayList<>();
        for (BuildingView b : view.buildings()) {
            if (b.state() == BuildingStore.COMPLETE
                    && (view.isCentre(b) || b.template().equals(temple))) {
                out.add(b);
            }
        }
        return out;
    }

    /** Lets the units healed to {@code resume} out of the temples and centres they are in. */
    private static void release(AiView view, AiBudget budget, CommandSink sink, Depth.Veterans v) {
        for (BuildingView b : sites(view)) {
            List<Integer> healed = new ArrayList<>();
            for (UnitView u : view.occupants(b.handle())) {
                if (view.role(u) != AiView.ROLE_NONE && view.health(u) >= v.resume()) {
                    healed.add(u.handle());
                }
            }
            if (!healed.isEmpty() && budget.spend(10)) {
                sink.send(new Ungarrison(
                        b.handle(),
                        UnitSet.of(healed.stream().mapToInt(Integer::intValue).toArray())));
            }
        }
    }

    /**
     * Sends each resting veteran not yet on its way into the nearest site within the script's reach that has room for it,
     * else to the healers' station {@code rear} (null without healers: it keeps fighting then, nothing would heal it).
     */
    private static void shelter(
            AiView view,
            AiBudget budget,
            CommandSink sink,
            Base base,
            Depth.Veterans v,
            List<UnitView> soldiers,
            @Nullable Vec2d rear) {
        List<BuildingView> sites = sites(view);
        Map<Integer, List<Integer>> into = new TreeMap<>();
        List<Integer> back = new ArrayList<>();
        for (UnitView u : soldiers) {
            if (!resting(view, u, v) || view.garrisoning(u)) {
                continue;
            }
            BuildingView site = nearest(view, sites, u, v.near());
            if (site != null) {
                into.computeIfAbsent(site.handle(), k -> new ArrayList<>()).add(u.handle());
                continue;
            }
            Vec2d t = u.target();
            boolean there = rear == null
                    || DetMath.length(u.x() - rear.x(), u.z() - rear.z()) <= REAR
                    || (t != null && DetMath.length(t.x() - rear.x(), t.z() - rear.z()) <= SETTLED);
            if (!there
                    && !(u.state() == UnitState.IDLE && DetMath.length(u.x() - base.x(), u.z() - base.z()) <= REAR)) {
                back.add(u.handle());
            }
        }
        into.forEach((building, units) -> {
            if (budget.spend(10)) {
                sink.send(new Garrison(
                        UnitSet.of(units.stream().mapToInt(Integer::intValue).toArray()), building));
            }
        });
        if (!back.isEmpty() && rear != null && budget.spend(10)) {
            sink.send(Move.replace(
                    UnitSet.of(back.stream().mapToInt(Integer::intValue).toArray()), rear));
        }
    }

    /** The site within {@code reach} blocks of {@code u} that has room for it, the nearest first; null for none. */
    private static @Nullable BuildingView nearest(AiView view, List<BuildingView> sites, UnitView u, int reach) {
        return sites.stream()
                .filter(b -> view.garrisonRoom(u.handle(), b.handle()) > 0)
                .map(b -> Map.entry(b, distance(u, b)))
                .filter(e -> e.getValue() <= reach)
                .min(Comparator.<Map.Entry<BuildingView, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparingInt(e -> e.getKey().handle()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static double distance(UnitView u, BuildingView b) {
        return DetMath.length(
                u.x() - (b.footprint().anchorX() + b.footprint().spanX() / 2.0),
                u.z() - (b.footprint().anchorZ() + b.footprint().spanZ() / 2.0));
    }
}
