package dev.strategos.core.sim.diplomacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiplomacyStateTest {
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final PlayerSlot P2 = new PlayerSlot(2);
    private static final PlayerSlot P3 = new PlayerSlot(3);
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final FactionSlot F2 = new FactionSlot(2);

    @Test
    void unboundPlayersHaveNoImplicitNumericAuthority() {
        DiplomacyState state = new DiplomacyState(3);
        assertThat(state.factionOf(P0)).isEmpty();
        assertThat(state.controls(P0, F0)).isFalse();
        assertThat(state.controls(P1, F1)).isFalse();
        assertThat(state.allied(F0, F0)).isTrue();
        assertThat(state.allied(F0, F1)).isFalse();
    }

    @Test
    void legacyIdentityIsAnExplicitSetupAndAdminIsNeverAParticipant() {
        DiplomacyState state = DiplomacyState.identity(3);
        for (int p = 0; p <= FactionSlot.MAX; p++) {
            for (int f = 0; f <= FactionSlot.MAX; f++) {
                assertThat(state.controls(new PlayerSlot(p), new FactionSlot(f)))
                        .as("player %s, faction %s", p, f)
                        .isEqualTo(p == f && f < 3);
            }
        }
        assertThat(state.factionOf(PlayerSlot.ADMIN)).isEmpty();
        assertThat(state.controls(PlayerSlot.ADMIN, F2)).isTrue();
        assertThat(state.controls(PlayerSlot.ADMIN, new FactionSlot(3))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> state.bind(PlayerSlot.ADMIN, F0));
        assertThatIllegalArgumentException().isThrownBy(() -> state.grant(PlayerSlot.ADMIN, P1));
        assertThatIllegalArgumentException().isThrownBy(() -> state.grant(P0, PlayerSlot.ADMIN));
    }

    @Test
    void twoIndependentPlayerSeatsCanOwnOneFaction() {
        DiplomacyState state = new DiplomacyState(3).bind(P0, F2).bind(P1, F2);
        assertThat(state.controls(P0, F2)).isTrue();
        assertThat(state.controls(P1, F2)).isTrue();
        assertThat(state.controls(P2, F2)).isFalse();
        assertThat(state.controls(P0, F0)).isFalse();
        assertThat(state.factionOf(P1)).contains(F2);
    }

    @Test
    void teamMembershipIsSymmetricAndDoesNotItselfGrantControl() {
        DiplomacyState state = allies();
        assertThat(state.allied(F0, F1)).isTrue();
        assertThat(state.allied(F1, F0)).isTrue();
        assertThat(state.allied(F0, F2)).isTrue();
        assertThat(state.controls(P0, F1)).isFalse();
        assertThat(state.controls(P1, F0)).isFalse();
        assertThat(state.allied(F0, new FactionSlot(3))).isFalse();
        assertThat(state.allied(new FactionSlot(3), new FactionSlot(3))).isFalse();
    }

    @Test
    void grantsAreDirectionalRevocableAndDoNotChangeTheRecipientsFaction() {
        DiplomacyState state = allies().grant(P0, P1);
        assertThat(state.controls(P1, F0)).isTrue();
        assertThat(state.controls(P0, F1)).isFalse();
        assertThat(state.factionOf(P1)).contains(F1);
        assertThat(state.revoke(P0, P1).controls(P1, F0)).isFalse();
        assertThat(state.controls(P1, F0)).as("old view remains immutable").isTrue();
    }

    @Test
    void aGrantNeverLetsItsRecipientDelegateTheOwnersFaction() {
        DiplomacyState state = allies().grant(P0, P1).grant(P1, P2);
        assertThat(state.controls(P1, F0)).isTrue();
        assertThat(state.controls(P2, F1)).isTrue();
        assertThat(state.controls(P2, F0)).isFalse();
        assertThat(state.revoke(P1, P2).controls(P1, F0)).isTrue();
    }

    @Test
    void anEnemyOrAbsentPlayerCannotReceiveOrIssueAGrant() {
        DiplomacyState state = DiplomacyState.identity(3);
        assertThatIllegalArgumentException().isThrownBy(() -> state.grant(P0, P1));
        assertThatIllegalArgumentException().isThrownBy(() -> state.grant(P0, P3));
        assertThatIllegalArgumentException().isThrownBy(() -> state.grant(P3, P0));
        assertThatIllegalArgumentException().isThrownBy(() -> state.grant(P0, P0));
        assertThat(state.snapshot().grants()).isEmpty();
    }

    @Test
    void anyPrimaryControllerOfTheGrantingFactionCanRevokeItsGrant() {
        DiplomacyState state = allies().bind(P3, F0).grant(P0, P1);
        assertThat(state.revoke(P3, P1).controls(P1, F0)).isFalse();
        assertThat(state.grant(P3, P1).snapshot()).isEqualTo(state.snapshot());
    }

    @Test
    void changingTeamsRevokesInvalidGrantsAndLocksCannotBeBypassed() {
        DiplomacyState state = allies().grant(P0, P1);
        DiplomacyState enemies = state.withTeam(F1, 7);
        assertThat(enemies.controls(P1, F0)).isFalse();
        assertThat(enemies.withTeam(F1, 0).controls(P1, F0)).isFalse();
        DiplomacyState locked = state.lockTeams();
        assertThat(locked.teamsLocked()).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> locked.withTeam(F1, 7));
        assertThat(locked.withTeam(F1, 0).snapshot()).isEqualTo(locked.snapshot());
        assertThat(locked.revoke(P0, P1).controls(P1, F0)).isFalse();
    }

    @Test
    void rebindingCannotCarryOrResurrectAPreviousSeatsGrant() {
        DiplomacyState state = allies().grant(P0, P1);
        DiplomacyState changed = state.bind(P1, F2);
        assertThat(changed.controls(P1, F0)).isFalse();
        assertThat(changed.bind(P1, F1).controls(P1, F0)).isFalse();
        assertThat(state.bind(P1, F1).snapshot()).isEqualTo(state.snapshot());
        assertThat(state.unbind(P1).bind(P1, F1).controls(P1, F0)).isFalse();
    }

    @Test
    void removingTheLastPrimaryControllerRevokesThatFactionsOutgoingGrants() {
        DiplomacyState state = allies().bind(P3, F0).grant(P0, P1);
        assertThat(state.unbind(P0).controls(P1, F0)).isTrue();
        DiplomacyState removed = state.unbind(P0).unbind(P3);
        assertThat(removed.controls(P1, F0)).isFalse();
        assertThat(removed.bind(P0, F0).controls(P1, F0)).isFalse();
    }

    @Test
    void snapshotsAndViewsAreDetachedFromEveryLaterChange() {
        DiplomacyState initial = allies().grant(P0, P1);
        DiplomacyView view = initial;
        DiplomacyState.Snapshot saved = initial.snapshot();
        DiplomacyState changed = initial.revoke(P0, P1).withTeam(F0, 4).unbind(P2);
        assertThat(changed.controls(P1, F0)).isFalse();
        assertThat(view.controls(P1, F0)).isTrue();
        assertThat(view.factionOf(P2)).contains(F2);
        assertThat(DiplomacyState.restore(saved).snapshot()).isEqualTo(saved);
        assertThatThrownBy(() -> saved.teams().add(9)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> saved.bindings().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> saved.grants().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reconstructionCanonicalizesOrderAndPreservesLocksGrantsAndHash() {
        DiplomacyState state = allies().grant(P0, P2).grant(P1, P0).lockTeams();
        DiplomacyState.Snapshot saved = state.snapshot();
        DiplomacyState.Snapshot reversed = new DiplomacyState.Snapshot(
                saved.teams(), saved.bindings().reversed(), saved.grants().reversed(), true);
        DiplomacyState restored = DiplomacyState.restore(reversed);
        assertThat(restored.snapshot()).isEqualTo(saved);
        assertThat(hash(restored)).isEqualTo(hash(state));
        assertThat(hash(state.revoke(P0, P2))).isNotEqualTo(hash(state));
        assertThat(hash(allies())).isNotEqualTo(hash(allies().lockTeams()));
        assertThat(hash(allies())).isNotEqualTo(hash(allies().bind(P0, F2)));
        assertThat(hash(allies())).isNotEqualTo(hash(allies().withTeam(F0, 4)));
    }

    @Test
    void malformedSnapshotsRejectDuplicateBindingsGrantsAndUnknownParticipants() {
        DiplomacyState.Snapshot saved = allies().grant(P0, P1).snapshot();
        List<DiplomacyState.Binding> duplicateBindings =
                List.of(new DiplomacyState.Binding(P0, F0), new DiplomacyState.Binding(P0, F1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiplomacyState.restore(
                        new DiplomacyState.Snapshot(saved.teams(), duplicateBindings, List.of(), false)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiplomacyState.restore(new DiplomacyState.Snapshot(
                        saved.teams(),
                        saved.bindings(),
                        List.of(saved.grants().getFirst(), saved.grants().getFirst()),
                        false)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiplomacyState.restore(new DiplomacyState.Snapshot(
                        saved.teams(), saved.bindings(), List.of(new DiplomacyState.Grant(F0, P3)), false)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiplomacyState.restore(
                        new DiplomacyState.Snapshot(List.of(0, 1, 2), saved.bindings(), saved.grants(), false)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiplomacyState.restore(new DiplomacyState.Snapshot(
                        saved.teams(), List.of(new DiplomacyState.Binding(PlayerSlot.ADMIN, F0)), List.of(), false)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiplomacyState.restore(new DiplomacyState.Snapshot(
                        saved.teams(), List.of(new DiplomacyState.Binding(P0, new FactionSlot(3))), List.of(), false)));
    }

    @Test
    void allSixteenSeatsAndHighBitGrantsWorkWithoutCrossingTheMatchBoundary() {
        PlayerSlot lastPlayer = new PlayerSlot(FactionSlot.MAX);
        FactionSlot lastFaction = new FactionSlot(FactionSlot.MAX);
        DiplomacyState state =
                DiplomacyState.identity(16).withTeam(lastFaction, 0).grant(lastPlayer, P0);
        assertThat(state.controls(P0, lastFaction)).isTrue();
        assertThat(state.controls(lastPlayer, F0)).isFalse();
        assertThat(DiplomacyState.restore(state.snapshot()).controls(P0, lastFaction))
                .isTrue();
        assertThat(state.revoke(lastPlayer, P0).controls(P0, lastFaction)).isFalse();
    }

    @Test
    void constructionAndMutationValidateBoundsWithoutChangingTheOldState() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DiplomacyState(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new DiplomacyState(17));
        DiplomacyState state = allies();
        assertThatIllegalArgumentException().isThrownBy(() -> state.withTeam(F0, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> state.withTeam(F0, 16));
        assertThatIllegalArgumentException().isThrownBy(() -> state.bind(P0, new FactionSlot(3)));
        assertThatIllegalArgumentException().isThrownBy(() -> state.teamOf(new FactionSlot(3)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DiplomacyState.restore(
                        new DiplomacyState.Snapshot(List.of(0, 16), List.of(), List.of(), false)));
        assertThat(state.snapshot()).isEqualTo(allies().snapshot());
    }

    private static DiplomacyState allies() {
        return DiplomacyState.identity(3).withTeam(F1, 0).withTeam(F2, 0);
    }

    private static long hash(DiplomacyState state) {
        Hasher hasher = new Hasher(0);
        state.hashInto(hasher);
        return hasher.finish();
    }
}
