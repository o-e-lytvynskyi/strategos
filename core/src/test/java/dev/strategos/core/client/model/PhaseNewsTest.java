package dev.strategos.core.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.MatchNews;
import org.junit.jupiter.api.Test;

/** UI-21.1's phase source: the joined match's phase notices reach the model for the feed, and only those. */
class PhaseNewsTest {
    @Test
    void aPhaseNoticeIsKeptAndCounted() {
        ClientMatchModel m = new ClientMatchModel();
        assertThat(m.phase()).isNull();
        assertThat(m.phases()).isZero();
        MatchNews town = new MatchNews(3, 100, MatchNews.PHASE, 1, 1);
        m.accept(town, 3);
        assertThat(m.phase()).isEqualTo(town);
        assertThat(m.phases()).isEqualTo(1);
        MatchNews city = new MatchNews(3, 900, MatchNews.PHASE, 0, 2);
        m.accept(city, 3);
        assertThat(m.phase()).isEqualTo(city);
        assertThat(m.phases()).isEqualTo(2);
        assertThat(m.alerts()).as("not an alert").isZero();
        assertThat(m.waves()).isZero();
    }

    @Test
    void anotherMatchsNoticeIsIgnoredAndLeavingForgets() {
        ClientMatchModel m = new ClientMatchModel();
        m.accept(new MatchNews(4, 100, MatchNews.PHASE, 1, 1), 3);
        assertThat(m.phases()).isZero();
        m.accept(new MatchNews(3, 100, MatchNews.PHASE, 1, 1), 3);
        int before = m.clears();
        m.clear();
        assertThat(m.phase()).isNull();
        assertThat(m.phases()).isZero();
        assertThat(m.clears())
                .as("a reader of the counts sees the match was forgotten")
                .isEqualTo(before + 1);
    }
}
