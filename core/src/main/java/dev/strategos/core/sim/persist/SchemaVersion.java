package dev.strategos.core.sim.persist;

import dev.strategos.core.shared.CoreApi;

/** The snapshot schema version this build writes, as the {@code "v"} field. */
@CoreApi
public final class SchemaVersion {
    /** 2 from M1: terrain pages (T7); M1's later tasks add fields with defaults until it is released (T27). */
    public static final int CURRENT = 2;

    private SchemaVersion() {}
}
