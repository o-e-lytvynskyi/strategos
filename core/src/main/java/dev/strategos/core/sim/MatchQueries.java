package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatBreakdown;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Read-only questions about a match at its last completed tick. */
@CoreApi
public interface MatchQueries {
    /** The match's resource nodes, by id (ECO-02); empty before discovery. */
    List<NodeView> nodes();

    /** Faction {@code faction}'s treasury (ECO-01); the match's resources are {@code config().rules().economy()}. */
    FactionTreasury treasury(FactionSlot faction);

    /** A live unit's hit points now (CMB-01): its most less the damage it took; NaN for no live unit. */
    double hitPoints(int handle);

    /** A live unit's most hit points, as modifiers leave them; NaN for no live unit. */
    double maxHitPoints(int handle);

    long tick();

    MatchConfig config();

    TemplateSet templates();

    int unitCount();

    boolean isAlive(int handle);

    @Nullable UnitView unit(int handle);

    /** A sorted copy; cold path. */
    int[] unitHandles();

    int[] unitHandlesOf(FactionSlot faction);

    /** The published terrain pages: an immutable grid, replaced when a page is published. */
    SurfaceGrid surfaces();

    /** The published pages by the hash of their slices, in page order. */
    List<PageRef> pageRefs();

    /** A live unit's path: its waypoints, the next one, and the request it waits for; null for a dead handle. */
    PathStore.@Nullable UnitPath path(int handle);

    /** A live unit's orders, the current one first (ORD-02); empty for an idle unit or a dead handle. */
    List<OrderQueues.Order> orders(int handle);

    /** How often each rung of the stuck ladder (1 to 5) was reached in this run, by rung; not saved (PATH-07). */
    long[] ladderRungs();

    /**
     * For an {@code ADAPTIVE} adapter about to step tick {@code tick}: the path requests in flight due by then whose jobs
     * have finished, ascending, which it hands in as that tick's {@link JobDelivery} (PATH-16).
     */
    int[] finishedPathJobs(long tick);

    /** A live unit's terrain motion: velocity, floor, stuck monitor, arrival latch; null for a dead handle. */
    MotionStore.@Nullable Extra motion(int handle);

    /**
     * How a live unit's value at a value path comes about: base, modifiers, result (ADR-0013); null for a dead handle or
     * a path the set lacks.
     */
    @Nullable StatBreakdown stats(int handle, String path);

    /** The path service's counts since the match started. */
    PathService.Stats pathStats();
}
