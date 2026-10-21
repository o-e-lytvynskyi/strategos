package dev.strategos.core.sim;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.PlayerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The feedback record of one command (04 §7.3): its outcome, how many units it was accepted and refused for, and the
 * first refusal met in handle order.
 */
@CoreApi
public record CommandResult(
        PlayerSlot issuer,
        int clientSeq,
        String kind,
        CommandOutcome outcome,
        int accepted,
        int rejected,
        @Nullable RejectReason firstReason)
        implements OutputEvent {}
