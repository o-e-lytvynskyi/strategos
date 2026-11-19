package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.PlayerSlot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T9.1: queued recipient validity belongs to one live seat, never a global roster revision. */
class MatchRosterGenerationTest {
    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);

    @Test
    void anotherParticipantsAdmissionDoesNotInvalidateTheFirstRecipientsFrames() {
        MatchRoster roster = new MatchRoster();
        assertThat(roster.generationOf(FIRST)).isZero();
        roster.seat(FIRST, new PlayerSlot(0));
        long first = roster.generationOf(FIRST);
        assertThat(first).isPositive();

        roster.seat(SECOND, new PlayerSlot(1));
        roster.connect(FIRST);
        roster.disconnect(SECOND);
        roster.unseat(SECOND);

        assertThat(roster.generationOf(FIRST)).isEqualTo(first);
    }

    @Test
    void disconnectImmediatelyInvalidatesAndReconnectCannotResurrectAnOldFrame() {
        MatchRoster roster = new MatchRoster();
        roster.seat(FIRST, new PlayerSlot(0));
        long before = roster.generationOf(FIRST);
        roster.disconnect(FIRST);
        roster.disconnect(FIRST);
        assertThat(roster.generationOf(FIRST)).isZero();

        roster.connect(FIRST);
        long after = roster.generationOf(FIRST);
        assertThat(after).isGreaterThan(before);
        roster.connect(FIRST);
        assertThat(roster.generationOf(FIRST)).isEqualTo(after);
    }

    @Test
    void unseatReseatAndObserverRoleChangesCannotReuseAFormerRecipientGeneration() {
        MatchRoster roster = new MatchRoster();
        roster.seat(FIRST, new PlayerSlot(0));
        long primary = roster.generationOf(FIRST);
        roster.unseat(FIRST);
        assertThat(roster.generationOf(FIRST)).isZero();
        roster.connect(FIRST);
        assertThat(roster.generationOf(FIRST)).isZero();

        roster.seat(FIRST, new PlayerSlot(0));
        long reseated = roster.generationOf(FIRST);
        assertThat(reseated).isGreaterThan(primary);
        roster.seat(FIRST, PlayerSlot.ADMIN);
        assertThat(roster.generationOf(FIRST)).isGreaterThan(reseated);
    }

    @Test
    void savedMembershipNeverRestoresALiveTransportGeneration() {
        MatchRoster roster = new MatchRoster();
        roster.seat(FIRST, new PlayerSlot(0));
        var saved = roster.saveSessions();
        assertThat(saved.getCompound(0).getAllKeys()).containsExactlyInAnyOrder("uuid", "slot", "remainingNanos");
        MatchRoster restored = MatchRoster.loadSessions(saved);
        assertThat(restored.generationOf(FIRST)).isZero();
        restored.connect(FIRST);
        assertThat(restored.generationOf(FIRST)).isPositive();
    }
}
