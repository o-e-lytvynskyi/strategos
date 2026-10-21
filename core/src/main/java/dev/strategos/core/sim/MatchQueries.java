package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
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
}
