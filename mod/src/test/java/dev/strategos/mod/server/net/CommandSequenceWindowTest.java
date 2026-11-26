package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandSequenceWindowTest {
    @Test
    void duplicatesOldNumbersAndOverlapsNeverConsumeTheNextFreshRange() {
        var window = new CommandSequenceWindow();
        assertThat(window.take(20, 2)).isTrue();
        assertThat(window.take(20, 2)).isFalse();
        assertThat(window.take(19, 1)).isFalse();
        assertThat(window.take(21, 2)).isFalse();
        assertThat(window.take(22, 1)).isTrue();
        assertThat(window.take(22, 1)).isFalse();
    }

    @Test
    void missingNumbersAndTheMaximumLegalBatchRemainValid() {
        var window = new CommandSequenceWindow();
        assertThat(window.take(0, 32)).isTrue();
        assertThat(window.take(31, 1)).isFalse();
        assertThat(window.take(40, 1)).isTrue();
        assertThat(window.take(32, 1)).isFalse();
        assertThat(window.take(41, 1)).isTrue();
    }

    @Test
    void arithmeticOverflowDoesNotWrapOrConsumeTheLastValidRange() {
        var window = new CommandSequenceWindow();
        assertThat(window.take(Integer.MAX_VALUE - 1, 3)).isFalse();
        assertThat(window.take(Integer.MAX_VALUE - 1, 2)).isTrue();
        assertThat(window.take(Integer.MAX_VALUE, 1)).isFalse();
        assertThat(new CommandSequenceWindow().take(Integer.MAX_VALUE, 1)).isTrue();
    }

    @Test
    void invalidBoundsDoNotConsumeZeroAndANewViewStartsIndependently() {
        var window = new CommandSequenceWindow();
        assertThat(window.take(-1, 1)).isFalse();
        assertThat(window.take(0, 0)).isFalse();
        assertThat(window.take(0, 33)).isFalse();
        assertThat(window.take(0, 1)).isTrue();
        assertThat(window.take(0, 1)).isFalse();
        assertThat(new CommandSequenceWindow().take(0, 1)).isTrue();
    }
}
