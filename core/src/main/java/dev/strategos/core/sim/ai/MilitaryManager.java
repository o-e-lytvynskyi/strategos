package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.AnnounceWave;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import java.util.ArrayList;
import java.util.List;

/**
 * Attacks in announced waves (AI-17, 04 §14's MilitaryManager): when a wave is due by the schedule and enough
 * spearmen stand, they muster {@code muster} blocks from the civic centre toward the enemy's, and the wave is
 * announced to the other players; once its warning has run, every spearman goes to burn the enemy's civic centre.
 * Waves repeat on the schedule until the match is won.
 */
final class MilitaryManager implements AiModule {
    private final StrategyScript script;
    private final AiMemory memory;

    MilitaryManager(StrategyScript script, AiMemory memory) {
        this.script = script;
        this.memory = memory;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        Base base = Base.of(view);
        if (base == null || base.enemy() == null || budget.exhausted()) {
            return;
        }
        StrategyScript.Attack a = script.attack();
        long tick = view.tick();
        StrategyScript.Battle battle = script.battle();
        List<Integer> army = new ArrayList<>();
        List<UnitView> soldiers = new ArrayList<>();
        for (UnitView u : view.units()) {
            // those inside cannot muster; with battle tactics on, the horsemen march too (AI-03)
            if (u.state() != UnitState.GARRISONED
                    && (battle.enabled() ? view.role(u) != AiView.ROLE_NONE : view.is(u.template(), "Infantry"))) {
                army.add(u.handle());
                soldiers.add(u);
            }
        }
        if (battle.spears() > 0 && reserve(view, budget, sink, memory, base, battle, army, soldiers)) {
            return; // the way is clear: the spearmen are at its centre
        }
        if (memory.get(Slots.FIGHT) != 0 && Tactics.fight(view, budget, sink, memory, base, script, soldiers)) {
            return; // a wave is out: its fight is this think's work
        }
        int[] handles = army.stream().mapToInt(Integer::intValue).toArray();
        long mustered = memory.get(Slots.MUSTER);
        if (mustered == 0) {
            long due = 20L * (a.first() + memory.get(Slots.WAVE) * a.every());
            if (tick < due || army.size() < a.min() || !strongEnough(view, battle, soldiers)) {
                return;
            }
            double dx = base.enemyX() - base.x();
            double dz = base.enemyZ() - base.z();
            double d = Math.max(1, DetMath.length(dx, dz));
            int mx = (int) Math.floor(base.x() + dx / d * a.muster());
            int mz = (int) Math.floor(base.z() + dz / d * a.muster());
            budget.spend(20);
            sink.send(Move.replace(UnitSet.of(handles), new Vec2d(mx + 0.5, mz + 0.5)));
            sink.send(new AnnounceWave(mx, mz, 20 * a.announce()));
            memory.set(Slots.MUSTER, tick + 1);
            return;
        }
        if (tick - (mustered - 1) >= 20L * a.announce() && handles.length > 0) {
            budget.spend(10);
            if (battle.fights()) { // AI-03: attack-move, so every kind engages the units on the way
                sink.send(AttackMove.replace(UnitSet.of(handles), Tactics.front(base)));
            } else {
                sink.send(Attack.replace(
                        UnitSet.of(handles),
                        java.util.Objects.requireNonNull(base.enemy()).handle()));
            }
            memory.set(Slots.WAVE, memory.get(Slots.WAVE) + 1);
            memory.set(Slots.MUSTER, 0);
            if (battle.fights()) {
                memory.set(Slots.FIGHT, 1);
                memory.set(Slots.FOCUS, 0);
            }
        }
    }

    /**
     * The reserve of spearmen (AI-03's {@code spears}): the {@code spears} oldest stay home out of {@code army} and
     * {@code soldiers}, so no wave takes them. Once the way is clear (the wave's last fight think saw it at the enemy centre with no enemy soldier
     * near it, or the enemy fields no army that is not garrisoned), every spearman
     * without a target is sent at the enemy's civic centre, the reserve with them; true when it was, and the think is
     * done.
     */
    private static boolean reserve(
            AiView view,
            AiBudget budget,
            CommandSink sink,
            AiMemory memory,
            Base base,
            StrategyScript.Battle battle,
            List<Integer> army,
            List<UnitView> soldiers) {
        boolean clear = memory.get(Slots.CLEAR) != 0;
        if (!clear) { // or the enemy fields no army at all
            clear = true;
            for (UnitView e : view.enemyUnits()) {
                if (view.role(e) != AiView.ROLE_NONE && e.state() != UnitState.GARRISONED) {
                    clear = false;
                    break;
                }
            }
        }
        List<UnitView> spears = new ArrayList<>();
        for (UnitView u : soldiers) {
            if (view.role(u) == AiView.ROLE_SPEAR) {
                spears.add(u);
            }
        }
        if (clear) {
            List<Integer> going = new ArrayList<>();
            for (UnitView u : spears) {
                if (view.attackTarget(u) == 0) {
                    going.add(u.handle());
                }
            }
            if (going.isEmpty() || base.enemy() == null) {
                return false;
            }
            budget.spend(10);
            sink.send(Attack.replace(
                    UnitSet.of(going.stream().mapToInt(Integer::intValue).toArray()),
                    base.enemy().handle()));
            return true;
        }
        spears.sort(java.util.Comparator.comparingInt(UnitView::handle));
        List<Integer> home = new ArrayList<>();
        Vec2d centre = new Vec2d(base.x(), base.z());
        for (UnitView u : spears.subList(0, Math.min(battle.spears(), spears.size()))) {
            army.remove(Integer.valueOf(u.handle()));
            soldiers.remove(u);
            Vec2d t = u.target();
            if (t == null
                    ? DetMath.length(u.x() - centre.x(), u.z() - centre.z()) > Tactics.HOME_RESERVE
                    : DetMath.length(t.x() - centre.x(), t.z() - centre.z()) > Tactics.HOME_RESERVE) {
                home.add(u.handle());
            }
        }
        if (!home.isEmpty()) {
            budget.spend(10);
            sink.send(Move.replace(
                    UnitSet.of(home.stream().mapToInt(Integer::intValue).toArray()), centre));
        }
        return false;
    }

    /**
     * AI-03's launch gate: the wave leaves when its strength is at least {@code launch} times the enemy army's and its
     * civic centre's defence together; a script without the knob always passes.
     */
    private static boolean strongEnough(AiView view, StrategyScript.Battle battle, List<UnitView> own) {
        if (battle.launch() <= 0) {
            return true;
        }
        double mine = 0;
        for (UnitView u : own) {
            mine += view.worth(u) * view.health(u);
        }
        double theirs = battle.defence();
        for (UnitView e : view.enemyUnits()) {
            if (view.role(e) != AiView.ROLE_NONE && e.state() != UnitState.GARRISONED) {
                theirs += view.worth(e) * view.health(e);
            }
        }
        return mine >= battle.launch() * theirs;
    }
}
