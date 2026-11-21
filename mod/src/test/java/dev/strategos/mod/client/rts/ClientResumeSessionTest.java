package dev.strategos.mod.client.rts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.model.ClientResume;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientResumeSessionTest {
    private static final ClientResumeSession.Scope A = new ClientResumeSession.Scope("server-a", new UUID(0, 1));
    private static final ClientResume.Pose POSE = new ClientResume.Pose(12, 18, 40, 30);
    private static final List<List<Integer>> GROUPS = List.of(List.of(1048576));

    @Test
    void reconnectRestoresOnlyAfterTheSameRealmAccountAndMatchAreConfirmed() {
        var session = new ClientResumeSession();
        session.joined(A, 1);
        session.remember(1, POSE, GROUPS);
        session.disconnected();
        assertThat(session.of(1)).isNull();
        session.joined(A, 1);
        assertThat(session.of(1)).isEqualTo(new ClientResume.Saved(POSE, GROUPS));
    }

    @Test
    void anotherMatchHasNoStateButReturningToTheFirstKeepsIt() {
        var session = new ClientResumeSession();
        session.joined(A, 1);
        session.remember(1, POSE, GROUPS);
        session.joined(A, 2);
        assertThat(session.of(2)).isNull();
        assertThat(session.of(1)).isNull();
        session.joined(A, 1);
        assertThat(session.of(1)).isNotNull();
    }

    @Test
    void identicalMatchNumbersOnAnotherServerOrAccountCannotRestoreState() {
        for (var other : List.of(
                new ClientResumeSession.Scope("server-b", A.account()),
                new ClientResumeSession.Scope(A.realm(), new UUID(0, 2)))) {
            var session = new ClientResumeSession();
            session.joined(A, 1);
            session.remember(1, POSE, GROUPS);
            session.disconnected();
            session.joined(other, 1);
            assertThat(session.of(1)).isNull();
        }
    }

    @Test
    void unconfirmedOrMismatchedSavesCannotOverwriteTheConfirmedMatch() {
        var session = new ClientResumeSession();
        session.remember(1, POSE, GROUPS);
        session.joined(A, 1);
        assertThat(session.of(1)).isNull();
        session.remember(2, POSE, GROUPS);
        session.joined(A, 2);
        assertThat(session.of(2)).isNull();
    }

    @Test
    void unknownRealmNeverRestoresAnEarlierView() {
        var session = new ClientResumeSession();
        session.joined(null, 1);
        session.remember(1, POSE, GROUPS);
        session.disconnected();
        session.joined(null, 1);
        assertThat(session.of(1)).isNull();
    }
}
