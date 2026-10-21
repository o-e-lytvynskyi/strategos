package dev.strategos.core.sim.persist;

import dev.strategos.core.shared.CoreApi;

/** The snapshot schema version this build writes, as the {@code "v"} field. */
@CoreApi
public final class SchemaVersion {
    public static final int CURRENT = 1;

    private SchemaVersion() {}
}
