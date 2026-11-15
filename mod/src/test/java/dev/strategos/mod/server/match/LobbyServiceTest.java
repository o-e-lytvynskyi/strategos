package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LobbyServiceTest {
    private static final UUID HOST = new UUID(0, 1);
    private static final UUID PEER = new UUID(0, 2);
    private static final UUID OUTSIDER = new UUID(0, 3);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);

    private static MatchLobby.Seat human(PlayerSlot player, @Nullable UUID member) {
        return new MatchLobby.Seat(
                player,
                MatchLobby.Kind.HUMAN,
                member,
                new FactionSlot(player.value()),
                player.value(),
                "strategos:placeholder",
                player.value());
    }

    private static final class Backend implements LobbyService.Backend {
        final Set<UUID> online = new HashSet<>(Set.of(HOST, PEER, OUTSIDER));
        final List<UUID> recipients = new ArrayList<>();
        final List<LobbyService.Snapshot> updates = new ArrayList<>();
        boolean active;
        boolean invalid;
        boolean fail;
        boolean cancelled;
        int starts;
        LobbyService.Preparation.Status status = LobbyService.Preparation.Status.PREPARING;

        @Override
        public boolean mayHost(UUID member) {
            return member.equals(HOST);
        }

        @Override
        public boolean online(UUID member) {
            return online.contains(member);
        }

        @Override
        public boolean seated(UUID member) {
            return false;
        }

        @Override
        public boolean activeMatch() {
            return active;
        }

        @Override
        public void validate(MatchLobby lobby, SkirmishSetup setup) {
            if (invalid) throw new IllegalArgumentException("unsupported map capacity");
        }

        @Override
        public LobbyService.Preparation begin(MatchLobby lobby, SkirmishSetup setup) {
            starts++;
            if (fail) throw new IllegalStateException("preparation failed");
            return new LobbyService.Preparation() {
                @Override
                public Status poll() {
                    return status;
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            };
        }

        @Override
        public void send(UUID member, LobbyService.Snapshot snapshot) {
            recipients.add(member);
            updates.add(snapshot);
        }
    }

    private static LobbyService open(Backend backend) {
        LobbyService service = new LobbyService(backend);
        service.open(HOST, 2, List.of(human(P0, HOST), human(P1, null)), SkirmishSetup.DEFAULTS);
        return service;
    }

    private static void ready(LobbyService service) {
        service.join(PEER, P1);
        long revision = service.snapshot().lobby().configurationRevision();
        service.ready(HOST, true, revision);
        service.ready(PEER, true, revision);
    }

    @Test
    void onlyHostAuthorityCanOpenAndOnlyMembersReceiveState() {
        Backend backend = new Backend();
        LobbyService unopened = new LobbyService(backend);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> unopened.open(OUTSIDER, 2, List.of(human(P0, OUTSIDER)), SkirmishSetup.DEFAULTS));
        LobbyService service = open(backend);
        service.join(PEER, P1);
        assertThat(backend.recipients).contains(HOST, PEER).doesNotContain(OUTSIDER);
    }

    @Test
    void outsidersCannotReadyEditOrStartTheLobby() {
        Backend backend = new Backend();
        LobbyService service = open(backend);
        assertThatIllegalArgumentException().isThrownBy(() -> service.ready(OUTSIDER, true, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> service.edit(OUTSIDER, service.snapshot().lobby().seats(), SkirmishSetup.DEFAULTS));
        assertThatIllegalArgumentException().isThrownBy(() -> service.start(OUTSIDER));
        assertThat(backend.starts).isZero();
    }

    @Test
    void joiningCannotReplaceAnOccupantOrTakeTwoSeats() {
        Backend backend = new Backend();
        LobbyService service = open(backend);
        assertThatIllegalArgumentException().isThrownBy(() -> service.join(OUTSIDER, P0));
        service.join(PEER, P1);
        assertThatIllegalArgumentException().isThrownBy(() -> service.join(PEER, P0));
        assertThat(service.snapshot().lobby().seats().get(0).occupant()).isEqualTo(HOST);
    }

    @Test
    void editsInvalidateReadinessAndCannotImpersonateMembers() {
        Backend backend = new Backend();
        LobbyService service = open(backend);
        ready(service);
        var before = service.snapshot();
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        service.edit(HOST, List.of(human(P0, HOST), human(P1, OUTSIDER)), SkirmishSetup.DEFAULTS));
        assertThat(service.snapshot()).isEqualTo(before);
        var changed = new SkirmishSetup(
                SkirmishSetup.FIRST_SKIRMISH,
                SkirmishSetup.Level.HARD,
                SkirmishSetup.StartResources.MAP,
                100,
                SkirmishSetup.Visibility.FOG);
        service.edit(HOST, before.lobby().seats(), changed);
        assertThat(service.snapshot().lobby().readyPlayers()).isEmpty();
        assertThatIllegalStateException()
                .isThrownBy(() -> service.ready(PEER, true, before.lobby().configurationRevision()));
    }

    @Test
    void startRequiresAllReadyAndValidatedSetupBeforeAllocation() {
        Backend backend = new Backend();
        LobbyService service = open(backend);
        assertThatIllegalStateException().isThrownBy(() -> service.start(HOST));
        ready(service);
        backend.invalid = true;
        assertThatIllegalArgumentException().isThrownBy(() -> service.start(HOST));
        assertThat(backend.starts).isZero();
        assertThat(service.snapshot().lobby().phase()).isEqualTo(MatchLobby.Phase.EDITING);
    }

    @Test
    void preparationFreezesSettingsAndRunsOnce() {
        Backend backend = new Backend();
        LobbyService service = open(backend);
        ready(service);
        service.start(HOST);
        assertThatIllegalStateException().isThrownBy(() -> service.start(HOST));
        assertThatIllegalStateException()
                .isThrownBy(() -> service.edit(HOST, service.snapshot().lobby().seats(), SkirmishSetup.DEFAULTS));
        assertThat(backend.starts).isEqualTo(1);
        backend.status = LobbyService.Preparation.Status.COMPLETE;
        service.tick();
        assertThat(service.snapshot().state()).isEqualTo(LobbyService.State.STARTED);
    }

    @Test
    void failedAllocationRollsBackAndRequiresFreshAcknowledgements() {
        Backend backend = new Backend();
        LobbyService service = open(backend);
        ready(service);
        long revision = service.snapshot().lobby().configurationRevision();
        backend.fail = true;
        service.start(HOST);
        assertThat(service.snapshot().state()).isEqualTo(LobbyService.State.EDITING);
        assertThat(service.snapshot().lobby().readyPlayers()).isEmpty();
        assertThat(service.snapshot().lobby().configurationRevision()).isGreaterThan(revision);
        assertThat(service.snapshot().message()).isNotBlank();
    }

    @Test
    void failedOrDisconnectedPreparationCancelsPartialWorldBeforeRollback() {
        Backend backend = new Backend();
        LobbyService service = open(backend);
        ready(service);
        service.start(HOST);
        backend.online.remove(PEER);
        service.tick();
        assertThat(backend.cancelled).isTrue();
        assertThat(service.snapshot().state()).isEqualTo(LobbyService.State.EDITING);
        assertThat(service.snapshot().lobby().readyPlayers()).isEmpty();
    }

    @Test
    void oneActiveMatchPreventsOpeningOrStartingAnother() {
        Backend backend = new Backend();
        backend.active = true;
        assertThatIllegalStateException().isThrownBy(() -> open(backend));
        backend.active = false;
        LobbyService service = open(backend);
        ready(service);
        backend.active = true;
        assertThatIllegalStateException().isThrownBy(() -> service.start(HOST));
        assertThat(backend.starts).isZero();
    }
}
