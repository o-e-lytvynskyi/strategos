package dev.strategos.mod.client.menu;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyReply;
import dev.strategos.mod.server.net.LobbyState;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class LobbyClientSessionTest {
    private static LobbyState view(boolean ready) {
        return new LobbyState(
                new LobbyConfiguration(
                        2,
                        List.of(
                                new LobbyConfiguration.Seat(
                                        0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0),
                                new LobbyConfiguration.Seat(
                                        1, LobbyConfiguration.Kind.HUMAN, 1, 1, "strategos:placeholder", 1)),
                        SkirmishSetup.DEFAULTS),
                List.of(new LobbyState.Member(0, "host", ready, true), new LobbyState.Member(1, "peer", false, false)),
                0,
                true,
                9,
                7,
                LobbyState.Phase.EDITING,
                "");
    }

    @Test
    void onlyAnExplicitRequestAcceptsLobbyViewsAndCanonicalRepliesEndWaiting() {
        var session = new LobbyClientSession();
        assertThat(session.accept(view(false))).isFalse();
        assertThat(session.model()).isNull();
        session.beginRequest();
        assertThat(session.waiting()).isTrue();
        assertThat(session.accept(view(false))).isTrue();
        assertThat(session.waiting()).isFalse();
        assertThat(Objects.requireNonNull(session.model()).ownReady()).isFalse();
        session.beginRequest();
        assertThat(Objects.requireNonNull(session.model()).ownReady()).isFalse();
        session.accept(view(true));
        assertThat(Objects.requireNonNull(session.model()).ownReady()).isTrue();
    }

    @Test
    void refusalRetainsTheCanonicalViewAndOnlyExposesABoundedReply() {
        var session = new LobbyClientSession();
        session.beginRequest();
        session.accept(view(false));
        var before = session.model();
        session.beginRequest();
        session.refuse(LobbyReply.REFUSED);
        assertThat(session.model()).isSameAs(before);
        assertThat(session.waiting()).isFalse();
        assertThat(session.error()).isEqualTo(LobbyReply.REFUSED);
        session.beginRequest();
        assertThat(session.error()).isNull();
    }

    @Test
    void leavingOrDisconnectingIgnoresLateStateAndRefusalsUntilANewRequest() {
        var session = new LobbyClientSession();
        session.beginRequest();
        session.accept(view(false));
        session.clear();
        assertThat(session.model()).isNull();
        assertThat(session.waiting()).isFalse();
        assertThat(session.accept(view(true))).isFalse();
        session.refuse(LobbyReply.ERROR);
        assertThat(session.error()).isNull();
        session.beginRequest();
        assertThat(session.accept(view(true))).isTrue();
    }
}
