package dev.strategos.mod.server.net;

import dev.strategos.mod.server.match.LobbyService;
import dev.strategos.mod.server.match.MatchLobby;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Private projection: prove recipient membership before reading any display names. */
public final class LobbyViews {
    private LobbyViews() {}

    public static LobbyState project(
            LobbyService.Snapshot snapshot,
            UUID recipient,
            Predicate<UUID> isHost,
            boolean mayConfigure,
            Function<UUID, String> names) {
        MatchLobby lobby = snapshot.lobby();
        int self = lobby.seats().stream()
                .filter(s -> recipient.equals(s.occupant()))
                .mapToInt(s -> s.player().value())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("recipient is not a lobby member"));
        var seats = lobby.seats().stream()
                .map(s -> new LobbyConfiguration.Seat(
                        s.player().value(),
                        switch (s.kind()) {
                            case HUMAN -> LobbyConfiguration.Kind.HUMAN;
                            case AI -> LobbyConfiguration.Kind.AI;
                            case CLOSED -> LobbyConfiguration.Kind.CLOSED;
                        },
                        s.faction().value(),
                        s.team(),
                        s.civilisation(),
                        s.colour()))
                .toList();
        var config = new LobbyConfiguration(lobby.factionCapacity(), seats, snapshot.setup());
        var members = new ArrayList<LobbyState.Member>();
        for (MatchLobby.Seat seat : lobby.seats()) {
            UUID occupant = seat.occupant();
            if (occupant != null)
                members.add(new LobbyState.Member(
                        seat.player().value(),
                        Objects.requireNonNull(names.apply(occupant)),
                        lobby.readyPlayers().contains(seat.player()),
                        isHost.test(occupant)));
        }
        LobbyState.Phase phase = switch (snapshot.state()) {
            case EDITING -> LobbyState.Phase.EDITING;
            case PREPARING -> LobbyState.Phase.PREPARING;
            case STARTED -> LobbyState.Phase.STARTED;
            case FAILED -> LobbyState.Phase.FAILED;
            case CLOSED -> LobbyState.Phase.CLOSED;
        };
        return new LobbyState(
                config,
                members,
                self,
                mayConfigure,
                lobby.revision(),
                lobby.configurationRevision(),
                phase,
                snapshot.message());
    }
}
