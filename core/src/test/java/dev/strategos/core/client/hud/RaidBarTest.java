package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.MatchNews;
import org.junit.jupiter.api.Test;

/** AI-04.5's bar in L1: it fills with the muster, counts down once the wave left, and is no bar for its own wave. */
class RaidBarTest {
    /** Announced at tick 100, leaving at 700, from faction 1. */
    private static final MatchNews WAVE = new MatchNews(7, 100, MatchNews.WAVE, 1, 700, 60, 60);

    @Test
    void fillsWithTheMusterThenCountsDown() {
        assertThat(RaidBar.of(WAVE, 0, 100).fill()).isZero();
        RaidBar half = RaidBar.of(WAVE, 0, 400);
        assertThat(half.fill()).isEqualTo(0.5f);
        assertThat(half.lit()).isEqualTo(5);
        assertThat(half.marching()).isFalse();
        assertThat(half.seconds()).isEqualTo(15);
        RaidBar left = RaidBar.of(WAVE, 0, 700);
        assertThat(left.marching()).isTrue();
        assertThat(left.fill()).isEqualTo(1f);
        assertThat(RaidBar.of(WAVE, 0, 700 + RaidBar.MARCH / 2).fill()).isEqualTo(0.5f);
        assertThat(RaidBar.of(WAVE, 0, 700 + RaidBar.MARCH)).as("gone").isNull();
    }

    @Test
    void isNoBarForItsOwnWaveOrForNone() {
        assertThat(RaidBar.of(WAVE, 1, 400)).isNull();
        assertThat(RaidBar.of(null, 0, 400)).isNull();
        assertThat(RaidBar.of(new MatchNews(7, 100, MatchNews.ALERT, 1, 0, 1, 1), 0, 400))
                .isNull();
    }

    @Test
    void hasTenNotches() {
        assertThat(RaidBar.NOTCHES).isEqualTo(10);
        assertThat(RaidBar.notchAt(5, 182)).isEqualTo(91);
        assertThat(RaidBar.notchAt(9, 100)).isEqualTo(90);
    }
}
