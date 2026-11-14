package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DefenceLog;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The base's defence (AI-04, 04 §14): a computer player attacked (a blow on a unit or building of the last
 * {@value #ATTACK} ticks, or an enemy soldier within {@value #NEAR} blocks of its centre) commits every soldier within
 * {@value #REACH} blocks of the centre that is not wounded: at the enemy army if any stands near, else at its workers,
 * else at its buildings (army, economy, civic centre). Soldiers under {@value #WOUNDED_PERCENT} per cent health
 * walk back to the centre instead. When two distinct workers are hit within {@value #BELL_WINDOW} ticks the bell is
 * rung at the civic centre (BLD-16); {@value #QUIET} ticks with no enemy within {@value #NEAR} blocks end it with the all
 * clear. The think after an attack runs at once ({@code AiSlots.wake}), so the commit follows within the cadence.
 */
final class DefenceManager implements AiModule {
    /** Ticks a blow counts as an attack going on: 10 seconds, the budget AI-04.2 gives a defence. */
    static final long ATTACK = 200;

    /** Blocks from the centre inside which an enemy is at the door, and the all clear waits on. */
    static final double NEAR = 32;

    /** Soldiers farther than this from the centre are out on a wave, not at home. */
    static final double REACH = 64;

    /** Ticks inside which two hit workers ring the bell: 5 seconds. */
    static final long BELL_WINDOW = 100;

    /** Ticks without an enemy near that end the bell: 20 seconds. */
    static final long QUIET = 400;

    /** Health under this share of the most sends a soldier home. */
    static final double WOUNDED = 0.30;

    static final int WOUNDED_PERCENT = 30;

    /** A blow on one of its own at home: where, when, and whether on a worker. */
    private record Hit(int handle, long tick, double x, double z, boolean worker) {}

    private final AiMemory memory;

    DefenceManager(AiMemory memory) {
        this.memory = memory;
    }

    /** Whether a defence is going on: a blow or an enemy soldier at home within {@value #ATTACK} ticks. */
    static boolean defending(AiMemory memory, long tick) {
        long threat = memory.get(Slots.THREAT);
        return threat != 0 && tick - (threat - 1) <= ATTACK;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        Base base = Base.of(view);
        if (base == null || budget.exhausted()) {
            return;
        }
        long tick = view.tick();
        List<Hit> blows = home(view, base);
        List<UnitView> enemies = new ArrayList<>();
        boolean soldierNear = false;
        for (UnitView e : view.enemyUnits()) {
            if (DetMath.length(e.x() - base.x(), e.z() - base.z()) <= NEAR) {
                enemies.add(e);
                soldierNear |= view.role(e) != AiView.ROLE_NONE && e.state() != UnitState.GARRISONED;
            }
        }
        if (!enemies.isEmpty()) {
            memory.set(Slots.NEAR, tick + 1);
        }
        boolean hit = false;
        for (Hit b : blows) {
            hit |= tick - b.tick() <= ATTACK;
        }
        if (hit || soldierNear) {
            memory.set(Slots.THREAT, tick + 1);
            defend(view, budget, sink, base, blows, enemies);
            bell(budget, sink, base, blows, tick);
        }
        allClear(budget, sink, base, enemies, tick);
    }

    /** Commits the soldiers and sends the wounded home. */
    private void defend(
            AiView view, AiBudget budget, CommandSink sink, Base base, List<Hit> blows, List<UnitView> enemies) {
        Vec2d centre = new Vec2d(base.x(), base.z());
        Vec2d at = scene(base, blows);
        List<Integer> go = new ArrayList<>();
        List<Integer> home = new ArrayList<>();
        for (UnitView u : view.units()) {
            if (view.role(u) == AiView.ROLE_NONE
                    || u.state() == UnitState.GARRISONED
                    || DetMath.length(u.x() - base.x(), u.z() - base.z()) > REACH) {
                continue;
            }
            Vec2d t = u.target();
            if (view.health(u) < WOUNDED) {
                if (DetMath.length(u.x() - centre.x(), u.z() - centre.z()) > 6
                        && (t == null || DetMath.length(t.x() - centre.x(), t.z() - centre.z()) > 6)) {
                    home.add(u.handle()); // already walking home or there: left alone
                }
            } else if (view.attackTarget(u) == 0 && (t == null || DetMath.length(t.x() - at.x(), t.z() - at.z()) > 8)) {
                go.add(u.handle());
            }
        }
        if (!home.isEmpty() && budget.spend(10)) {
            sink.send(Move.replace(
                    UnitSet.of(home.stream().mapToInt(Integer::intValue).toArray()), centre));
        }
        if (go.isEmpty() || !budget.spend(10)) {
            return;
        }
        UnitSet set = UnitSet.of(go.stream().mapToInt(Integer::intValue).toArray());
        UnitView target = nearest(view, enemies, at, true);
        if (target != null) { // the army in range: attack-move, so each soldier takes the nearest of it
            sink.send(AttackMove.replace(set, new Vec2d(target.x(), target.z())));
            return;
        }
        target = nearest(view, enemies, at, false);
        if (target != null) { // then the economy
            sink.send(Attack.replace(set, target.handle()));
            return;
        }
        BuildingView civic = null; // then the buildings, the civic centre first
        double best = Double.POSITIVE_INFINITY;
        for (BuildingView b : view.enemyBuildings()) {
            double d = DetMath.length(
                    b.footprint().anchorX() - base.x(), b.footprint().anchorZ() - base.z());
            if (d <= NEAR + 16 && (view.is(b.template(), Base.CENTRE) ? d - 1_000 : d) < best) {
                best = view.is(b.template(), Base.CENTRE) ? d - 1_000 : d;
                civic = b;
            }
        }
        if (civic != null) {
            sink.send(Attack.replace(set, civic.handle()));
        } else {
            sink.send(AttackMove.replace(set, at));
        }
    }

    /** The blows of the log that fell on its own within {@value #REACH} blocks of the centre; the fights of a wave far away are not the base's. */
    private static List<Hit> home(AiView view, Base base) {
        List<DefenceLog.Blow> log = view.blows();
        List<Hit> out = new ArrayList<>();
        if (log.isEmpty()) {
            return out;
        }
        for (DefenceLog.Blow b : log) {
            Hit hit = null;
            for (UnitView u : view.units()) {
                if (u.handle() == b.handle()) {
                    hit = new Hit(b.handle(), b.tick(), u.x(), u.z(), view.role(u) == AiView.ROLE_NONE);
                    break;
                }
            }
            if (hit == null) {
                for (BuildingView bv : view.buildings()) {
                    if (bv.handle() == b.handle()) {
                        hit = new Hit(
                                b.handle(),
                                b.tick(),
                                bv.footprint().anchorX() + bv.footprint().spanX() / 2.0,
                                bv.footprint().anchorZ() + bv.footprint().spanZ() / 2.0,
                                false);
                        break;
                    }
                }
            }
            if (hit != null && DetMath.length(hit.x() - base.x(), hit.z() - base.z()) <= REACH) {
                out.add(hit);
            }
        }
        return out;
    }

    /** Where the fight is: the newest blow's place, else the centre. */
    private static Vec2d scene(Base base, List<Hit> blows) {
        Hit newest = null;
        for (Hit b : blows) {
            if (newest == null
                    || b.tick() > newest.tick()
                    || (b.tick() == newest.tick() && b.handle() < newest.handle())) {
                newest = b;
            }
        }
        return newest == null ? new Vec2d(base.x(), base.z()) : new Vec2d(newest.x(), newest.z());
    }

    /** The enemy nearest {@code at}: a soldier when {@code army}, else one that is none, ties by handle. */
    private static @Nullable UnitView nearest(AiView view, List<UnitView> enemies, Vec2d at, boolean army) {
        UnitView best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (UnitView e : enemies) {
            if ((view.role(e) != AiView.ROLE_NONE) != army || e.state() == UnitState.GARRISONED) {
                continue;
            }
            double d = DetMath.length(e.x() - at.x(), e.z() - at.z());
            if (d < bestD || (d == bestD && best != null && e.handle() < best.handle())) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Rings the bell when two workers were hit within the window and it is not already rung. */
    private void bell(AiBudget budget, CommandSink sink, Base base, List<Hit> blows, long tick) {
        if (memory.get(Slots.BELL) != 0) {
            return;
        }
        int hit = 0;
        for (Hit b : blows) {
            if (b.worker() && tick - b.tick() <= BELL_WINDOW) {
                hit++;
            }
        }
        if (hit >= 2 && budget.spend(10)) {
            sink.send(new RingBell(base.centre().handle()));
            memory.set(Slots.BELL, tick + 1);
        }
    }

    /** Ends the bell {@value #QUIET} ticks after the last enemy near. */
    private void allClear(AiBudget budget, CommandSink sink, Base base, List<UnitView> near, long tick) {
        long bell = memory.get(Slots.BELL);
        if (bell == 0 || !near.isEmpty()) {
            return;
        }
        long last = Math.max(bell, memory.get(Slots.NEAR));
        if (tick - (last - 1) >= QUIET && budget.spend(10)) {
            sink.send(new AllClear(base.centre().handle()));
            memory.set(Slots.BELL, 0);
        }
    }
}
