package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.nav.MaterialKind;

/**
 * A copy of one resource node for cold readers (ECO-02): GameTests, debug commands, the overlay; with what it was found
 * with and, for a field, its ticks left to ripen (ECO-09); the block gathered next, which a gather order names, X and Z
 * match-local and Y the world's height, so below 0 in an arena (all -1 once none is left: ask {@link #hasBlock}, not the
 * height's sign); and a deposit's template net index (ECO-08), -1 for any other node.
 */
@CoreApi
public record NodeView(
        int id,
        MaterialKind kind,
        String resource,
        String subtype,
        long amount,
        double anchorX,
        double anchorZ,
        int blocks,
        int gone,
        int slots,
        int freeSlots,
        long most,
        long ripening,
        int topX,
        int topY,
        int topZ,
        int template) {
    /** Whether a block is left to gather, the one the top fields name. */
    public boolean hasBlock() {
        return gone < blocks;
    }
}
