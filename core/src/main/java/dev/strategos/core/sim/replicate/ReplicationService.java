package dev.strategos.core.sim.replicate;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatBreakdown;
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

    /**
     * The buildings {@code viewer} knows of (BLD-01): every building until fog (M6); its own with the owner's fields,
     * which stay empty until production (PROD-01) and rally points (PROD-03) fill them; null {@code viewer}, an
     * observer, sees every building as no one's own.
     */
    public static BuildingState buildingState(MatchQueries q, int matchId, @Nullable FactionSlot viewer) {
        List<BuildingState.Entry> out = new ArrayList<>();
        for (BuildingView b : q.buildings()) {
            boolean own = viewer != null && viewer.value() == b.faction();
            int template = q.templates().byId(b.template()) == null
                    ? -1
                    : java.util.Objects.requireNonNull(q.templates().byId(b.template()))
                            .netIndex();
            out.add(new BuildingState.Entry(
                    b.handle(),
                    template,
                    b.faction(),
                    b.footprint().anchorX(),
                    b.footprint().anchorZ(),
                    b.footprint().rotation(),
                    b.baseY(),
                    b.state(),
                    (int) b.progress(),
                    UnitHud.Entry.health(b.hitPoints(), b.maxHitPoints()),
                    own,
                    List.of(),
                    false,
                    0,
                    0));
        }
        return new BuildingState(matchId, q.tick(), out);
    }

    /**
     * Faction {@code faction}'s {@code faction_state} (ECO-01): its treasury's stock, gathered and spent per resource;
     * population and workers are 0 until ECO-06 and ECO-05 count them.
     */
    public static FactionState factionState(MatchQueries q, int matchId, FactionSlot faction) {
        FactionTreasury t = q.treasury(faction);
        int n = t.stock().size();
        List<Integer> workers = new ArrayList<>(n);
        for (int r = 0; r < n; r++) {
            workers.add(0);
        }
        return new FactionState(
                matchId, faction.value(), q.tick(), t.stock(), t.gathered(), t.spent(), 0, 0, 0, workers);
    }

    /**
     * The details a {@code ui_query} asked for (NET-08.4): the viewer's own live units among {@code handles}, in their
     * order; another faction's unit, or a dead one, is left out.
     */
    public static UnitDetail detail(MatchQueries q, int matchId, FactionSlot viewer, List<Integer> handles) {
        List<UnitDetail.Unit> units = new ArrayList<>();
        for (int handle : handles) {
            UnitView unit = q.unit(handle);
            if (unit == null || !unit.faction().equals(viewer) || units.size() == UiQuery.MAX_HANDLES) {
                continue;
            }
            List<UnitDetail.Order> orders = new ArrayList<>();
            for (OrderQueues.Order o : q.orders(handle)) {
                orders.add(new UnitDetail.Order(o.kind(), (float) o.x(), (float) o.z()));
            }
            StatBreakdown speed = q.stats(handle, CorePaths.WALK_SPEED);
            units.add(new UnitDetail.Unit(
                    handle,
                    unit.templateIndex(),
                    stateCode(unit.state()),
                    (float) q.hitPoints(handle),
                    (float) q.maxHitPoints(handle),
                    speed == null ? 0 : (float) speed.result(),
                    orders));
        }
        return new UnitDetail(matchId, q.tick(), units);
    }

    /** A state's code in a summary entry: IDLE 0, WALKING 1, COMBAT 2, GATHERING 3. */
    public static int stateCode(UnitState state) {
        return switch (state) {
            case IDLE -> 0;
            case WALKING -> 1;
            case COMBAT -> 2;
            case GATHERING -> 3;
        };
    }
}
