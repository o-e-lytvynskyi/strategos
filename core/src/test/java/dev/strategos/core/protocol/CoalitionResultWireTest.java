package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.client.model.ClientMatchModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoalitionResultWireTest {
    @Test
    void liveNewsAndRestoredResultsCarryTheSameOrderedCoalition() {
        MatchNews news = new MatchNews(7, 300, MatchNews.ENDED, 0, 300, 0, 0, List.of(0, 2));
        byte[] packet = WireCodec.encode(MatchNews.WIRE, news);
        assertThat(WireCodec.decode(MatchNews.WIRE, packet)).isEqualTo(news);
        assertThat(packet.length).isLessThanOrEqualTo(MatchNews.WIRE.maxSize());
        ClientMatchModel client = new ClientMatchModel();
        client.accept(WireCodec.decode(MatchNews.WIRE, packet), 7);
        assertThat(java.util.Objects.requireNonNull(client.end()).winners()).containsExactly(0, 2);
        MatchResults result = new MatchResults(List.of(0, 2), 300, List.of());
        assertThat(WireCodec.decode(MatchResults.WIRE, WireCodec.encode(MatchResults.WIRE, result)))
                .isEqualTo(result);
        ClientMatchModel rejoined = new ClientMatchModel();
        rejoined.accept(new MatchStateMsg(7, MatchStateMsg.Lifecycle.ENDED, 301, result, List.of()), 7);
        assertThat(java.util.Objects.requireNonNull(rejoined.end()).winners()).containsExactly(0, 2);
        rejoined.accept(
                new MatchStateMsg(
                        8, MatchStateMsg.Lifecycle.ENDED, 302, new MatchResults(-1, 302, List.of()), List.of()),
                7);
        assertThat(java.util.Objects.requireNonNull(rejoined.end()).winners()).containsExactly(0, 2);
        MatchResults draw = new MatchResults(List.of(), 300, List.of());
        assertThat(WireCodec.decode(MatchResults.WIRE, WireCodec.encode(MatchResults.WIRE, draw))
                        .winners())
                .isEmpty();
    }

    @Test
    void legacySingleWinnerAndDrawBytesStayUnchanged() {
        assertThat(WireCodec.encode(MatchResults.WIRE, new MatchResults(2, 3, List.of())))
                .containsExactly(new byte[] {4, 3, 0});
        assertThat(WireCodec.encode(MatchResults.WIRE, new MatchResults(-1, 3, List.of())))
                .containsExactly(new byte[] {1, 3, 0});
        assertThat(WireCodec.encode(MatchNews.WIRE, new MatchNews(1, 2, MatchNews.ENDED, 2, 3)))
                .containsExactly(new byte[] {1, 2, 1, 4, 3});
    }

    @Test
    void malformedCoalitionsNeverBecomeAnIndividualWinOrDraw() {
        assertThatThrownBy(() -> new MatchResults(List.of(2, 0), 3, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchResults(List.of(0, 0), 3, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MatchResults(List.of(16), 3, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        // -2 is the coalition marker: it must be followed by a bounded mask with at least two winners.
        assertThatThrownBy(() -> WireCodec.decode(MatchResults.WIRE, new byte[] {3, 0, 3, 0}))
                .isInstanceOf(WireException.class);
        assertThatThrownBy(() -> WireCodec.decode(MatchResults.WIRE, new byte[] {3, 1, 3, 0}))
                .isInstanceOf(WireException.class);
        assertThatThrownBy(() -> WireCodec.decode(MatchResults.WIRE, new byte[] {3}))
                .isInstanceOf(WireException.class);
    }
}
