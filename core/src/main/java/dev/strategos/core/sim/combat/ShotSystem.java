package dev.strategos.core.sim.combat;

import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.spatial.UnitFilter;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

/** Landing-point impacts: preferred full target handle first, otherwise the lowest-index live footprint. */
public final class ShotSystem implements MatchSystem {
    private final ShotStore store;
    private final IntConsumer candidate = this::candidate;
    private @Nullable MatchState queryState;
    private ShotStore.@Nullable Shot queryShot;
    private int selected;

    public ShotSystem(ShotStore store) {
        this.store = store;
    }

    @Override
    public Phase phase() {
        return Phase.COMBAT;
    }

    @Override
    public void run(MatchState state) {
        List<ShotStore.Shot> due = store.takeDue(state.tick());
        if (due.isEmpty()) return;
        double radius = 0;
        for (UnitTemplate template : state.templates().all()) {
            if (!template.isBuilding()) radius = Math.max(radius, template.radius());
        }
        queryState = state;
        for (ShotStore.Shot shot : due) {
            selected = Handle.NONE;
            if (state.entities().isAlive(shot.target()) && contains(state, Handle.index(shot.target()), shot)) {
                selected = shot.target();
            } else {
                queryShot = shot;
                state.spatial().forEachInRadius(shot.landingX(), shot.landingZ(), radius, UnitFilter.ANY, candidate);
            }
            if (selected != Handle.NONE) {
                Damage d = shot.damage();
                state.queueDamage(new Damage(
                        selected, d.attacker(), d.hack(), d.pierce(), d.crush(), d.fire(), d.bonuses(), d.lethal()));
            }
        }
        queryShot = null;
        queryState = null;
    }

    private void candidate(int index) {
        MatchState state = Objects.requireNonNull(queryState);
        if (selected == Handle.NONE
                && state.entities().aliveAt(index)
                && contains(state, index, Objects.requireNonNull(queryShot))) {
            selected = state.entities().handleAt(index);
        }
    }

    private static boolean contains(MatchState state, int index, ShotStore.Shot shot) {
        UnitTemplate template = state.templates().byIndex(state.identity().templateIndex(index));
        double dx = shot.landingX() - state.motion().x(index);
        double dz = shot.landingZ() - state.motion().z(index);
        double y = state.motion().y(index);
        return dx * dx + dz * dz <= template.radius() * template.radius()
                && shot.landingY() >= y
                && shot.landingY() <= y + template.height();
    }
}
