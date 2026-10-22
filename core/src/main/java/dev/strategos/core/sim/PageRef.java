package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** A published terrain page in a snapshot (schema 2): its page coordinates and the hash of the slice it was built from. */
@CoreApi
public record PageRef(int x, int z, long slice) {}
