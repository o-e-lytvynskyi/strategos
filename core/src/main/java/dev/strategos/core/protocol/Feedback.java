package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A command's result for the player who sent it: its client sequence number, its outcome, the first reason a unit of it
 * was refused (none when all were accepted), how many of its units were accepted of how many, and what the faction
 * lacked per resource in thousandths, when it could not pay (ECO-01).
 */
@CoreApi
public record Feedback(
        int clientSeq,
        CommandOutcome outcome,
        @Nullable RejectReason reason,
        int accepted,
        int total,
        List<Long> missing)
        implements FeedbackMsg {
    /** The most resources a refusal names. */
    public static final int MAX_MISSING = 16;

    public Feedback {
        missing = List.copyOf(missing);
        if (missing.size() > MAX_MISSING) {
            throw new IllegalArgumentException(missing.size() + " missing amounts, more than " + MAX_MISSING);
        }
    }

    /** A result that lacks nothing. */
    public Feedback(int clientSeq, CommandOutcome outcome, @Nullable RejectReason reason, int accepted, int total) {
        this(clientSeq, outcome, reason, accepted, total, List.of());
    }

    public static final WireCodec<Feedback> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Feedback value) {
            out.writeVarInt(value.clientSeq());
            WireCodecs.COMMAND_OUTCOME.write(out, value.outcome());
            WireCodecs.writeReason(out, value.reason());
            out.writeVarInt(value.accepted());
            out.writeVarInt(value.total());
            out.writeVarInt(value.missing().size());
            for (long m : value.missing()) {
                out.writeVarLong(m);
            }
        }

        @Override
        public Feedback read(WireReader in) {
            int seq = in.readVarInt();
            CommandOutcome outcome = WireCodecs.COMMAND_OUTCOME.read(in);
            RejectReason reason = WireCodecs.readReason(in);
            int accepted = in.readVarInt();
            int total = in.readVarInt();
            int n = in.readVarInt(MAX_MISSING);
            List<Long> missing = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                missing.add(in.readVarLong());
            }
            return new Feedback(seq, outcome, reason, accepted, total, missing);
        }

        @Override
        public int maxSize() {
            return 5 + 1 + 1 + 5 + 5 + 1 + MAX_MISSING * 10;
        }
    };
}
