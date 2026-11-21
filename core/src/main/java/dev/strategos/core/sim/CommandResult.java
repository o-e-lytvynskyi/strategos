package dev.strategos.core.sim;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The feedback record of one command (04 §7.3): its outcome, how many units it was accepted and refused for, the
 * first refusal met in handle order, and, refused for {@link RejectReason#NOT_ENOUGH_RESOURCES}, what the faction
 * lacks per resource in thousandths (ECO-01); empty otherwise. {@code source} is the exact in-memory
 * submission, for adapter callback routing only: it is never serialized, journaled or hashed.
 */
@CoreApi
public record CommandResult(
        PlayerSlot issuer,
        int clientSeq,
        String kind,
        CommandOutcome outcome,
        int accepted,
        int rejected,
        @Nullable RejectReason firstReason,
        List<Long> missing,
        @Nullable CommandEnvelope source)
        implements OutputEvent {
    public CommandResult {
        missing = List.copyOf(missing);
    }

    /** A detached result without a live submission origin. */
    public CommandResult(
            PlayerSlot issuer,
            int clientSeq,
            String kind,
            CommandOutcome outcome,
            int accepted,
            int rejected,
            @Nullable RejectReason firstReason,
            List<Long> missing) {
        this(issuer, clientSeq, kind, outcome, accepted, rejected, firstReason, missing, null);
    }

    /** A core result bound to the exact submission that produced it. */
    public CommandResult(
            CommandEnvelope source,
            String kind,
            CommandOutcome outcome,
            int accepted,
            int rejected,
            @Nullable RejectReason firstReason,
            List<Long> missing) {
        this(source.issuer(), source.clientSeq(), kind, outcome, accepted, rejected, firstReason, missing, source);
    }

    /** Keep command bodies (including AI scripts) out of diagnostic result rendering. */
    @Override
    public String toString() {
        return "CommandResult[issuer=" + issuer + ", clientSeq=" + clientSeq + ", kind=" + kind
                + ", outcome=" + outcome + ", accepted=" + accepted + ", rejected=" + rejected
                + ", firstReason=" + firstReason + ", missing=" + missing + "]";
    }

    /** Submission identity is transport metadata, not part of the result's value. */
    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof CommandResult r
                && issuer.equals(r.issuer)
                && clientSeq == r.clientSeq
                && kind.equals(r.kind)
                && outcome == r.outcome
                && accepted == r.accepted
                && rejected == r.rejected
                && firstReason == r.firstReason
                && missing.equals(r.missing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, clientSeq, kind, outcome, accepted, rejected, firstReason, missing);
    }

    /** A result that lacks nothing. */
    public CommandResult(
            PlayerSlot issuer,
            int clientSeq,
            String kind,
            CommandOutcome outcome,
            int accepted,
            int rejected,
            @Nullable RejectReason firstReason) {
        this(issuer, clientSeq, kind, outcome, accepted, rejected, firstReason, List.of());
    }
}
