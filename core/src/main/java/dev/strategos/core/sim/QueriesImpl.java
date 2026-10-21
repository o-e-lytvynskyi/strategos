package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/** {@link MatchQueries} over a match's state. */
final class QueriesImpl implements MatchQueries {
    private final MatchState state;

    QueriesImpl(MatchState state) {
        this.state = state;
    }

    @Override
    public long tick() {
        return state.tick();
    }

    @Override
    public MatchConfig config() {
        return state.config();
    }

    @Override
    public TemplateSet templates() {
        return state.templates();
    }

    @Override
    public int unitCount() {
        return state.entities().aliveCount();
    }

    @Override
    public boolean isAlive(int handle) {
        return state.entities().isAlive(handle);
    }

    @Override
    public @Nullable UnitView unit(int handle) {
        if (!state.entities().isAlive(handle)) {
            return null;
        }
        int i = Handle.index(handle);
        int templateIndex = state.identity().templateIndex(i);
        OrderStore orders = state.orders();
        MotionStore motion = state.motion();
        return new UnitView(
                handle,
                state.templates().byIndex(templateIndex).id(),
                templateIndex,
                new FactionSlot(state.identity().faction(i)),
                motion.x(i),
                motion.y(i),
                motion.z(i),
                motion.yaw(i),
                state.ai().state(i),
                orders.kind(i) == OrderStore.MOVE ? new Vec2d(orders.targetX(i), orders.targetZ(i)) : null);
    }

    @Override
    public int[] unitHandles() {
        return handles(-1);
    }

    @Override
    public int[] unitHandlesOf(FactionSlot faction) {
        return handles(faction.value());
    }

    /** The live handles, of one faction or of all when {@code faction} is -1, sorted. */
    private int[] handles(int faction) {
        EntityStore entities = state.entities();
        int[] handles = new int[entities.aliveCount()];
        int n = 0;
        for (int i = 0; i < entities.highWater(); i++) {
            if (entities.aliveAt(i) && (faction < 0 || state.identity().faction(i) == faction)) {
                handles[n++] = entities.handleAt(i);
            }
        }
        int[] result = Arrays.copyOf(handles, n);
        Arrays.sort(result);
        return result;
    }
}
