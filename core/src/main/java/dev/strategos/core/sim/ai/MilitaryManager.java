package dev.strategos.core.sim.ai;

import dev.strategos.core.protocol.AnnounceWave;
import dev.strategos.core.protocol.Attack;
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
        List<Integer> army = new ArrayList<>();
        for (UnitView u : view.units()) {
            if (u.state() != UnitState.GARRISONED && view.is(u.template(), "Infantry")) { // those inside cannot muster
                army.add(u.handle());
            }
        }
        int[] handles = army.stream().mapToInt(Integer::intValue).toArray();
        long mustered = memory.get(Slots.MUSTER);
        if (mustered == 0) {
            long due = 20L * (a.first() + memory.get(Slots.WAVE) * a.every());
            if (tick < due || army.size() < a.min()) {
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
            sink.send(Attack.replace(
                    UnitSet.of(handles),
                    java.util.Objects.requireNonNull(base.enemy()).handle()));
            memory.set(Slots.WAVE, memory.get(Slots.WAVE) + 1);
            memory.set(Slots.MUSTER, 0);
        }
    }
}
