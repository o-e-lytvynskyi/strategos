package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.PlayerSlot;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** T5.2: only remaining monotonic duration crosses a save boundary, never a live connection. */
class MatchRosterSessionsTest {
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final long WINDOW = 10_000;

    @Test
    void legacyMembershipRestoresDisconnectedWithTheConfiguredWindow() {
        MatchRoster old = new MatchRoster();
        old.seat(MEMBER, new PlayerSlot(0));
        AtomicLong clock = new AtomicLong(7_000_000);

        MatchRoster restored = MatchRoster.load(old.save(), clock::get, WINDOW);

        assertThat(restored.slotOf(MEMBER)).isEqualTo(new PlayerSlot(0));
        assertThat(restored.isConnected(MEMBER)).isFalse();
        assertThat(restored.remainingNanos(MEMBER)).isEqualTo(WINDOW);
        clock.addAndGet(3_000);
        assertThat(restored.remainingNanos(MEMBER)).isEqualTo(7_000);
    }

    @Test
    void aLiveConnectionIsNotRestoredAndNoAbsoluteClockOriginIsPersisted() {
        AtomicLong clock = new AtomicLong(8_000_000);
        MatchRoster live = new MatchRoster(clock::get, WINDOW);
        live.seat(MEMBER, new PlayerSlot(1));
        assertThat(live.isConnected(MEMBER)).isTrue();
        clock.addAndGet(900_000);
        ListTag saved = live.saveSessions();
        assertThat(saved.getCompound(0).getAllKeys()).containsExactlyInAnyOrder("uuid", "slot", "remainingNanos");

        AtomicLong restartedClock = new AtomicLong(-500_000);
        MatchRoster back = MatchRoster.loadSessions(saved, restartedClock::get, WINDOW);
        assertThat(back.isConnected(MEMBER)).isFalse();
        assertThat(back.remainingNanos(MEMBER)).isEqualTo(WINDOW);
        assertThat(back.slotOf(MEMBER)).isEqualTo(new PlayerSlot(1));
    }

    @Test
    void disconnectIsIdempotentAndSaveRestartPreservesTheRemainingWindow() {
        AtomicLong clock = new AtomicLong(100);
        MatchRoster live = new MatchRoster(clock::get, WINDOW);
        live.seat(MEMBER, new PlayerSlot(0));
        live.disconnect(MEMBER);
        clock.addAndGet(2_000);
        live.disconnect(MEMBER);
        assertThat(live.remainingNanos(MEMBER)).isEqualTo(8_000);

        AtomicLong restartedClock = new AtomicLong(Long.MAX_VALUE - 2_000);
        MatchRoster back = MatchRoster.loadSessions(live.saveSessions(), restartedClock::get, WINDOW);
        assertThat(back.remainingNanos(MEMBER)).isEqualTo(8_000);
        restartedClock.addAndGet(3_000); // nanoTime's arbitrary origin may wrap; elapsed time is still 3,000.
        assertThat(back.remainingNanos(MEMBER)).isEqualTo(5_000);
        restartedClock.addAndGet(5_000);
        assertThat(back.remainingNanos(MEMBER)).isZero();
        restartedClock.addAndGet(1_000);
        assertThat(back.remainingNanos(MEMBER)).isZero();
        MatchRoster expired = MatchRoster.loadSessions(back.saveSessions(), () -> 0, WINDOW);
        assertThat(expired.remainingNanos(MEMBER)).isZero();
        assertThat(expired.isConnected(MEMBER)).isFalse();
    }

    @Test
    void currentSessionDurationsAreRequiredTypedAndBounded() {
        MatchRoster live = new MatchRoster(() -> 0, WINDOW);
        live.seat(MEMBER, new PlayerSlot(0));
        ListTag missing = live.saveSessions();
        missing.getCompound(0).remove("remainingNanos");
        assertThatThrownBy(() -> MatchRoster.loadSessions(missing, () -> 0, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        ListTag wrongType = live.saveSessions();
        wrongType.getCompound(0).putString("remainingNanos", "10000");
        assertThatThrownBy(() -> MatchRoster.loadSessions(wrongType, () -> 0, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        for (long invalid : new long[] {-1, Long.MAX_VALUE}) {
            ListTag malformed = live.saveSessions();
            malformed.getCompound(0).putLong("remainingNanos", invalid);
            assertThatThrownBy(() -> MatchRoster.loadSessions(malformed, () -> 0, WINDOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
