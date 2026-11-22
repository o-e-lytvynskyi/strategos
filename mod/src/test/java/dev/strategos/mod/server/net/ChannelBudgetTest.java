package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ChannelBudgetTest {
    @Test
    void anUnsentFrameSpendsNothingAndARefillOccursOnlyOncePerTick() {
        var budget = new ChannelBudget(100, 400, 20);
        assertThat(budget.take(20, 350)).isTrue();
        assertThat(budget.take(20, 51)).isFalse();
        assertThat(budget.take(20, 50)).isTrue();
        assertThat(budget.take(21, 101)).isFalse();
        assertThat(budget.take(21, 100)).isTrue();
        assertThat(budget.take(21, 1)).isFalse();
    }

    @Test
    void aLargeFrameEventuallyFitsAndLongIdleCannotExceedTheBurst() {
        var budget = new ChannelBudget(100, 400, 0);
        assertThat(budget.take(0, 400)).isTrue();
        assertThat(budget.take(3, 400)).isFalse();
        assertThat(budget.ticksUntil(400)).isEqualTo(1);
        assertThat(budget.take(4, 400)).isTrue();
        assertThat(budget.take(1_000, 401)).isFalse();
        assertThat(budget.take(1_000, 400)).isTrue();
        assertThat(budget.take(1_000, 1)).isFalse();
    }

    @Test
    void wrapAndIndependentRecipientsPreserveTheirCredits() {
        var first = new ChannelBudget(50, 100, Integer.MAX_VALUE);
        var second = new ChannelBudget(50, 100, Integer.MAX_VALUE);
        assertThat(first.take(Integer.MAX_VALUE, 100)).isTrue();
        assertThat(first.take(Integer.MAX_VALUE, 1)).isFalse();
        assertThat(second.take(Integer.MAX_VALUE, 100)).isTrue();
        assertThat(first.take(Integer.MIN_VALUE, 50)).isTrue();
        assertThat(first.take(Integer.MIN_VALUE, 1)).isFalse();
    }

    @Test
    void invalidCapsAndNegativeBytesAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ChannelBudget(0, 100, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new ChannelBudget(100, 99, 0));
        var budget = new ChannelBudget(100, 200, 0);
        assertThatIllegalArgumentException().isThrownBy(() -> budget.take(0, -1));
        assertThat(budget.take(0, 200)).isTrue();
    }
}
