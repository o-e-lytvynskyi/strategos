package dev.strategos.core.sim.replicate;

import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Builds a faction's tier-2 frame (NET-08, 04 §12.4) from the core's queries after a step: its own units, and enemy
 * units only while {@link Visibility} says it sees them; a null viewer is an observer and gets every unit. Plain Java
 * over the queries, so a frame's fog properties are checked in L1, and nothing it does reaches the hashed state.
 */
@CoreApi
public final class ReplicationService {
    private ReplicationService() {}

    public static UnitSummary summary(
            MatchQueries q, int matchId, @Nullable FactionSlot viewer, Visibility visibility) {
        List<UnitSummary.Entry> entries = new ArrayList<>();
        for (int handle : q.unitHandles()) {
            UnitView unit = q.unit(handle);
            if (unit == null) {
                continue;
            }
            boolean own = viewer != null && unit.faction().equals(viewer);
            if (viewer == null || own || visibility.sees(viewer, unit)) {
                entries.add(UnitSummary.Entry.at(
                        Handle.index(handle),
                        unit.faction().value(),
                        unit.templateIndex(),
                        stateCode(unit.state()),
                        unit.x(),
                        unit.z()));
                if (entries.size() == UnitSummary.MAX_UNITS) {
                    break;
                }
            }
        }
        return new UnitSummary(matchId, q.tick(), entries);
    }

    /** A state's code in a summary entry: IDLE 0, WALKING 1. */
    public static int stateCode(UnitState state) {
        return switch (state) {
            case IDLE -> 0;
            case WALKING -> 1;
        };
    }
}
