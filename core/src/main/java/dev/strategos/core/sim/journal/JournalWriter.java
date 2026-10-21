package dev.strategos.core.sim.journal;

import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Records a match's journal; public but not {@code @CoreApi}: only the core writes it (rule B2 keeps the mod out). */
public final class JournalWriter {
    private final int checkpointEvery;
    private final int maxTicks;
    private MatchSnapshot start;
    private long lastTick;
    private final List<TickInputs> ticks = new ArrayList<>();
    private long[] checkpointTicks = new long[16];
    private long[] checkpointHashes = new long[16];
    private int checkpoints;

    public JournalWriter(MatchSnapshot start, int checkpointEvery, int maxTicks) {
        if (checkpointEvery < 1 || maxTicks < 1) {
            throw new IllegalArgumentException("journal needs positive limits: " + checkpointEvery + ", " + maxTicks);
        }
        this.checkpointEvery = checkpointEvery;
        this.maxTicks = maxTicks;
        this.start = start;
        this.lastTick = start.tick();
    }

    public int checkpointEvery() {
        return checkpointEvery;
    }

    /** A tick's canonical inputs; an empty tick only moves the last tick. */
    public void record(TickInputs canonical) {
        if (canonical.tick() != lastTick + 1) {
            throw new IllegalArgumentException("recording tick " + canonical.tick() + " after tick " + lastTick);
        }
        if (!canonical.events().isEmpty()) {
            ticks.add(canonical);
        }
        lastTick = canonical.tick();
    }

    public void checkpoint(long tick, long hash) {
        if (checkpoints == checkpointTicks.length) {
            checkpointTicks = Arrays.copyOf(checkpointTicks, checkpoints * 2);
            checkpointHashes = Arrays.copyOf(checkpointHashes, checkpoints * 2);
        }
        checkpointTicks[checkpoints] = tick;
        checkpointHashes[checkpoints] = hash;
        checkpoints++;
    }

    /** Whether the journal spans {@code maxTicks} and should start again from a fresh snapshot. */
    public boolean needsRebase() {
        return lastTick - start.tick() >= maxTicks;
    }

    /** Starts over from {@code start}, dropping every tick and checkpoint before it. */
    public void rebase(MatchSnapshot start) {
        this.start = start;
        this.lastTick = start.tick();
        ticks.clear();
        checkpoints = 0;
    }

    /** An immutable copy of the journal so far. */
    public Journal view() {
        return new Journal(
                start,
                lastTick,
                ticks,
                checkpointEvery,
                Arrays.copyOf(checkpointTicks, checkpoints),
                Arrays.copyOf(checkpointHashes, checkpoints));
    }
}
