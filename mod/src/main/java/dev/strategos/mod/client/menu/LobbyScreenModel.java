package dev.strategos.mod.client.menu;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

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

    static LobbyConfiguration defaultConfiguration() {
        return new LobbyConfiguration(
                2,
                List.of(
                        new LobbyConfiguration.Seat(0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0),
                        new LobbyConfiguration.Seat(
                                1, LobbyConfiguration.Kind.HUMAN, 1, 1, "strategos:placeholder", 1)),
                SkirmishSetup.DEFAULTS);
    }

    static LobbyConfiguration teamSkirmishConfiguration() {
        return new LobbyConfiguration(
                4,
                List.of(
                        new LobbyConfiguration.Seat(0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0),
                        new LobbyConfiguration.Seat(1, LobbyConfiguration.Kind.HUMAN, 2, 0, "strategos:placeholder", 2),
                        new LobbyConfiguration.Seat(2, LobbyConfiguration.Kind.AI, 1, 1, "strategos:placeholder", 1),
                        new LobbyConfiguration.Seat(3, LobbyConfiguration.Kind.AI, 3, 1, "strategos:placeholder", 3)),
                new SkirmishSetup(
                        SkirmishSetup.TEAM_SKIRMISH,
                        SkirmishSetup.Level.NORMAL,
                        SkirmishSetup.StartResources.MAP,
                        SkirmishSetup.DEFAULT_POPULATION,
                        SkirmishSetup.Visibility.FOG));
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

    /** Human-only choices: one AI owns its entire faction and cannot share it. */
    public List<Integer> factionsFor(int player) {
        var config = state.configuration();
        var seat = config.seats().stream()
                .filter(s -> s.player() == player)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown lobby player"));
        if (seat.kind() != LobbyConfiguration.Kind.HUMAN) return List.of(seat.faction());
        return IntStream.range(0, config.factionCapacity())
                .filter(f -> config.seats().stream()
                        .noneMatch(s -> s.faction() == f && s.kind() == LobbyConfiguration.Kind.AI))
                .boxed()
                .toList();
    }

    /** Join or split a human faction atomically, keeping the player identity and all unrelated seats. */
    public LobbyAction.Edit factionIntent(int player, int faction) {
        if (!mayConfigure()) throw new IllegalStateException("lobby configuration is frozen or unauthorized");
        var config = state.configuration();
        var source = config.seats().stream()
                .filter(s -> s.player() == player)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown lobby player"));
        if (source.kind() != LobbyConfiguration.Kind.HUMAN
                || !factionsFor(player).contains(faction)) {
            throw new IllegalArgumentException("faction cannot accept this human controller");
        }
        if (source.faction() == faction) return new LobbyAction.Edit(config);
        var target = config.seats().stream()
                .filter(s -> s.kind() == LobbyConfiguration.Kind.HUMAN && s.faction() == faction)
                .findFirst();
        int colour = target.map(LobbyConfiguration.Seat::colour)
                .orElseGet(() -> IntStream.range(0, 16)
                        .filter(c -> config.seats().stream()
                                .noneMatch(s -> s.player() != player
                                        && s.kind() != LobbyConfiguration.Kind.CLOSED
                                        && s.colour() == c))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("no unused faction colour")));
        var replacement = new LobbyConfiguration.Seat(
                player,
                source.kind(),
                faction,
                target.map(LobbyConfiguration.Seat::team).orElse(faction),
                target.map(LobbyConfiguration.Seat::civilisation).orElse(source.civilisation()),
                colour);
        return new LobbyAction.Edit(new LobbyConfiguration(
                config.factionCapacity(),
                config.seats().stream()
                        .map(s -> s.player() == player ? replacement : s)
                        .toList(),
                config.setup()));
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
