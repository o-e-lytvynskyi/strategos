package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import org.jspecify.annotations.Nullable;

/**
 * A command's result for the player who sent it: its client sequence number, its outcome, the first reason a unit of it
 * was refused (none when all were accepted), and how many of its units were accepted of how many.
 */
@CoreApi
public record Feedback(
        int clientSeq, CommandOutcome outcome, @Nullable RejectReason reason, int accepted, int total)
        implements FeedbackMsg {
    public static final WireCodec<Feedback> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Feedback value) {
            out.writeVarInt(value.clientSeq());
            WireCodecs.COMMAND_OUTCOME.write(out, value.outcome());
            WireCodecs.writeReason(out, value.reason());
            out.writeVarInt(value.accepted());
            out.writeVarInt(value.total());
        }

        @Override
        public Feedback read(WireReader in) {
            return new Feedback(
                    in.readVarInt(),
                    WireCodecs.COMMAND_OUTCOME.read(in),
                    WireCodecs.readReason(in),
                    in.readVarInt(),
                    in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 5 + 1 + 1 + 5 + 5;
        }
    };
}
