package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.mod.server.match.LobbyService;
import dev.strategos.mod.server.match.MatchLobby;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LobbyIntentsTest {
    private static final UUID HOST = new UUID(0, 1);
    private static final UUID PEER = new UUID(0, 2);
    private static final UUID OUTSIDER = new UUID(0, 3);

    private static final class Backend implements LobbyService.Backend {
        final List<UUID> recipients = new ArrayList<>();

        @Override
        public boolean mayHost(UUID member) {
            return member.equals(HOST);
        }

        @Override
        public boolean online(UUID member) {
            return true;
        }

        @Override
        public boolean seated(UUID member) {
            return false;
        }

        @Override
        public boolean activeMatch() {
            return false;
        }

        @Override
        public void validate(MatchLobby lobby, SkirmishSetup setup) {}

        @Override
        public LobbyService.Preparation begin(MatchLobby lobby, SkirmishSetup setup) {
            throw new AssertionError("this test must not allocate");
        }

        @Override
        public void send(UUID member, LobbyService.Snapshot state) {
            recipients.add(member);
        }
    }

    private static LobbyConfiguration config(int cap) {
        return new LobbyConfiguration(
                2,
                List.of(
                        new LobbyConfiguration.Seat(0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0),
                        new LobbyConfiguration.Seat(
                                1, LobbyConfiguration.Kind.HUMAN, 1, 1, "strategos:placeholder", 1)),
                new SkirmishSetup(
                        SkirmishSetup.FIRST_SKIRMISH,
                        SkirmishSetup.Level.NORMAL,
                        SkirmishSetup.StartResources.MAP,
                        cap,
                        SkirmishSetup.Visibility.FOG));
    }

    @Test
    void senderOwnsItsJoinAndConfigurationEditsRetainAuthenticatedOccupants() {
        var backend = new Backend();
        var service = new LobbyService(backend);
        LobbyIntents.accept(service, HOST, new LobbyAction.Open(config(150)));
        assertThat(service.snapshot().lobby().seats().get(0).occupant()).isEqualTo(HOST);
        assertThat(service.snapshot().lobby().seats().get(1).occupant()).isNull();
        assertThatThrownBy(() -> LobbyIntents.accept(service, PEER, new LobbyAction.Join(0)))
                .isInstanceOf(IllegalArgumentException.class);
        LobbyIntents.accept(service, PEER, new LobbyAction.Join(1));
        LobbyIntents.accept(service, HOST, new LobbyAction.Edit(config(200)));
        assertThat(service.snapshot().lobby().seats().stream()
                        .map(MatchLobby.Seat::occupant)
                        .toList())
                .containsExactly(HOST, PEER);
        assertThat(service.snapshot().setup().populationCap()).isEqualTo(200);
        assertThat(backend.recipients).contains(HOST, PEER).doesNotContain(OUTSIDER);
    }

    @Test
    void staleReadinessAndUnadmittedIntentsCannotMutateTheLobby() {
        var service = new LobbyService(new Backend());
        LobbyIntents.accept(service, HOST, new LobbyAction.Open(config(150)));
        LobbyIntents.accept(service, PEER, new LobbyAction.Join(1));
        long revision = service.snapshot().lobby().configurationRevision();
        LobbyIntents.accept(service, PEER, new LobbyAction.Ready(true, revision));
        LobbyIntents.accept(service, HOST, new LobbyAction.Edit(config(200)));
        var before = service.snapshot();
        for (LobbyAction intent : List.of(
                new LobbyAction.Ready(true, revision),
                new LobbyAction.Leave(),
                new LobbyAction.Edit(config(150)),
                new LobbyAction.Start())) {
            assertThatThrownBy(() -> LobbyIntents.accept(service, OUTSIDER, intent))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(service.snapshot()).isSameAs(before);
        }
        assertThatThrownBy(() -> LobbyIntents.accept(service, PEER, new LobbyAction.Ready(true, revision)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.snapshot().lobby().readyPlayers()).isEmpty();
    }

    @Test
    void projectionRejectsOutsidersAndUsesRecipientSeatWithoutSendingUuids() {
        var service = new LobbyService(new Backend());
        LobbyIntents.accept(service, HOST, new LobbyAction.Open(config(150)));
        LobbyIntents.accept(service, PEER, new LobbyAction.Join(1));
        LobbyState view = LobbyViews.project(
                service.snapshot(), PEER, HOST::equals, false, member -> member.equals(HOST) ? "host" : "peer");
        assertThat(view.self()).isEqualTo(1);
        assertThat(view.members())
                .containsExactly(
                        new LobbyState.Member(0, "host", false, true), new LobbyState.Member(1, "peer", false, false));
        String bytes = new String(WireCodec.encode(LobbyState.WIRE, view), StandardCharsets.ISO_8859_1);
        assertThat(bytes).doesNotContain(HOST.toString(), PEER.toString());
        assertThatThrownBy(() -> LobbyViews.project(service.snapshot(), OUTSIDER, HOST::equals, false, member -> {
                    throw new AssertionError("outsider projection must fail before looking up names");
                }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
