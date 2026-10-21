package dev.strategos.core.sim.journal;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.TickInputs;
import java.util.List;

/**
 * A read-only journal: a start snapshot, the inputs of every tick after it that had any (empty ticks are implicit), up
 * to {@link #lastTick()}, and state-hash checkpoints. Replaying it from the snapshot must reproduce every checkpoint.
 */
@CoreApi
public final class Journal {
    public static final int DEFAULT_CHECKPOINT_EVERY = 20;

    /** One hour of ticks; then the core re-bases the journal on a fresh snapshot. */
    public static final int DEFAULT_MAX_TICKS = 72_000;

    private final MatchSnapshot start;
    private final long lastTick;
    private final List<TickInputs> nonEmptyTicks;
    private final int checkpointEvery;
    private final long[] checkpointTicks;
    private final long[] checkpointHashes;

    Journal(
            MatchSnapshot start,
            long lastTick,
            List<TickInputs> nonEmptyTicks,
            int checkpointEvery,
            long[] checkpointTicks,
            long[] checkpointHashes) {
        if (checkpointTicks.length != checkpointHashes.length) {
            throw new IllegalArgumentException("one hash per checkpoint tick");
        }
        if (lastTick < start.tick()) {
            throw new IllegalArgumentException("journal ends at " + lastTick + " before its start " + start.tick());
        }
        this.start = start;
        this.lastTick = lastTick;
        this.nonEmptyTicks = List.copyOf(nonEmptyTicks);
        this.checkpointEvery = checkpointEvery;
        this.checkpointTicks = checkpointTicks.clone();
        this.checkpointHashes = checkpointHashes.clone();
    }

    public MatchSnapshot start() {
        return start;
    }

    public long startTick() {
        return start.tick();
    }

    public long lastTick() {
        return lastTick;
    }

    /** In tick order. */
    public List<TickInputs> nonEmptyTicks() {
        return nonEmptyTicks;
    }

    public int checkpointEvery() {
        return checkpointEvery;
    }

    public long[] checkpointTicks() {
        return checkpointTicks.clone();
    }

    public long[] checkpointHashes() {
        return checkpointHashes.clone();
    }
}
