package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.FactionSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactionExpiryPlanTest {
    private static final FactionSlot FIRST = new FactionSlot(0);
    private static final FactionSlot SECOND = new FactionSlot(1);

    @Test
    void aLoneExpiredPrimaryReservationMakesItsFactionDue() {
        assertThat(FactionExpiryPlan.due(List.of(new FactionExpiryPlan.Member(FIRST, false, 0))))
                .containsExactly(FIRST);
    }

    @Test
    void aConnectedCoopOwnerKeepsTheSharedFactionEvenAtZeroReportedRemaining() {
        assertThat(FactionExpiryPlan.due(List.of(
                        new FactionExpiryPlan.Member(FIRST, false, 0), new FactionExpiryPlan.Member(FIRST, true, 0))))
                .isEmpty();
    }

    @Test
    void oneNanosecondOfACoopReservationStillKeepsTheFaction() {
        assertThat(FactionExpiryPlan.due(List.of(
                        new FactionExpiryPlan.Member(FIRST, false, 0), new FactionExpiryPlan.Member(FIRST, false, 1))))
                .isEmpty();
    }

    @Test
    void simultaneousExpiredCoopMembersProduceOneFactionAction() {
        assertThat(FactionExpiryPlan.due(List.of(
                        new FactionExpiryPlan.Member(FIRST, false, 0), new FactionExpiryPlan.Member(FIRST, false, 0))))
                .containsExactly(FIRST);
    }

    @Test
    void observersHaveNoPrimaryOwnershipAndCannotKeepAFactionAlive() {
        assertThat(FactionExpiryPlan.due(List.of(
                        new FactionExpiryPlan.Member(null, true, 300), new FactionExpiryPlan.Member(FIRST, false, 0))))
                .containsExactly(FIRST);
        assertThat(FactionExpiryPlan.due(List.of(new FactionExpiryPlan.Member(null, false, 0))))
                .isEmpty();
    }

    @Test
    void anotherPrimaryFactionNeverExtendsTheExpiredOwnersLeaseAndOutputIsOrdered() {
        var third = new FactionSlot(2);
        assertThat(FactionExpiryPlan.due(List.of(
                        new FactionExpiryPlan.Member(third, false, 0), new FactionExpiryPlan.Member(SECOND, true, 300),
                        new FactionExpiryPlan.Member(FIRST, false, 0), new FactionExpiryPlan.Member(third, false, 0))))
                .containsExactly(FIRST, third);
    }

    @Test
    void noExpiredPrimaryReservationMeansNoAction() {
        assertThat(FactionExpiryPlan.due(List.of())).isEmpty();
        assertThat(FactionExpiryPlan.due(List.of(
                        new FactionExpiryPlan.Member(FIRST, true, 300),
                        new FactionExpiryPlan.Member(SECOND, false, 1))))
                .isEmpty();
    }

    @Test
    void aNegativeRemainingDurationIsNotAnExpirySignal() {
        assertThatThrownBy(() -> new FactionExpiryPlan.Member(FIRST, false, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
