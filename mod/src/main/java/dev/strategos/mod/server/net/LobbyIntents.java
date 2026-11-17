package dev.strategos.mod.server.net;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.mod.server.match.LobbyService;
import dev.strategos.mod.server.match.MatchLobby;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Translates bounded intents using only the connection's sender and server-owned occupants. */
public final class LobbyIntents {
    private LobbyIntents() {}

    public static void accept(LobbyService service, UUID sender, LobbyAction action) {
        switch (action) {
            case LobbyAction.Open open -> {
                LobbyConfiguration config = open.configuration();
                int hostSeat = config.seats().stream()
                        .filter(s -> s.kind() == LobbyConfiguration.Kind.HUMAN)
                        .mapToInt(LobbyConfiguration.Seat::player)
                        .min()
                        .orElseThrow(() -> new IllegalArgumentException("lobby needs a host seat"));
                var seats = config.seats().stream()
                        .map(s -> new MatchLobby.Seat(
                                new PlayerSlot(s.player()),
                                LobbyConfiguration.matchKind(s.kind()),
                                s.player() == hostSeat ? sender : null,
                                new FactionSlot(s.faction()),
                                s.team(),
                                s.civilisation(),
                                s.colour()))
                        .toList();
                service.open(sender, config.factionCapacity(), seats, config.setup());
            }
            case LobbyAction.Edit edit -> {
                LobbyConfiguration config = edit.configuration();
                MatchLobby before = service.snapshot().lobby();
                if (config.factionCapacity() != before.factionCapacity()) {
                    throw new IllegalArgumentException("cannot replace lobby faction capacity");
                }
                Map<Integer, MatchLobby.Seat> previous = new HashMap<>();
                for (MatchLobby.Seat seat : before.seats())
                    previous.put(seat.player().value(), seat);
                var seats = config.seats().stream()
                        .map(s -> {
                            MatchLobby.Seat old = previous.get(s.player());
                            return new MatchLobby.Seat(
                                    new PlayerSlot(s.player()),
                                    LobbyConfiguration.matchKind(s.kind()),
                                    old == null ? null : old.occupant(),
                                    new FactionSlot(s.faction()),
                                    s.team(),
                                    s.civilisation(),
                                    s.colour());
                        })
                        .toList();
                service.edit(sender, seats, config.setup());
            }
            case LobbyAction.Join join -> service.join(sender, new PlayerSlot(join.player()));
            case LobbyAction.JoinAvailable ignored -> service.joinAvailable(sender);
            case LobbyAction.Cancel ignored -> service.cancel(sender);
            case LobbyAction.Ready ready -> service.ready(sender, ready.ready(), ready.configurationRevision());
            case LobbyAction.Leave ignored -> service.leave(sender);
            case LobbyAction.Start ignored -> service.start(sender);
        }
    }
}
