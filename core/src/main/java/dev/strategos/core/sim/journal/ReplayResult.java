package dev.strategos.core.sim.journal;

import dev.strategos.core.shared.CoreApi;

/**
 * A replay's outcome: the tick and hash it reached, and whether every checkpoint matched; if not, the first divergent
 * checkpoint's tick with the recorded and the replayed hash ({@code firstDivergentCheckpoint} is -1 when none).
 */
@CoreApi
public record ReplayResult(
        long finalTick,
        long finalHash,
        boolean matches,
        long firstDivergentCheckpoint,
        long expectedHash,
        long actualHash) {}
