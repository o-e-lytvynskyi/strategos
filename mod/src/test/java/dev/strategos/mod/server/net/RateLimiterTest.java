package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Fixed published limits, rather than values copied from implementation constants. */
class RateLimiterTest {
    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);

    @Test
    void eightyCommandBurstThenExactlyFortyCommandsRefillInTwentyServerTicks() {
        var limiter = new RateLimiter();
        assertThat(limiter.take(FIRST, 0, 80, 0)).isTrue();
        assertThat(limiter.take(FIRST, 0, 1, 0)).isFalse();
        assertThat(limiter.take(FIRST, 19, 39, 0)).isFalse();
        assertThat(limiter.take(FIRST, 20, 40, 0)).isTrue();
        assertThat(limiter.take(FIRST, 20, 1, 0)).isFalse();
        assertThat(limiter.take(SECOND, 20, 80, 0)).isTrue();
    }

    @Test
    void threeThousandHandlesRefillPerTwentyTicksWithoutSpendingARejectedRange() {
        var limiter = new RateLimiter();
        assertThat(limiter.take(FIRST, 0, 0, 3000)).isTrue();
        assertThat(limiter.take(FIRST, 0, 0, 1)).isFalse();
        assertThat(limiter.take(FIRST, 19, 0, 2851)).isFalse();
        assertThat(limiter.take(FIRST, 20, 0, 3000)).isTrue();
        assertThat(limiter.take(FIRST, 20, 0, 1)).isFalse();
    }

    @Test
    void failureOfEitherBudgetCannotConsumeTheOtherBudget() {
        var limiter = new RateLimiter();
        assertThat(limiter.take(FIRST, 0, 1, 3001)).isFalse();
        assertThat(limiter.take(FIRST, 0, 80, 0)).isTrue();
        assertThat(limiter.take(SECOND, 0, 81, 1)).isFalse();
        assertThat(limiter.take(SECOND, 0, 0, 3000)).isTrue();
    }

    @Test
    void idleRefillCannotBankMoreThanTheAllowedBurst() {
        var limiter = new RateLimiter();
        assertThat(limiter.take(FIRST, 0, 80, 3000)).isTrue();
        assertThat(limiter.take(FIRST, 120000, 80, 3000)).isTrue();
        assertThat(limiter.take(FIRST, 120000, 1, 0)).isFalse();
        assertThat(limiter.take(FIRST, 120000, 0, 1)).isFalse();
    }

    @Test
    void theRollingWindowExpiresOnlyOldViolationsAtTheExactMinuteBoundary() {
        var limiter = new RateLimiter();
        for (int i = 0; i < 100; i++) assertThat(limiter.violation(FIRST, 0)).isFalse();
        assertThat(limiter.violation(FIRST, 1199)).isTrue();
        for (int i = 0; i < 99; i++) assertThat(limiter.violation(FIRST, 1200)).isFalse();
        assertThat(limiter.violation(FIRST, 1200)).isTrue();
        assertThat(limiter.violation(SECOND, 1200)).isFalse();
    }

    @Test
    void forgettingOneConnectionRestoresItsBudgetsWithoutChangingTheOtherPeer() {
        var limiter = new RateLimiter();
        assertThat(limiter.take(FIRST, 0, 80, 3000)).isTrue();
        assertThat(limiter.take(SECOND, 0, 80, 3000)).isTrue();
        for (int i = 0; i < 100; i++) assertThat(limiter.violation(FIRST, 0)).isFalse();
        limiter.forget(FIRST);
        assertThat(limiter.take(FIRST, 0, 80, 3000)).isTrue();
        assertThat(limiter.violation(FIRST, 0)).isFalse();
        assertThat(limiter.take(SECOND, 0, 1, 0)).isFalse();
        assertThat(limiter.take(SECOND, 0, 0, 1)).isFalse();
    }
}
