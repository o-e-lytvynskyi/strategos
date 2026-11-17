package dev.strategos.mod.client.menu;

import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Detached presentation of an authenticated recipient view; button affordances never replace server validation. */
public record LobbyScreenModel(LobbyState state) {
    public record Row(
            LobbyConfiguration.Seat configuration,
            String name,
            boolean occupied,
            boolean ready,
            boolean host,
            boolean self) {}

    public LobbyScreenModel {
        Objects.requireNonNull(state);
    }

    public List<Row> rows() {
        return state.configuration().seats().stream()
                .sorted(Comparator.comparingInt(LobbyConfiguration.Seat::player))
                .map(seat -> {
                    var member = state.members().stream()
                            .filter(m -> m.player() == seat.player())
                            .findFirst()
                            .orElse(null);
                    return new Row(
                            seat,
                            member == null ? "" : member.name(),
                            member != null,
                            member != null && member.ready(),
                            member != null && member.host(),
                            seat.player() == state.self());
                })
                .toList();
    }

    public boolean mayConfigure() {
        return state.phase() == LobbyState.Phase.EDITING && state.mayConfigure();
    }

    public boolean mayReady() {
        return state.phase() == LobbyState.Phase.EDITING;
    }

    public boolean ownReady() {
        return state.members().stream()
                .filter(m -> m.player() == state.self())
                .findFirst()
                .orElseThrow()
                .ready();
    }

    public boolean mayStart() {
        if (!mayConfigure()) return false;
        List<Row> humans = rows().stream()
                .filter(r -> r.configuration().kind() == LobbyConfiguration.Kind.HUMAN)
                .toList();
        return !humans.isEmpty() && humans.stream().allMatch(r -> r.occupied() && r.ready());
    }

    public boolean mayLeave() {
        return state.phase() == LobbyState.Phase.EDITING || state.phase() == LobbyState.Phase.PREPARING;
    }

    public LobbyAction.Ready readyIntent() {
        if (!mayReady()) throw new IllegalStateException("lobby readiness is frozen");
        return new LobbyAction.Ready(!ownReady(), state.configurationRevision());
    }

    public LobbyAction.Start startIntent() {
        if (!mayStart()) throw new IllegalStateException("lobby is not ready to start");
        return new LobbyAction.Start();
    }

    public String statusKey() {
        return switch (state.reason()) {
            case "preparation_failed",
                    "cleanup_failed",
                    "member_left",
                    "member_disconnected",
                    "host_left",
                    "server_stopping" -> "strategos.lobby.reason." + state.reason();
            default ->
                switch (state.phase()) {
                    case EDITING -> "strategos.lobby.status.editing";
                    case PREPARING -> "strategos.lobby.status.preparing";
                    case STARTED -> "strategos.lobby.status.started";
                    case FAILED -> "strategos.lobby.status.failed";
                    case CLOSED -> "strategos.lobby.status.closed";
                };
        };
    }
}
