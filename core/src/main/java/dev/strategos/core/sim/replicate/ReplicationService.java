package dev.strategos.core.sim.replicate;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.DepositState;
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
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.Deposits;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
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
                    own ? b.queue() : List.of(),
                    own && b.rallyKind() != BuildingStore.RALLY_NONE,
                    own ? (int) Math.floor(b.rallyX()) : 0,
                    own ? (int) Math.floor(b.rallyZ()) : 0,
                    own && !b.queue().isEmpty() ? (int) b.trained() : 0,
                    own && b.fieldAmount() >= 0 ? (int) Math.min(Integer.MAX_VALUE - 1, b.fieldAmount()) : -1,
                    own && b.fieldAmount() >= 0 ? (int) Math.min(Integer.MAX_VALUE, b.fieldRipening()) : 0,
                    own && b.autoReseed(),
                    own ? b.batches() : List.of()));
        }
        return new BuildingState(matchId, q.tick(), out);
    }

    /**
     * The match's deposits (ECO-08), the same for every viewer: each one's corner, resource and what it has left of its
     * most, in whole units rounded up.
     */
    public static DepositState depositState(MatchQueries q, int matchId) {
        List<DepositState.Entry> out = new ArrayList<>();
        for (NodeView n : q.nodes()) {
            Blueprint b = n.template() < 0 ? null : q.templates().blueprint(n.template());
            if (n.kind() != MaterialKind.DEPOSIT || b == null || out.size() == DepositState.MAX_DEPOSITS) {
                continue;
            }
            Footprint at = Deposits.footprint(b, n.anchorX(), n.anchorZ());
            int most = (int) Math.min(Integer.MAX_VALUE, (n.most() + 999) / 1000);
            out.add(new DepositState.Entry(
                    n.id(),
                    n.template(),
                    at.anchorX(),
                    at.anchorZ(),
                    Math.max(0, q.config().rules().economy().indexOf(n.resource())),
                    (int) Math.min(most, (n.amount() + 999) / 1000),
                    most));
        }
        return new DepositState(matchId, q.tick(), out);
    }

    /**
     * Faction {@code faction}'s {@code faction_state} (ECO-01): its treasury's stock, gathered and spent per resource,
     * its population and cap (ECO-06), its idle workers and its gatherers per resource (ECO-05).
     */
    public static FactionState factionState(MatchQueries q, int matchId, FactionSlot faction) {
        FactionTreasury t = q.treasury(faction);
        int n = t.stock().size();
        int[] counted = q.workers(faction);
        List<Integer> workers = new ArrayList<>(n);
        for (int r = 0; r < n; r++) {
            workers.add(r < counted.length - 1 ? counted[r] : 0);
        }
        return new FactionState(
                matchId,
                faction.value(),
                q.tick(),
                t.stock(),
                t.gathered(),
                t.spent(),
                q.populationUsed(faction),
                q.populationCap(faction),
                counted[counted.length - 1],
                workers);
    }

    /**
     * The details a {@code ui_query} asked for (NET-08.4): the viewer's own live units among {@code handles}, in their
     * order; another faction's unit, or a dead one, is left out. An observer (no viewer) gets any faction's.
     */
    public static UnitDetail detail(MatchQueries q, int matchId, @Nullable FactionSlot viewer, List<Integer> handles) {
        List<UnitDetail.Unit> units = new ArrayList<>();
        for (int handle : handles) {
            UnitView unit = q.unit(handle);
            if (unit == null
                    || (viewer != null && !unit.faction().equals(viewer))
                    || units.size() == UiQuery.MAX_HANDLES) {
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
                    orders,
                    q.carriedResource(handle),
                    q.carriedAmount(handle)));
        }
        return new UnitDetail(matchId, q.tick(), units);
    }

    /** A state's code in a summary entry: IDLE 0, WALKING 1, COMBAT 2, GATHERING 3, BUILDING 4, GARRISONED 5. */
    public static int stateCode(UnitState state) {
        return switch (state) {
            case IDLE -> 0;
            case WALKING -> 1;
            case COMBAT -> 2;
            case GATHERING -> 3;
            case BUILDING -> 4;
            case GARRISONED -> 5;
        };
    }
}
