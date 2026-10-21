package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;

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

    /** {@code message} names the first divergent checkpoint when there is one. */
    public record ReplayReport(boolean ok, String message, ReplayResult result) {}
}
