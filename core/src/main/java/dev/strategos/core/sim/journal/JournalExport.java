package dev.strategos.core.sim.journal;

import dev.strategos.core.shared.CoreApi;

/** A journal as exported from a live match, with the live tick and state hash its replay must reach. */
@CoreApi
public record JournalExport(Journal journal, long liveTick, long liveHash) {}
