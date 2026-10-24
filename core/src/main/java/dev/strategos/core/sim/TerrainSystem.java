package dev.strategos.core.sim;

import dev.strategos.core.sim.build.Construction;

/**
 * P7, WORLD: the pages the core edited are rebuilt from their slices with its edits and footprints (M3 spec §1.6 row 5)
 * every {@code pageK} ticks, all in one publication, so no page job of the adapter is needed. A publication joins the
 * grid's components again, which costs most of a rebuild (spec §5: 0.31 ms for one page of a 4 x 4 grid against 0.04 ms
 * for its build), so edits wait for the next multiple of {@code pageK}, at most 4 ticks by default. A unit on a
 * footprint's column at the end of a tick, one that walked in during that wait, is put outside.
 */
final class TerrainSystem implements MatchSystem {
    @Override
    public Phase phase() {
        return Phase.WORLD;
    }

    @Override
    public void run(MatchState state) {
        if (state.tick() % state.config().rules().pageK() == 0) {
            state.rebuildEdited();
        }
        Construction.evictBlocked(state);
    }
}
