package dev.strategos.core.sim;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The feedback record of one command (04 §7.3): its outcome, how many units it was accepted and refused for, the
 * first refusal met in handle order, and, refused for {@link RejectReason#NOT_ENOUGH_RESOURCES}, what the faction
 * lacks per resource in thousandths (ECO-01); empty otherwise.
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
        List<Long> missing)
        implements OutputEvent {
    public CommandResult {
        missing = List.copyOf(missing);
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
