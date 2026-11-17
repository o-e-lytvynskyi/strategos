package dev.strategos.mod.client.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyState;
import java.util.List;
import org.junit.jupiter.api.Test;

class LobbyScreenModelTest {
    private static LobbyConfiguration.Seat seat(int player, LobbyConfiguration.Kind kind) {
        return new LobbyConfiguration.Seat(player, kind, player, player, "strategos:placeholder", player);
    }

    private static LobbyState state(
            LobbyState.Phase phase,
            boolean hostReady,
            boolean peerReady,
            int self,
            boolean mayConfigure,
            String reason) {
        return new LobbyState(
                new LobbyConfiguration(
                        2,
                        List.of(seat(0, LobbyConfiguration.Kind.HUMAN), seat(1, LobbyConfiguration.Kind.HUMAN)),
                        SkirmishSetup.DEFAULTS),
                List.of(
                        new LobbyState.Member(0, "host", hostReady, true),
                        new LobbyState.Member(1, "peer", peerReady, false)),
                self,
                mayConfigure,
                9,
                7,
                phase,
                reason);
    }

    @Test
    void startRequiresEditablePermissionAndEveryHumanAcknowledgement() {
        assertThat(new LobbyScreenModel(state(LobbyState.Phase.EDITING, true, false, 0, true, "")).mayStart())
                .isFalse();
        var host = new LobbyScreenModel(state(LobbyState.Phase.EDITING, true, true, 0, true, ""));
        assertThat(host.mayStart()).isTrue();
        assertThat(host.startIntent()).isEqualTo(new LobbyAction.Start());
        assertThat(new LobbyScreenModel(state(LobbyState.Phase.EDITING, true, true, 1, false, "")).mayStart())
                .isFalse();
        // An admitted operator's explicit server permission counts even when it is not the named host.
        assertThat(new LobbyScreenModel(state(LobbyState.Phase.EDITING, true, true, 1, true, "")).mayStart())
                .isTrue();
    }

    @Test
    void emptyHumansBlockStartWhileAiAndClosedSeatsNeedNoAcknowledgement() {
        var config = new LobbyConfiguration(
                4,
                List.of(
                        seat(3, LobbyConfiguration.Kind.CLOSED),
                        seat(1, LobbyConfiguration.Kind.AI),
                        seat(0, LobbyConfiguration.Kind.HUMAN),
                        seat(2, LobbyConfiguration.Kind.HUMAN)),
                SkirmishSetup.DEFAULTS);
        var members = List.of(new LobbyState.Member(0, "host", true, true));
        var view = new LobbyScreenModel(new LobbyState(config, members, 0, true, 9, 7, LobbyState.Phase.EDITING, ""));
        assertThat(view.mayStart()).isFalse();
        assertThat(view.rows().stream().map(r -> r.configuration().player()).toList())
                .containsExactly(0, 1, 2, 3);
        assertThat(view.rows().getFirst().self()).isTrue();
        assertThat(view.rows().getFirst().host()).isTrue();
        assertThat(view.rows().get(2).occupied()).isFalse();
        assertThat(view.rows().get(2).name()).isEmpty();
        var aiOnlyOthers = new LobbyConfiguration(
                2,
                List.of(seat(0, LobbyConfiguration.Kind.HUMAN), seat(1, LobbyConfiguration.Kind.AI)),
                SkirmishSetup.DEFAULTS);
        assertThat(new LobbyScreenModel(
                                new LobbyState(aiOnlyOthers, members, 0, true, 9, 7, LobbyState.Phase.EDITING, ""))
                        .mayStart())
                .isTrue();
    }

    @Test
    void readyIntentUsesOwnReadyAndConfigurationRevisionWithoutOptimisticMutation() {
        var state = state(LobbyState.Phase.EDITING, true, false, 1, false, "");
        var view = new LobbyScreenModel(state);
        assertThat(view.mayReady()).isTrue();
        assertThat(view.ownReady()).isFalse();
        assertThat(view.readyIntent()).isEqualTo(new LobbyAction.Ready(true, 7));
        assertThat(view.state()).isSameAs(state);
        assertThat(view.ownReady()).isFalse();
        assertThat(new LobbyScreenModel(state(LobbyState.Phase.EDITING, true, true, 1, false, "")).readyIntent())
                .isEqualTo(new LobbyAction.Ready(false, 7));
    }

    @Test
    void everyFrozenPhaseDisablesConfigurationReadyAndStart() {
        for (var phase : List.of(
                LobbyState.Phase.PREPARING,
                LobbyState.Phase.STARTED,
                LobbyState.Phase.FAILED,
                LobbyState.Phase.CLOSED)) {
            var view = new LobbyScreenModel(state(phase, true, true, 0, true, ""));
            assertThat(view.mayConfigure()).isFalse();
            assertThat(view.mayReady()).isFalse();
            assertThat(view.mayStart()).isFalse();
            assertThatThrownBy(view::readyIntent).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(view::startIntent).isInstanceOf(IllegalStateException.class);
            assertThat(view.mayLeave()).isEqualTo(phase == LobbyState.Phase.PREPARING);
        }
    }

    @Test
    void rowsAreDetachedAndStatusUsesOnlyKnownTranslationKeys() {
        var view = new LobbyScreenModel(state(LobbyState.Phase.EDITING, true, false, 0, true, "preparation_failed"));
        assertThatThrownBy(() -> view.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(view.statusKey()).isEqualTo("strategos.lobby.reason.preparation_failed");
        assertThat(new LobbyScreenModel(state(LobbyState.Phase.FAILED, true, true, 0, true, "unknown_internal_code"))
                        .statusKey())
                .isEqualTo("strategos.lobby.status.failed");
        assertThat(new LobbyScreenModel(state(LobbyState.Phase.PREPARING, true, true, 0, true, "")).statusKey())
                .isEqualTo("strategos.lobby.status.preparing");
    }
}
