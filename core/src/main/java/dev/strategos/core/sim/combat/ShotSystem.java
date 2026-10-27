package dev.strategos.core.sim.combat;

import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MatchSystem;
import dev.strategos.core.sim.Phase;
import dev.strategos.core.sim.ShotRequested;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.spatial.UnitFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import org.jspecify.annotations.Nullable;

/** Landing-point impacts: preferred full target handle first, otherwise the lowest-index live footprint. */
public final class ShotSystem implements MatchSystem {
    private final ShotStore store;
    /** Pre-MOVEMENT target position is transient: flat matches keep pose-only persisted motion and legacy hashes. */
    private record Pending(ShotRequested request, double targetX, double targetZ) {}

    private final ArrayList<Pending> pending = new ArrayList<>();
    private final IntConsumer candidate = this::candidate;
    private @Nullable MatchState queryState;
    private ShotStore.@Nullable Shot queryShot;
    private int selected;

    public ShotSystem(ShotStore store) {
        this.store = store;
    }

    /** The match kernel's shot system listens to UNIT_AI intents and freezes them in COMBAT. */
    public ShotSystem(MatchState state) {
        this(state.shots());
        state.bus().subscribe(ShotRequested.class, request -> {
            int target = request.target();
            int index = Handle.index(target);
            pending.add(new Pending(
                    request,
                    state.entities().isAlive(target) ? state.motion().x(index) : 0,
                    state.entities().isAlive(target) ? state.motion().z(index) : 0));
        });
    }

    @Override
    public Phase phase() {
        return Phase.COMBAT;
    }

    @Override
    public void run(MatchState state) {
        release(state);
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

    private void release(MatchState state) {
        if (pending.isEmpty()) return;
        for (int i = 0; i < pending.size(); i++) {
            Pending row = pending.get(i);
            ShotRequested request = row.request();
            if (!state.entities().isAlive(request.shooter())
                    || !state.entities().isAlive(request.target())) {
                continue;
            }
            int shooter = Handle.index(request.shooter());
            int target = Handle.index(request.target());
            if (Engagement.gap(state, shooter, target) > request.range()) continue;
            double vx = state.surfaces().version() == 0
                    ? state.motion().x(target) - row.targetX()
                    : state.motion().vx(target);
            double vz = state.surfaces().version() == 0
                    ? state.motion().z(target) - row.targetZ()
                    : state.motion().vz(target);
            UnitTemplate body = state.templates().byIndex(state.identity().templateIndex(target));
            store.launch(
                    state.config().seed(),
                    state.tick(),
                    new ShotStore.Launch(
                            state.motion().x(shooter),
                            state.motion().z(shooter),
                            state.motion().x(target),
                            state.motion().y(target) + body.height() / 2,
                            state.motion().z(target),
                            vx,
                            vz,
                            request.speed(),
                            request.spread(),
                            request.damage()));
            state.outputs().add(new UnitStruck(request.shooter(), request.target()));
        }
        pending.clear(); // every intent belongs to this tick; FINAL sees none
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
