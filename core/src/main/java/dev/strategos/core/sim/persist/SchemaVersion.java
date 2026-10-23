package dev.strategos.core.sim.persist;

import dev.strategos.core.shared.CoreApi;

/** The snapshot schema version this build writes, as the {@code "v"} field. */
@CoreApi
public final class SchemaVersion {
    /**
     * 2 from M1: terrain pages (T7) and the fields M1 and M2 added, each with a default; released at M1's close (T27),
     * pinned by the {@code match_v2} fixtures. A field that changes what an older save means needs version 3.
     */
    public static final int CURRENT = 2;

    private SchemaVersion() {}
}
