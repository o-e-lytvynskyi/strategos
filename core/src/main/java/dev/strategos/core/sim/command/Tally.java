package dev.strategos.core.sim.command;

import dev.strategos.core.protocol.CommandKind;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Counts a command's accepted and refused units, remembering the first refusal, and makes its result. */
final class Tally {
    private int accepted;
    private int rejected;
    private @Nullable RejectReason firstReason;

    void accept() {
        accepted++;
    }

    void reject(RejectReason reason) {
        rejected++;
        if (firstReason == null) {
            firstReason = reason;
        }
    }

    /** ACCEPTED if every unit was accepted, PARTIAL if some, REJECTED if none. */
    CommandResult result(CommandEnvelope envelope, CommandKind<?> kind) {
        CommandOutcome outcome = accepted > 0 && rejected == 0
                ? CommandOutcome.ACCEPTED
                : accepted > 0 ? CommandOutcome.PARTIAL : CommandOutcome.REJECTED;
        return new CommandResult(
                envelope.issuer(), envelope.clientSeq(), kind.id(), outcome, accepted, rejected, firstReason);
    }

    /** A command that applied to all its {@code units}. */
    static CommandResult acceptAll(CommandEnvelope envelope, CommandKind<?> kind, int units) {
        return new CommandResult(
                envelope.issuer(), envelope.clientSeq(), kind.id(), CommandOutcome.ACCEPTED, units, 0, null);
    }

    /** A refusal for want of resources: what the faction lacks per resource, in thousandths (ECO-01). */
    static CommandResult unaffordable(CommandEnvelope envelope, CommandKind<?> kind, int units, long[] missing) {
        List<Long> lack = new ArrayList<>(missing.length);
        for (long m : missing) {
            lack.add(m);
        }
        return new CommandResult(
                envelope.issuer(),
                envelope.clientSeq(),
                kind.id(),
                CommandOutcome.REJECTED,
                0,
                units,
                RejectReason.NOT_ENOUGH_RESOURCES,
                lack);
    }

    /** A whole-command refusal: no unit accepted, {@code units} refused. */
    static CommandResult rejectAll(CommandEnvelope envelope, CommandKind<?> kind, int units, RejectReason reason) {
        return new CommandResult(
                envelope.issuer(), envelope.clientSeq(), kind.id(), CommandOutcome.REJECTED, 0, units, reason);
    }
}
