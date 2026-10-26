package dev.strategos.core.sim;

import dev.strategos.core.sim.econ.Forage;
import dev.strategos.core.sim.econ.NodeDiscovery;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.TerrainEdits;
import dev.strategos.core.sim.nav.VoxelSlice;

/** Resource discovery and reconciliation against externally changed terrain slices. */
final class MatchResourceDiscovery {
    private MatchResourceDiscovery() {}

    static void discover(MatchState state, byte[][] sliceBytes) {
        MatchBounds b = state.config().bounds();
        NodeDiscovery.discover(
                state.nodes(),
                state.config().rules().economy(),
                state.surfaces().pagesX(),
                state.surfaces().pagesZ(),
                b.minY(),
                i -> sliceBytes[i] == null ? null : VoxelSlice.decode(sliceBytes[i]));
        for (int id = 1; id <= state.nodes().count(); id++) {
            state.ledger().found(state.nodes().resource(id), state.nodes().amount(id));
        }
        Forage.showAll(state);
    }

    static void losses(MatchState state, byte[][] sliceBytes, TerrainEdits terrainEdits, int page, PageCoord coord) {
        NodeStore nodes = state.nodes();
        if (!nodes.discovered() || nodes.count() == 0) {
            return;
        }
        VoxelSlice slice = VoxelSlice.decode(sliceBytes[page]);
        int minY = state.config().bounds().minY();
        for (int id = 1; id <= nodes.count(); id++) {
            long[] blocks = nodes.blocks(id);
            for (int k = nodes.gone(id); k < blocks.length; k++) {
                int x = NodeStore.x(blocks[k]);
                int z = NodeStore.z(blocks[k]);
                int band = NodeStore.y(blocks[k]) - minY;
                if (x >> 4 != coord.x() || z >> 4 != coord.z() || band < 0 || band >= slice.height()) {
                    continue;
                }
                int column = (x & 15) * 16 + (z & 15);
                boolean shown = slice.materials().at(column * slice.height() + band) != null;
                boolean ours = terrainEdits.classAt(page, TerrainEdits.cell(x & 15, band, z & 15)) >= 0;
                if (!shown && !ours) {
                    long lost = nodes.lose(id, blocks[k]);
                    if (lost > 0) {
                        state.ledger().lost(nodes.resource(id), lost);
                    }
                }
            }
        }
    }
}
