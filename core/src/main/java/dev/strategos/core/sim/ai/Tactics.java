package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A wave's fight (AI-03, M5 §2.11), thought at the bot's cadence while the wave is out. The wave is the soldiers
 * beyond {@link #home} blocks of the base. Its strength is the sum of its soldiers' worth (what they cost to train,
 * food, wood, stone and iron together) times their health fraction; the enemy's is the same sum over the enemy soldiers
 * within {@code reach} blocks of the wave's middle, plus {@code defence} when the wave stands within {@link #ARROWS}
 * of the enemy's civic centre. Below {@code retreat} times the enemy's the wave goes home, every soldier, and stays
 * out of the fight until it is there. Otherwise the melee soldiers share one target in reach, the most wounded or the
 * dearest, kept until it dies or leaves reach, so a command goes out only when the choice changes; the archers
 * stand {@code behind} blocks behind the melee line's middle toward the base, and shoot at the target when it is in
 * their range (an archer's own stance shoots whatever else comes near).
 */
final class Tactics {
    /** Blocks from the enemy centre inside which its arrows count as defenders. */
    static final double ARROWS = 40;

    /** Farther than this from the target an archer holding the line does not take it up. */
    static final double SHOT = 18;

    /** Within this many blocks of its base a reserved spearman counts as home. */
    static final double HOME_RESERVE = 30;

    private static final double SLACK = 1.5;
    private static final double SETTLED = 2.5;

    private Tactics() {}

    /** How near its base a soldier is not in a wave: it stands at home. */
    static double home(StrategyScript.Attack attack) {
        return Math.min(12, attack.muster() / 2.0);
    }

    /**
     * Thinks the wave's fight; false when no wave is out (the fight is over, the caller schedules the next wave), true
     * when the tactics took this think.
     */
    private static final double ARROWS_FRONT = 16;

    static boolean fight(
            AiView view,
            AiBudget budget,
            CommandSink sink,
            AiMemory memory,
            Base base,
            StrategyScript script,
            List<UnitView> soldiers) {
        StrategyScript.Battle battle = script.battle();
        double home = home(script.attack());
        List<UnitView> wave = new ArrayList<>();
        double cx = 0;
        double cz = 0;
        for (UnitView u : soldiers) {
            if (DetMath.length(u.x() - base.x(), u.z() - base.z()) > home) {
                wave.add(u);
                cx += u.x();
                cz += u.z();
            }
        }
        if (wave.isEmpty()) {
            memory.set(Slots.FIGHT, 0);
            memory.set(Slots.FOCUS, 0);
            memory.set(Slots.CLEAR, 0);
            return false;
        }
        cx /= wave.size();
        cz /= wave.size();
        budget.spend(wave.size());
        // the enemy near the wave, and the strengths
        List<UnitView> near = new ArrayList<>();
        double enemy = 0;
        for (UnitView e : view.enemyUnits()) {
            if (view.role(e) != AiView.ROLE_NONE
                    && e.state() != UnitState.GARRISONED
                    && DetMath.length(e.x() - cx, e.z() - cz) <= battle.reach()) {
                near.add(e);
                enemy += view.worth(e) * view.health(e);
            }
        }
        budget.spend(near.size());
        boolean arrived = base.enemy() != null && DetMath.length(base.enemyX() - cx, base.enemyZ() - cz) <= ARROWS;
        if (arrived) {
            enemy += battle.defence();
        }
        double own = 0;
        for (UnitView u : wave) {
            own += view.worth(u) * view.health(u);
        }
        long state = memory.get(Slots.FIGHT);
        memory.set(Slots.CLEAR, state != 2 && arrived && near.isEmpty() ? 1 : 0);
        if (state == 2 || (battle.retreat() > 0 && enemy > 0 && own < battle.retreat() * enemy)) {
            retreat(budget, sink, memory, base, wave, state != 2);
            return true;
        }
        if (battle.fights()) {
            engage(view, budget, sink, memory, base, battle, wave, near);
        }
        return true;
    }

    private static void retreat(
            AiBudget budget, CommandSink sink, AiMemory memory, Base base, List<UnitView> wave, boolean first) {
        Vec2d home = new Vec2d(base.x(), base.z());
        List<Integer> going = new ArrayList<>();
        for (UnitView u : wave) {
            Vec2d t = u.target();
            if (t == null || DetMath.length(t.x() - home.x(), t.z() - home.z()) > SETTLED) {
                going.add(u.handle());
            }
        }
        memory.set(Slots.FIGHT, 2);
        memory.set(Slots.FOCUS, 0);
        if (first) {
            memory.set(Slots.RETREATS, memory.get(Slots.RETREATS) + 1);
        }
        if (!going.isEmpty()) {
            budget.spend(10);
            sink.send(Move.replace(
                    UnitSet.of(going.stream().mapToInt(Integer::intValue).toArray()), home));
        }
    }

    private static void engage(
            AiView view,
            AiBudget budget,
            CommandSink sink,
            AiMemory memory,
            Base base,
            StrategyScript.Battle battle,
            List<UnitView> wave,
            List<UnitView> near) {
        List<UnitView> melee = new ArrayList<>();
        List<UnitView> ranged = new ArrayList<>();
        double mx = 0;
        double mz = 0;
        for (UnitView u : wave) {
            if (view.role(u) == AiView.ROLE_ARCHER && battle.behind() > 0) {
                ranged.add(u);
            } else {
                melee.add(u);
                mx += u.x();
                mz += u.z();
            }
        }
        if (melee.isEmpty()) { // nothing to stand behind: the archers fight as the line
            melee.addAll(ranged);
            ranged.clear();
            for (UnitView u : melee) {
                mx += u.x();
                mz += u.z();
            }
        }
        mx /= melee.size();
        mz /= melee.size();
        @Nullable UnitView focus =
                battle.focus().equals(StrategyScript.Battle.OFF) ? null : pick(view, memory, battle, near, mx, mz);
        int target = focus == null ? 0 : focus.handle();
        memory.set(Slots.FOCUS, target == 0 ? 0 : target + 1L);
        // the melee line: one target, or back to the march when none stands in reach
        List<Integer> orders = new ArrayList<>();
        List<Integer> marching = new ArrayList<>();
        BuildingView centre = base.enemy();
        Vec2d front = front(base);
        for (UnitView u : melee) {
            int t = view.attackTarget(u);
            if (target != 0) {
                if (t != target) {
                    orders.add(u.handle());
                }
            } else if (near.isEmpty() && view.is(u.template(), "Infantry")) {
                if (t == 0) { // the way is clear: those that can burn a building go for the centre
                    orders.add(u.handle());
                }
            } else if (t == 0 && !arrived(u, front)) { // the others attack-move to its front and hold there
                marching.add(u.handle());
            }
        }
        if (!orders.isEmpty()) {
            int goal = target != 0 ? target : centre == null ? 0 : centre.handle();
            if (goal != 0) {
                budget.spend(10);
                sink.send(Attack.replace(
                        UnitSet.of(orders.stream().mapToInt(Integer::intValue).toArray()), goal));
            }
        }
        if (!marching.isEmpty()) {
            budget.spend(10);
            sink.send(AttackMove.replace(
                    UnitSet.of(marching.stream().mapToInt(Integer::intValue).toArray()), front));
        }
        if (ranged.isEmpty()) {
            return;
        }
        if (near.isEmpty()) { // nothing to stand behind a line from: the archers march on to the front with the rest
            List<Integer> marchers = new ArrayList<>();
            for (UnitView u : ranged) {
                Vec2d t = u.target();
                if (!arrived(u, front)
                        && (t == null || DetMath.length(t.x() - front.x(), t.z() - front.z()) > SETTLED)) {
                    marchers.add(u.handle());
                }
            }
            if (!marchers.isEmpty()) {
                budget.spend(10);
                sink.send(AttackMove.replace(
                        UnitSet.of(marchers.stream().mapToInt(Integer::intValue).toArray()), front));
            }
            return;
        }
        // the archers: a step behind the line, taking the target up when it is in their range
        double dx = base.x() - mx;
        double dz = base.z() - mz;
        double d = Math.max(1, DetMath.length(dx, dz));
        double ux = dx / d;
        double uz = dz / d;
        Vec2d hold = new Vec2d(mx + ux * battle.behind(), mz + uz * battle.behind());
        List<Integer> back = new ArrayList<>();
        List<Integer> shoot = new ArrayList<>();
        for (UnitView u : ranged) {
            boolean forward = (u.x() - hold.x()) * ux + (u.z() - hold.z()) * uz < -SLACK;
            Vec2d t = u.target();
            if (forward) {
                if (t == null || DetMath.length(t.x() - hold.x(), t.z() - hold.z()) > SETTLED) {
                    back.add(u.handle());
                }
            } else if (focus != null
                    && view.attackTarget(u) != target
                    && DetMath.length(focus.x() - u.x(), focus.z() - u.z()) <= SHOT) {
                shoot.add(u.handle());
            }
        }
        if (!back.isEmpty()) {
            budget.spend(10);
            sink.send(Move.replace(
                    UnitSet.of(back.stream().mapToInt(Integer::intValue).toArray()), hold));
        }
        if (!shoot.isEmpty()) {
            budget.spend(10);
            sink.send(Attack.replace(
                    UnitSet.of(shoot.stream().mapToInt(Integer::intValue).toArray()), target));
        }
    }

    /** Where a wave attack-moves to: {@value #ARROWS_FRONT} blocks from the enemy centre, toward our own. */
    static Vec2d front(Base base) {
        double dx = base.x() - base.enemyX();
        double dz = base.z() - base.enemyZ();
        double d = Math.max(1, DetMath.length(dx, dz));
        return new Vec2d(base.enemyX() + dx / d * ARROWS_FRONT, base.enemyZ() + dz / d * ARROWS_FRONT);
    }

    private static boolean arrived(UnitView u, Vec2d front) {
        return DetMath.length(u.x() - front.x(), u.z() - front.z()) <= 2 * ARROWS_FRONT;
    }

    /** The target kept from the last think if it still stands in reach, else the policy's pick among {@code near}. */
    private static @Nullable UnitView pick(
            AiView view, AiMemory memory, StrategyScript.Battle battle, List<UnitView> near, double mx, double mz) {
        int kept = (int) memory.get(Slots.FOCUS) - 1;
        UnitView best = null;
        boolean value = battle.focus().equals(StrategyScript.Battle.VALUE);
        for (UnitView e : near) {
            if (e.handle() == kept) {
                return e;
            }
            if (best == null || better(view, value, e, best, mx, mz)) {
                best = e;
            }
        }
        return best;
    }

    /** Whether {@code a} beats {@code b}: the lower health fraction, or the higher worth; then the nearer; then the handle. */
    private static boolean better(AiView view, boolean value, UnitView a, UnitView b, double mx, double mz) {
        double ha = view.health(a);
        double hb = view.health(b);
        double wa = view.worth(a);
        double wb = view.worth(b);
        if (value ? wa != wb : ha != hb) {
            return value ? wa > wb : ha < hb;
        }
        if (value && ha != hb) {
            return ha < hb;
        }
        double da = DetMath.length(a.x() - mx, a.z() - mz);
        double db = DetMath.length(b.x() - mx, b.z() - mz);
        return da != db ? da < db : a.handle() < b.handle();
    }
}
