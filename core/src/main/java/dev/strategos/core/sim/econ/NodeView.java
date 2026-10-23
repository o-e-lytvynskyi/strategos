package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.nav.MaterialKind;

/** A copy of one resource node for cold readers (ECO-02): GameTests, debug commands, the overlay. */
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
        int freeSlots) {}
