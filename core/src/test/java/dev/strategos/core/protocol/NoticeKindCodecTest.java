package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** UI-21.1: the feed's three new notice kinds cross the wire, and the codes of the old kinds did not move. */
class NoticeKindCodecTest {
    @Test
    void everyKindRoundTripsInANotice() {
        for (NoticeKind kind : NoticeKind.values()) {
            NoticeMsg sent = new NoticeMsg(kind, 0x1234, "strategos:unit/worker");
            NoticeMsg got = WireCodec.decode(NoticeMsg.WIRE, WireCodec.encode(NoticeMsg.WIRE, sent));
            assertThat(got).as(kind.name()).isEqualTo(sent);
        }
    }

    @Test
    void theCodesAreStableAndTheNextOneIsUnknown() {
        NoticeKind[] kinds = NoticeKind.values();
        for (int code = 0; code < kinds.length; code++) {
            assertThat(WireCodec.decode(WireCodecs.NOTICE_KIND, WireCodec.encode(WireCodecs.NOTICE_KIND, kinds[code])))
                    .isEqualTo(kinds[code]);
            assertThat(WireCodec.encode(WireCodecs.NOTICE_KIND, kinds[code])[0]).isEqualTo((byte) code);
        }
        assertThat(WireCodec.encode(WireCodecs.NOTICE_KIND, NoticeKind.TRAINED)[0])
                .isEqualTo((byte) 5);
        assertThat(WireCodec.encode(WireCodecs.NOTICE_KIND, NoticeKind.BUILT)[0])
                .isEqualTo((byte) 6);
        assertThat(WireCodec.encode(WireCodecs.NOTICE_KIND, NoticeKind.RESEARCHED)[0])
                .isEqualTo((byte) 7);
        byte[] beyond = {(byte) kinds.length};
        assertThatThrownBy(() -> WireCodec.decode(WireCodecs.NOTICE_KIND, beyond))
                .isInstanceOf(WireException.class);
    }
}
