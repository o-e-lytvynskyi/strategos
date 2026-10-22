package dev.strategos.core.sim.journal;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.TickInputs;
import java.util.List;

/**
 * Replays a journal in core {@code main}, so {@code /strategos debug replay} works in dev runs without shipping test
 * code: restores the start snapshot, steps every tick to the last one (empty inputs where nothing was recorded) and
 * compares every checkpoint.
 */
@CoreApi
public final class ReplayDriver {
    private ReplayDriver() {}

    public static ReplayResult replay(Journal journal, TemplateSet templates) {
        MatchCore core = MatchCore.restore(
                journal.start(),
                templates,
                CorePorts.of(() -> 0L),
                journal.segments().get(0)::slice);
        List<TickInputs> recorded = journal.nonEmptyTicks();
        long[] checkpointTicks = journal.checkpointTicks();
        long[] checkpointHashes = journal.checkpointHashes();
        int nextInput = 0;
        int nextCheckpoint = 0;
        long firstDivergent = -1;
        long expected = 0;
        long actual = 0;
        while (nextCheckpoint < checkpointTicks.length && checkpointTicks[nextCheckpoint] <= journal.startTick()) {
            nextCheckpoint++; // taken before the start snapshot: nothing to compare
        }
        for (long tick = journal.startTick() + 1; tick <= journal.lastTick(); tick++) {
            TickInputs inputs = TickInputs.empty(tick);
            if (nextInput < recorded.size() && recorded.get(nextInput).tick() == tick) {
                inputs = recorded.get(nextInput++);
            }
            core.step(inputs);
            while (nextCheckpoint < checkpointTicks.length && checkpointTicks[nextCheckpoint] == tick) {
                long hash = core.stateHash();
                if (firstDivergent < 0 && hash != checkpointHashes[nextCheckpoint]) {
                    firstDivergent = tick;
                    expected = checkpointHashes[nextCheckpoint];
                    actual = hash;
                }
                nextCheckpoint++;
            }
        }
        return new ReplayResult(
                core.queries().tick(), core.stateHash(), firstDivergent < 0, firstDivergent, expected, actual);
    }
}
