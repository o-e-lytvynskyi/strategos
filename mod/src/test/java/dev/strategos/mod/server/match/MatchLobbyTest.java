package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The independent pre-match aggregate, not network admission or a running-match start. */
class MatchLobbyTest {
    private static final UUID ANN = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID EVE = new UUID(0, 3);
    private static final String CIV = "strategos:civ/test";

    private static MatchLobby.Seat human(int player, UUID member, int faction) {
        return seat(player, MatchLobby.Kind.HUMAN, member, faction, faction, faction);
    }

    private static MatchLobby.Seat seat(
            int player, MatchLobby.Kind kind, @Nullable UUID member, int faction, int team, int colour) {
        return new MatchLobby.Seat(new PlayerSlot(player), kind, member, new FactionSlot(faction), team, CIV, colour);
    }

    private static MatchLobby lobby() {
        return MatchLobby.create(2, List.of(human(0, ANN, 0), human(1, BOB, 1)));
    }

    private static MatchLobby ready() {
        return lobby().withReady(ANN, true).withReady(BOB, true);
    }

    @Test
    void configurationIsDetachedCanonicalAndUnmodifiable() {
        var input = new ArrayList<>(List.of(human(1, BOB, 1), human(0, ANN, 0)));
        MatchLobby state = MatchLobby.create(2, input);
        input.clear();
        assertThat(state.seats()).extracting(s -> s.player().value()).containsExactly(0, 1);
        assertThat(state.readyPlayers()).isEmpty();
        assertThat(state.revision()).isZero();
        assertThat(state.phase()).isEqualTo(MatchLobby.Phase.EDITING);
        assertThatThrownBy(() -> state.seats().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void seatsCapacityAndPlayerSlotsAreBounded() {
        assertThatIllegalArgumentException().isThrownBy(() -> MatchLobby.create(0, lobby().seats()));
        assertThatIllegalArgumentException().isThrownBy(() -> MatchLobby.create(17, lobby().seats()));
        assertThatIllegalArgumentException().isThrownBy(() -> MatchLobby.create(2, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> MatchLobby.create(2, List.of(human(4, ANN, 0))));
        assertThatIllegalArgumentException().isThrownBy(() -> MatchLobby.create(1, lobby().seats()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchLobby.Seat(
                        PlayerSlot.ADMIN, MatchLobby.Kind.HUMAN, ANN, new FactionSlot(0), 0, CIV, 0));
    }

    @Test
    void configurationRejectsDuplicateMembersAndConflictingFactionProperties() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchLobby.create(2, List.of(human(0, ANN, 0), human(0, BOB, 1))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchLobby.create(2, List.of(human(0, ANN, 0), human(1, ANN, 1))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lobby().withSeat(seat(1, MatchLobby.Kind.HUMAN, BOB, 0, 1, 0)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lobby().withSeat(seat(1, MatchLobby.Kind.HUMAN, BOB, 0, 0, 1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lobby().withSeat(seat(1, MatchLobby.Kind.HUMAN, BOB, 1, 1, 0)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchLobby.Seat(
                        new PlayerSlot(0), MatchLobby.Kind.HUMAN, ANN, new FactionSlot(0), 16, CIV, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchLobby.Seat(
                        new PlayerSlot(0), MatchLobby.Kind.HUMAN, ANN, new FactionSlot(0), 0, "INVALID", 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchLobby.Seat(
                        new PlayerSlot(0), MatchLobby.Kind.HUMAN, ANN, new FactionSlot(0), 0, CIV, 16));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatchLobby.Seat(
                        new PlayerSlot(0),
                        MatchLobby.Kind.HUMAN,
                        ANN,
                        new FactionSlot(0),
                        0,
                        "a:" + "b".repeat(127),
                        0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchLobby.create(
                        2,
                        List.of(
                                human(0, ANN, 0),
                                new MatchLobby.Seat(
                                        new PlayerSlot(1),
                                        MatchLobby.Kind.HUMAN,
                                        BOB,
                                        new FactionSlot(0),
                                        0,
                                        "strategos:civ/other",
                                        0))));
    }

    @Test
    void onlyHumanSeatsHaveUuidOccupants() {
        assertThatIllegalArgumentException().isThrownBy(() -> seat(0, MatchLobby.Kind.AI, ANN, 0, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> seat(0, MatchLobby.Kind.CLOSED, ANN, 0, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> lobby().withReady(EVE, true));
    }

    @Test
    void readyUpdatesAreIndividualAndOldViewsStayDetached() {
        MatchLobby before = lobby();
        MatchLobby one = before.withReady(ANN, true);
        MatchLobby both = one.withReady(BOB, true);
        assertThat(before.readyPlayers()).isEmpty();
        assertThat(one.readyPlayers()).containsExactly(new PlayerSlot(0));
        assertThat(both.readyPlayers()).containsExactlyInAnyOrder(new PlayerSlot(0), new PlayerSlot(1));
        assertThat(both.allHumansReady()).isTrue();
        assertThat(both.withReady(ANN, false).readyPlayers()).containsExactly(new PlayerSlot(1));
        assertThatThrownBy(() -> both.readyPlayers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void realConfigurationChangesInvalidateEveryReadyAcknowledgement() {
        MatchLobby before = ready();
        MatchLobby changed = before.withSeat(seat(0, MatchLobby.Kind.HUMAN, ANN, 0, 0, 3));
        assertThat(changed.readyPlayers()).isEmpty();
        assertThat(changed.revision()).isEqualTo(before.revision() + 1);
        assertThat(before.allHumansReady()).isTrue();
        assertThat(before.withSeat(human(0, EVE, 0)).readyPlayers()).isEmpty();
        assertThat(before.settingsChanged().readyPlayers()).isEmpty();
        assertThat(before.withFactionCapacity(3).readyPlayers()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> before.withFactionCapacity(1));
    }

    @Test
    void noOpUpdatesKeepReadinessAndRevision() {
        MatchLobby before = ready();
        assertThat(before.withSeat(human(0, ANN, 0))).isSameAs(before);
        assertThat(before.withSeats(before.seats().reversed())).isSameAs(before);
        assertThat(before.withReady(ANN, true)).isSameAs(before);
        assertThat(before.withFactionCapacity(2)).isSameAs(before);
        assertThat(lobby().settingsChanged().revision()).isEqualTo(1);
    }

    @Test
    void anEmptyHumanSeatBlocksReadinessAndAiDoesNotNeedAnAcknowledgement() {
        MatchLobby open =
                MatchLobby.create(2, List.of(human(0, ANN, 0), seat(1, MatchLobby.Kind.HUMAN, null, 1, 1, 1)));
        assertThat(open.withReady(ANN, true).allHumansReady()).isFalse();
        MatchLobby ai = open.withSeat(seat(1, MatchLobby.Kind.AI, null, 1, 1, 1));
        assertThat(ai.withReady(ANN, true).allHumansReady()).isTrue();
        assertThat(MatchLobby.create(2, List.of(seat(0, MatchLobby.Kind.AI, null, 0, 0, 0)))
                        .allHumansReady())
                .isFalse();
    }

    @Test
    void coOpMembersUseDifferentPlayersAndOneConsistentFactionConfiguration() {
        MatchLobby state = MatchLobby.create(
                2, List.of(human(0, ANN, 0), human(1, BOB, 0), seat(2, MatchLobby.Kind.AI, null, 1, 1, 1)));
        assertThat(state.withReady(ANN, true).withReady(BOB, true).allHumansReady())
                .isTrue();
        assertThat(state.seats()).extracting(s -> s.faction().value()).containsExactly(0, 0, 1);
    }

    @Test
    void preparationFreezesConfigurationAndRollbackRequiresFreshReadyAcknowledgements() {
        assertThatIllegalStateException().isThrownBy(() -> lobby().prepare());
        MatchLobby before = ready();
        MatchLobby pending = before.prepare();
        assertThat(pending.phase()).isEqualTo(MatchLobby.Phase.PREPARING);
        assertThat(before.phase()).isEqualTo(MatchLobby.Phase.EDITING);
        assertThatIllegalStateException().isThrownBy(() -> pending.withReady(ANN, false));
        assertThatIllegalStateException().isThrownBy(() -> pending.withSeat(human(0, ANN, 0)));
        assertThatIllegalStateException().isThrownBy(() -> pending.withSeats(pending.seats()));
        assertThatIllegalStateException().isThrownBy(pending::settingsChanged);
        assertThatIllegalStateException().isThrownBy(() -> pending.withFactionCapacity(2));
        assertThatIllegalStateException().isThrownBy(pending::prepare);
        MatchLobby failed = pending.preparationFailed();
        assertThat(failed.phase()).isEqualTo(MatchLobby.Phase.EDITING);
        assertThat(failed.readyPlayers()).isEmpty();
        assertThat(failed.revision()).isEqualTo(pending.revision() + 1);
        assertThatIllegalStateException().isThrownBy(failed::prepare);
        assertThatIllegalStateException().isThrownBy(before::preparationFailed);
    }
}
