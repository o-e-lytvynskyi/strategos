package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.stats.StatBreakdown;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Read-only questions about a match at its last completed tick. */
@CoreApi
public interface MatchQueries {
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
