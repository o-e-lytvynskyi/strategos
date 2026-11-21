package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.PlayerSlot;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A strict monotonic reclaim deadline is not extended by a denied or replayed lifecycle event. */
class ReconnectAdmissionTest {
    private static final UUID MEMBER = new UUID(0, 1);
    private static final long WINDOW = 1000;

    @Test
    void oneNanosecondBeforeExpiryReclaimsTheSameSlotAndStartsANewConnectionWindow() {
        var clock = new AtomicLong();
        var roster = reserved(clock);
        clock.set(WINDOW - 1);
        roster.connect(MEMBER);
        assertThat(roster.isConnected(MEMBER)).isTrue();
        assertThat(roster.slotOf(MEMBER)).isEqualTo(new PlayerSlot(0));
        assertThat(roster.generationOf(MEMBER)).isPositive();
        roster.disconnect(MEMBER);
        assertThat(roster.remainingNanos(MEMBER)).isEqualTo(WINDOW);
    }

    @ParameterizedTest
    @ValueSource(longs = {WINDOW, WINDOW + 1})
    void atOrAfterTheDeadlineCannotResurrectOrRestartTheReservation(long elapsed) {
        var clock = new AtomicLong();
        var roster = reserved(clock);
        clock.set(elapsed);
        roster.connect(MEMBER);
        assertThat(roster.isConnected(MEMBER)).isFalse();
        assertThat(roster.generationOf(MEMBER)).isZero();
        assertThat(roster.remainingNanos(MEMBER)).isZero();
        assertThat(roster.slotOf(MEMBER)).isEqualTo(new PlayerSlot(0));
        roster.disconnect(MEMBER);
        clock.incrementAndGet();
        roster.connect(MEMBER);
        assertThat(roster.isConnected(MEMBER)).isFalse();
        assertThat(roster.remainingNanos(MEMBER)).isZero();
    }

    @Test
    void aRestoredZeroRemainingReservationCannotBeRevivedByLogin() {
        var clock = new AtomicLong();
        var roster = reserved(clock);
        clock.set(WINDOW);
        var restartedClock = new AtomicLong(-100_000);
        var restored = MatchRoster.loadSessions(roster.saveSessions(), restartedClock::get, WINDOW);
        assertThat(restored.remainingNanos(MEMBER)).isZero();
        restored.connect(MEMBER);
        assertThat(restored.isConnected(MEMBER)).isFalse();
        assertThat(restored.generationOf(MEMBER)).isZero();
        assertThat(restored.slotOf(MEMBER)).isEqualTo(new PlayerSlot(0));
    }

    private static MatchRoster reserved(AtomicLong clock) {
        var roster = new MatchRoster(clock::get, WINDOW);
        roster.seat(MEMBER, new PlayerSlot(0));
        roster.disconnect(MEMBER);
        return roster;
    }
}
