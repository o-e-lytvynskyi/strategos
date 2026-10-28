package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.EconomyTimes;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.core.sim.unitai.ActivityTier;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Read-only questions about a match at its last completed tick. */
@CoreApi
public interface MatchQueries {
    /** Why a template swap would be unsafe, or null when every live reference remains valid. */
    @Nullable String templateSwapProblem(TemplateSet next);

    /** The match's standing buildings, by slot (BLD-01). */
    List<BuildingView> buildings();

    /** The match's resource nodes, by id (ECO-02); empty before discovery. */
    List<NodeView> nodes();

    /** Faction {@code faction}'s treasury (ECO-01); the match's resources are {@code config().rules().economy()}. */
    FactionTreasury treasury(FactionSlot faction);

    /** The population faction {@code faction} uses, its units' and its reservations (ECO-06). */
    int populationUsed(FactionSlot faction);

    /** The most population faction {@code faction} may use now (ECO-06). */
    int populationCap(FactionSlot faction);

    /**
     * Faction {@code faction}'s gatherers (ECO-05): per resource of the economy those on a gather order at a node of
     * it, and last the idle ones.
     */
    int[] workers(FactionSlot faction);

    /** The stance a player gave a live unit (CMB-05), or null when it fights as its template says or is dead. */
    @Nullable Stance explicitStance(int handle);

    /** The resource a live unit carries, its index in the economy, or -1 when it carries none or is dead (ECO-05). */
    int carriedResource(int handle);

    /** How much a live unit carries, in thousandths; 0 when it carries none or is dead. */
    long carriedAmount(int handle);

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

    /** Derived navigation memory across every mobility class; fake views may expose a single grid. */
    default long navigationBytes() {
        return surfaces().bytes();
    }

    /** The published pages by the hash of their slices, in page order. */
    List<PageRef> pageRefs();

    /** A live unit's path: its waypoints, the next one, and the request it waits for; null for a dead handle. */
    PathStore.@Nullable UnitPath path(int handle);

    /** A live unit's orders, the current one first (ORD-02); empty for an idle unit or a dead handle. */
    List<OrderQueues.Order> orders(int handle);

    /** How often each rung of the stuck ladder (1 to 5) was reached in this run, by rung; not saved (PATH-07). */
    long[] ladderRungs();

    /** Unit {@code handle}'s activity tier (UNIT-05), IDLE for a dead one. */
    ActivityTier tier(int handle);

    /** How many live units are in each activity tier, by {@link ActivityTier#code()}. */
    int[] tierCounts();

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

    /** The blocks the core owns, as they must stand (MATCH-04): what a resumed match's reconciler puts back. */
    default List<WorldEffect> worldClaims() {
        return List.of();
    }

    /**
     * Why faction {@code faction}'s building of template {@code template} with its corner at ({@code x}, {@code z}),
     * unturned, breaks a placement rule of the ground (BLD-02's, as the server judges them), or null; cost aside. For a
     * computer player's site search (AI-02).
     */
    default @Nullable String placementRefusal(int template, int x, int z, int faction) {
        return null;
    }

    /** The standing height of match-local column ({@code x}, {@code z}), NaN for none: for reachability (AI-02). */
    default double floorAt(int x, int z) {
        return Double.NaN;
    }

    /** Whether a building's footprint blocks match-local column ({@code x}, {@code z}). */
    default boolean footprintAt(int x, int z) {
        return false;
    }

    /** PERF-08's combat systems' times so far. */
    default dev.strategos.core.sim.combat.CombatTimes.Stats combatTimes() {
        return new dev.strategos.core.sim.combat.CombatTimes.Stats(0, 0, 0, 0, 0);
    }

    /** PERF-07.4's node index and dropsite query times so far. */
    default EconomyTimes.Stats economyTimes() {
        return new EconomyTimes.Stats(0, 0, 0, 0, 0, 0);
    }
}
