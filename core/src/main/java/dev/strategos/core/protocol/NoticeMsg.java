package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/** A notice for a faction: what happened, to which unit's handle, and a short text. */
@CoreApi
public record NoticeMsg(NoticeKind kind, int handle, String detail) implements FeedbackMsg {
    /** UTF-8 bytes of a notice's text. */
    public static final int DETAIL_BYTES = 256;

    public static final WireCodec<NoticeMsg> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, NoticeMsg value) {
            WireCodecs.NOTICE_KIND.write(out, value.kind());
            out.writeInt(value.handle());
            out.writeString(value.detail(), DETAIL_BYTES);
        }

        @Override
        public NoticeMsg read(WireReader in) {
            return new NoticeMsg(WireCodecs.NOTICE_KIND.read(in), in.readInt(), in.readString(DETAIL_BYTES));
        }

        @Override
        public int maxSize() {
            return 1 + 4 + 2 + DETAIL_BYTES;
        }
    };
}
