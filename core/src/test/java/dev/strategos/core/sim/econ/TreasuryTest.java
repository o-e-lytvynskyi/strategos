package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** ECO-01 in L1: payments cover every resource or change nothing, the missing amounts, and the invariant. */
class TreasuryTest {
    @Test
    void aPaymentCoversEveryResourceOrChangesNothingAndNamesWhatIsMissing() {
        Treasury t = new Treasury(2, 4);
        t.grant(0, 0, 100 * Amount.UNIT);
        t.grant(0, 1, 50 * Amount.UNIT);

        long[] dear = {60 * Amount.UNIT, 60 * Amount.UNIT};
        assertThat(t.missing(0, dear)).containsExactly(0, 10 * Amount.UNIT, 0, 0);
        assertThat(t.pay(0, dear)).isFalse();
        assertThat(t.view(0).stock()).containsExactly(100_000L, 50_000L, 0L, 0L);
        assertThat(t.view(0).spent()).containsExactly(0L, 0L, 0L, 0L);

        long[] cost = {60 * Amount.UNIT, 40 * Amount.UNIT};
        assertThat(t.missing(0, cost)).isNull();
        assertThat(t.pay(0, cost)).isTrue();
        assertThat(t.view(0).stock()).containsExactly(40_000L, 10_000L, 0L, 0L);
        assertThat(t.view(0).spent()).containsExactly(60_000L, 40_000L, 0L, 0L);
        assertThat(t.view(1).stock()).as("the other faction's").containsExactly(0L, 0L, 0L, 0L);

        t.refund(0, cost);
        t.gather(0, 2, 1_500);
        assertThat(t.view(0).stock()).containsExactly(100_000L, 50_000L, 1_500L, 0L);
        assertThat(t.view(0).refunded()).containsExactly(60_000L, 40_000L, 0L, 0L);
        assertThat(t.view(0).gathered()).containsExactly(0L, 0L, 1_500L, 0L);
        assertThat(t.broken()).isNull();
        assertThat(t.empty()).isFalse();
        assertThat(new Treasury(2, 4).empty()).isTrue();
        assertThatThrownBy(() -> t.grant(0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnbalancedTreasuryIsFound() {
        Treasury t = new Treasury(1, 2);
        long[] saved = new long[10];
        saved[5] = 7; // resource 1's stock with nothing granted, gathered or refunded
        t.restore(saved);
        assertThat(t.broken())
                .isEqualTo("faction 0 resource 1: granted 0 + gathered 0 + refunded 0 != stock 7 + spent 0");

        t.restore(new long[] {-5, 0, 0, 0, 0, 0, 0, 0, 5, 0});
        assertThat(t.broken()).startsWith("faction 0 resource 0: stock -5 below 0");
    }

    @Test
    void amountsAreThousandthsRoundedOnce() {
        assertThat(Amount.of(0.6)).isEqualTo(600);
        assertThat(Amount.of(1.0005)).isEqualTo(1_001);
        assertThat(Amount.of(Double.NaN)).isZero();
        assertThat(Amount.format(60_000)).isEqualTo("60");
        assertThat(Amount.format(1_500)).isEqualTo("1.5");
        assertThat(Amount.format(1_005)).isEqualTo("1.005");
        assertThat(Amount.whole(1_999)).isEqualTo(1);
    }
}
