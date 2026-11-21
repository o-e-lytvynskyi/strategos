package dev.strategos.mod.client.rts;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.model.ClientResume;
import dev.strategos.core.shared.Handle;
import dev.strategos.mod.server.net.GroupRestoreFrame;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ClientResumeSessionTest {
    @Test
    void groupsCreatedDuringTheConfirmedViewSurviveTheFirstPersonRoundTrip() {
        var session = new ClientResumeSession();
        confirm(session, A, 1);
        var newGroups = List.of(List.of(Handle.of(3, 1)));
        session.remember(1, POSE, newGroups);
        assertThat(session.of(1)).isEqualTo(new ClientResume.Saved(POSE, newGroups));
    }

    @Test
    void staleEpochOrWrongMatchCannotValidateGroupsAndReusedSlotsArePruned() {
        var session = new ClientResumeSession();
        confirm(session, A, 1);
        session.remember(1, POSE, GROUPS);
        session.disconnected();
        session.joined(A, 1, 20);
        assertThat(session.accept(new GroupRestoreFrame(10, 1, GROUPS.getFirst())))
                .isFalse();
        assertThat(session.accept(new GroupRestoreFrame(20, 2, GROUPS.getFirst())))
                .isFalse();
        assertThat(session.ready()).isFalse();
        assertThat(session.of(1)).isNull();
        assertThat(session.accept(new GroupRestoreFrame(20, 1, List.of(Handle.of(0, 2)))))
                .isTrue();
        var restored = java.util.Objects.requireNonNull(session.of(1));
        assertThat(restored).isNotNull();
        assertThat(restored.pose()).isEqualTo(POSE);
        assertThat(restored.groups()).containsExactly(List.of());
        assertThat(session.accept(new GroupRestoreFrame(20, 1, GROUPS.getFirst())))
                .isFalse();
        assertThat(session.of(1)).isEqualTo(restored);
    }

    private static void confirm(ClientResumeSession session, ClientResumeSession.@Nullable Scope scope, int matchId) {
        session.joined(scope, matchId, 10);
        session.accept(new GroupRestoreFrame(10, matchId, GROUPS.getFirst()));
    }

    private static final ClientResumeSession.Scope A = new ClientResumeSession.Scope("server-a", new UUID(0, 1));
    private static final ClientResume.Pose POSE = new ClientResume.Pose(12, 18, 40, 30);
    private static final List<List<Integer>> GROUPS = List.of(List.of(1048576));

    @Test
    void reconnectRestoresOnlyAfterTheSameRealmAccountAndMatchAreConfirmed() {
        var session = new ClientResumeSession();
        confirm(session, A, 1);
        session.remember(1, POSE, GROUPS);
        session.disconnected();
        assertThat(session.of(1)).isNull();
        confirm(session, A, 1);
        assertThat(session.of(1)).isEqualTo(new ClientResume.Saved(POSE, GROUPS));
    }

    @Test
    void anotherMatchHasNoStateButReturningToTheFirstKeepsIt() {
        var session = new ClientResumeSession();
        confirm(session, A, 1);
        session.remember(1, POSE, GROUPS);
        confirm(session, A, 2);
        assertThat(session.of(2)).isNull();
        assertThat(session.of(1)).isNull();
        confirm(session, A, 1);
        assertThat(session.of(1)).isNotNull();
    }

    @Test
    void identicalMatchNumbersOnAnotherServerOrAccountCannotRestoreState() {
        for (var other : List.of(
                new ClientResumeSession.Scope("server-b", A.account()),
                new ClientResumeSession.Scope(A.realm(), new UUID(0, 2)))) {
            var session = new ClientResumeSession();
            confirm(session, A, 1);
            session.remember(1, POSE, GROUPS);
            session.disconnected();
            confirm(session, other, 1);
            assertThat(session.of(1)).isNull();
        }
    }

    @Test
    void unconfirmedOrMismatchedSavesCannotOverwriteTheConfirmedMatch() {
        var session = new ClientResumeSession();
        session.remember(1, POSE, GROUPS);
        confirm(session, A, 1);
        assertThat(session.of(1)).isNull();
        session.remember(2, POSE, GROUPS);
        confirm(session, A, 2);
        assertThat(session.of(2)).isNull();
    }

    @Test
    void unknownRealmNeverRestoresAnEarlierView() {
        var session = new ClientResumeSession();
        confirm(session, null, 1);
        session.remember(1, POSE, GROUPS);
        session.disconnected();
        confirm(session, null, 1);
        assertThat(session.of(1)).isNull();
    }
}
