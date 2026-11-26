package dev.strategos.core.sim.ability;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.UnitState;
import java.util.List;

/**
 * The abilities' turn in the {@code UNIT_AI} phase, before the units think (UNIT-10): the tick boundary first, where
 * cooldowns that have run out are dropped and the timed modifiers whose tick has come expire (so an effect granted at
 * tick T for N ticks is gone from tick T + N on); then autocast. An idle unit with an {@code autocast} ability tries it
 * every {@value #AUTOCAST_PERIOD} ticks, at its own offset by handle index, in index order, each of its autocast abilities
 * in id order: the ability's picker names a target and, if there is one, the ability fires as the unit's machine would fire it. Only idle units cast by
 * themselves: a unit with orders is doing what it was told. Allocation-free while nothing is on cooldown or timed.
 */
public final class AbilitySystem implements MatchSystem {
    /** Ticks between a unit's autocast attempts. */
    public static final int AUTOCAST_PERIOD = 10;

    @Override
    public Phase phase() {
        return Phase.UNIT_AI;
    }

    @Override
    public void run(MatchState s) {
        long start = s.systemTimes().start();
        step(s);
        s.systemTimes().ability(start); // PERF-17
    }

    private static void step(MatchState s) {
        AbilityStore store = s.play().abilities();
        if (store.takeMoved() && !s.spatial().gridMatches(s.motion())) {
            s.spatial().rebuild(s); // units put elsewhere by an ability before this phase, once however many
        }
        if (!store.empty()) {
            store.sweep(s.tick(), s.stats(), s.entities());
        }
        if (s.templates().hasAutocast()) {
            autocast(s);
        }
    }

    private static void autocast(MatchState s) {
        EntityStore entities = s.entities();
        long tick = s.tick();
        for (int i = 0; i < entities.highWater(); i++) {
            if ((tick + i) % AUTOCAST_PERIOD != 0
                    || !entities.aliveAt(i)
                    || s.garrisoned(i)
                    || s.ai().state(i) != UnitState.IDLE
                    || s.orders().size(i) != 0) {
                continue;
            }
            List<AbilityDef> own = s.templates().abilitiesOf(s.identity().templateIndex(i));
            for (int k = 0; k < own.size(); k++) {
                AbilityDef def = own.get(k);
                if (def.autocast()) {
                    tryCast(s, entities.handleAt(i), def);
                }
            }
        }
    }

    private static boolean tryCast(MatchState s, int handle, AbilityDef def) {
        if (s.play().abilities().readyAt(handle, def.id()) > s.tick()) {
            return false;
        }
        int target = AbilityTargets.pick(def.pick(), s, handle, def);
        if (target == Handle.NONE) {
            return false;
        }
        int t = Handle.index(target);
        int unit = switch (def.aim()) {
            case UNIT, AREA -> target;
            case NONE, POINT -> Handle.NONE;
        };
        return AbilityRules.fire(
                        s, handle, def, unit, s.motion().x(t), s.motion().z(t))
                == null;
    }
}
