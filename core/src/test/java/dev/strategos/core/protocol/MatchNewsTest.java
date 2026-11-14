package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.client.model.ClientMatchModel;
import org.junit.jupiter.api.Test;

/** The PHASE news (TECH-02): kind 4, the advancing faction and the phase's ordinal, no place. */
class MatchNewsTest {
    @Test
    void aPhaseNoticeRoundTripsWithoutAPlace() {
        MatchNews phase = new MatchNews(7, 1_200, MatchNews.PHASE, 0, 1);
        byte[] bytes = WireCodec.encode(MatchNews.WIRE, phase);
        assertThat(WireCodec.decode(MatchNews.WIRE, bytes)).isEqualTo(phase);
        assertThat(MatchNews.PHASE).isEqualTo(4);
        assertThat(bytes)
                .as("matchId, tick (2 bytes), kind, faction, ordinal: no coordinates, as a defeat")
                .hasSize(1 + 2 + 1 + 1 + 1);
        assertThat(WireCodec.encode(MatchNews.WIRE, new MatchNews(7, 1_200, MatchNews.DEFEATED, 0, 1)))
                .hasSameSizeAs(bytes);
        assertThat(bytes.length).isLessThanOrEqualTo(MatchNews.WIRE.maxSize());
    }

    @Test
    void thereIsNoKindAfterPhase() {
        assertThatThrownBy(() -> new MatchNews(7, 1, MatchNews.PHASE + 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] bytes = WireCodec.encode(MatchNews.WIRE, new MatchNews(7, 1_200, MatchNews.PHASE, 0, 1));
        bytes[3] = 5; // the kind byte
        assertThatThrownBy(() -> WireCodec.decode(MatchNews.WIRE, bytes)).isInstanceOf(WireException.class);
    }

    @Test
    void thePhaseNoticeDefeatsNobody() {
        ClientMatchModel model = new ClientMatchModel();
        model.accept(new MatchNews(7, 1_200, MatchNews.PHASE, 0, 1), 7);
        assertThat(model.defeated(0)).as("a phase is not a defeat").isFalse();
        model.accept(new MatchNews(7, 1_300, MatchNews.DEFEATED, 1, 0), 7);
        assertThat(model.defeated(1)).isTrue();
    }
}
