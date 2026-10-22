package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * What the {@code feedback} payload carries (04 §2.1): a command's result for the player who sent it, or a notice for
 * a faction; a leading byte tells which.
 */
@CoreApi
public sealed interface FeedbackMsg permits Feedback, NoticeMsg {
    WireCodec<FeedbackMsg> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, FeedbackMsg value) {
            switch (value) {
                case Feedback f -> {
                    out.writeByte(0);
                    Feedback.WIRE.write(out, f);
                }
                case NoticeMsg n -> {
                    out.writeByte(1);
                    NoticeMsg.WIRE.write(out, n);
                }
            }
        }

        @Override
        public FeedbackMsg read(WireReader in) {
            int kind = in.readUnsignedByte();
            return switch (kind) {
                case 0 -> Feedback.WIRE.read(in);
                case 1 -> NoticeMsg.WIRE.read(in);
                default -> throw new WireException("unknown feedback kind " + kind);
            };
        }

        @Override
        public int maxSize() {
            return 1 + Math.max(Feedback.WIRE.maxSize(), NoticeMsg.WIRE.maxSize());
        }
    };
}
