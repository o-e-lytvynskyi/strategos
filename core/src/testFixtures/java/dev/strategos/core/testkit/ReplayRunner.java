package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import java.util.List;

/** Replays a journal and says whether it reproduced the live run: every checkpoint, then the final hash. */
public final class ReplayRunner {
    private ReplayRunner() {}

    public static ReplayReport check(Journal journal, TemplateSet templates, long expectedFinalHash) {
        ReplayResult result = ReplayDriver.replay(journal, templates);
        if (!result.matches()) {
            return new ReplayReport(
                    false,
                    "the replay diverges at checkpoint tick " + result.firstDivergentCheckpoint() + ": recorded "
                            + Hex64.format(result.expectedHash()) + ", replayed " + Hex64.format(result.actualHash()),
                    result);
        }
        if (result.finalHash() != expectedFinalHash) {
            return new ReplayReport(
                    false,
                    "the replay reaches tick " + result.finalTick() + " with hash " + Hex64.format(result.finalHash())
                            + ", the live run had " + Hex64.format(expectedFinalHash),
                    result);
        }
        return new ReplayReport(
                true,
                "the replay matches " + journal.checkpointTicks().length + " checkpoints and the final hash at tick "
                        + result.finalTick(),
                result);
    }

    /**
     * Replays the journals of one run that was saved and restored, as one uninterrupted run (QA-05.4): from the first
     * journal's start snapshot through each journal's inputs in turn. A later journal must start at the tick the replay
     * stands at, from a snapshot with the replayed state's hash, so the restore lost nothing; then every checkpoint and
     * the final hash must match.
     */
    public static ReplayReport chain(TemplateSet templates, long expectedFinalHash, List<Journal> journals) {
        if (journals.isEmpty()) {
            throw new IllegalArgumentException("no journal to replay");
        }
        MatchCore core = MatchCore.restore(journals.get(0).start(), templates, CorePorts.of(() -> 0L));
        int checkpoints = 0;
        for (int j = 0; j < journals.size(); j++) {
            Journal journal = journals.get(j);
            long tick = core.queries().tick();
            if (journal.startTick() != tick) {
                return failed(
                        core,
                        "journal " + j + " starts at tick " + journal.startTick() + ", the replay is at tick " + tick);
            }
            long restored = MatchCore.restore(journal.start(), templates, CorePorts.of(() -> 0L))
                    .stateHash();
            if (restored != core.stateHash()) {
                return failed(
                        core,
                        "journal " + j + " starts from hash " + Hex64.format(restored) + ", the replay has "
                                + Hex64.format(core.stateHash()) + " at tick " + tick);
            }
            List<TickInputs> recorded = journal.nonEmptyTicks();
            long[] checkpointTicks = journal.checkpointTicks();
            long[] checkpointHashes = journal.checkpointHashes();
            int nextInput = 0;
            int nextCheckpoint = 0;
            while (nextCheckpoint < checkpointTicks.length && checkpointTicks[nextCheckpoint] <= journal.startTick()) {
                nextCheckpoint++;
            }
            for (long t = journal.startTick() + 1; t <= journal.lastTick(); t++) {
                TickInputs inputs = TickInputs.empty(t);
                if (nextInput < recorded.size() && recorded.get(nextInput).tick() == t) {
                    inputs = recorded.get(nextInput++);
                }
                core.step(inputs);
                while (nextCheckpoint < checkpointTicks.length && checkpointTicks[nextCheckpoint] == t) {
                    if (core.stateHash() != checkpointHashes[nextCheckpoint]) {
                        return new ReplayReport(
                                false,
                                "journal " + j + " diverges at checkpoint tick " + t + ": recorded "
                                        + Hex64.format(checkpointHashes[nextCheckpoint]) + ", replayed "
                                        + Hex64.format(core.stateHash()),
                                new ReplayResult(
                                        t,
                                        core.stateHash(),
                                        false,
                                        t,
                                        checkpointHashes[nextCheckpoint],
                                        core.stateHash()));
                    }
                    nextCheckpoint++;
                    checkpoints++;
                }
            }
        }
        if (core.stateHash() != expectedFinalHash) {
            return failed(
                    core,
                    "the replay reaches tick " + core.queries().tick() + " with hash " + Hex64.format(core.stateHash())
                            + ", the live run had " + Hex64.format(expectedFinalHash));
        }
        return new ReplayReport(
                true,
                "the replay of " + journals.size() + " journals matches " + checkpoints
                        + " checkpoints and the final hash at tick "
                        + core.queries().tick(),
                new ReplayResult(core.queries().tick(), core.stateHash(), true, -1, 0, 0));
    }

    private static ReplayReport failed(MatchCore core, String message) {
        return new ReplayReport(
                false, message, new ReplayResult(core.queries().tick(), core.stateHash(), true, -1, 0, 0));
    }

    /** {@code message} names the first divergent checkpoint when there is one. */
    public record ReplayReport(boolean ok, String message, ReplayResult result) {}
}
