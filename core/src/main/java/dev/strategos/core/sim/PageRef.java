package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** A known terrain slice reference; schema 8 distinguishes geometry still awaiting its first restored capture. */
@CoreApi
public record PageRef(int x, int z, long slice, boolean pending) {
    /** Released references name published geometry. */
    public PageRef(int x, int z, long slice) {
        this(x, z, slice, false);
    }
}
