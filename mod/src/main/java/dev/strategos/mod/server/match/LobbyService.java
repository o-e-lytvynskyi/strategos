package dev.strategos.mod.server.match;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-thread lobby authority and preparation transaction. Callers supply authenticated connection UUIDs;
 * configuration replacements never assign occupants. The backend owns map validation, world allocation/cleanup,
 * admission and private publication through the network gateway.
 */
public final class LobbyService {
    public enum State {
        EDITING,
        PREPARING,
        STARTED,
        FAILED,
        CLOSED
    }

    /** Server-side snapshot; the network adapter must project it without exposing membership UUIDs. */
    public record Snapshot(MatchLobby lobby, SkirmishSetup setup, State state, String message) {}

    public interface Preparation {
        enum Status {
            PREPARING,
            COMPLETE,
            FAILED
        }
        /** COMPLETE means every member has been admitted and given its initial state. */
        Status poll();
        /** Release all partial runtime state before returning; a failure must throw. */
        void cancel();
    }

    /** Allocation failed and rollback could not establish a clean state, even if registry removal already ran. */
    public static final class IncompleteCleanupException extends RuntimeException {
        public IncompleteCleanupException(RuntimeException cause) {
            super("lobby allocation cleanup is incomplete", cause);
        }
    }

    public interface Backend {
        boolean mayHost(UUID member);

        boolean online(UUID member);

        boolean seated(UUID member);

        boolean activeMatch();
        /** Validate the supported map/setup against the frozen candidate before allocating anything. */
        void validate(MatchLobby lobby, SkirmishSetup setup);
        /** If allocation throws, the backend must clean up anything allocated before propagating it. */
        Preparation begin(MatchLobby lobby, SkirmishSetup setup);
        /** The service calls this only for a currently admitted, online member. */
        void send(UUID member, Snapshot snapshot);
    }

    private static final Logger LOG = LoggerFactory.getLogger(LobbyService.class);
    private final Backend backend;
    private @Nullable UUID host;
    private @Nullable Snapshot current;
    private @Nullable Preparation preparation;

    public LobbyService(Backend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    /** Server-owned role for the private recipient projection; it grants no connection membership. */
    public boolean isHost(UUID member) {
        return member.equals(host);
    }

    public Snapshot snapshot() {
        Snapshot value = current;
        if (value == null) throw new IllegalStateException("no lobby is open");
        return value;
    }

    public void open(UUID member, int capacity, List<MatchLobby.Seat> seats, SkirmishSetup setup) {
        if (!backend.mayHost(member) || !backend.online(member) || backend.seated(member)) {
            throw new IllegalArgumentException("only an available host or operator may open a lobby");
        }
        Snapshot before = current;
        if (backend.activeMatch()
                || (before != null && before.state() != State.CLOSED && before.state() != State.STARTED)) {
            throw new IllegalStateException("a lobby or match is already active");
        }
        MatchLobby lobby = MatchLobby.create(capacity, seats);
        boolean hasHost = false;
        for (MatchLobby.Seat seat : lobby.seats()) {
            UUID occupant = seat.occupant();
            if (occupant != null) {
                if (!occupant.equals(member)) throw new IllegalArgumentException("members must join their own seats");
                hasHost = true;
            }
        }
        if (!hasHost) throw new IllegalArgumentException("the host needs a human seat");
        host = member;
        preparation = null;
        current = new Snapshot(lobby, setup, State.EDITING, "");
        publish();
    }

    public void join(UUID member, PlayerSlot player) {
        Snapshot before = editing();
        if (!backend.online(member) || backend.seated(member)) {
            throw new IllegalArgumentException("member is not available");
        }
        MatchLobby.Seat target = null;
        for (MatchLobby.Seat seat : before.lobby().seats()) {
            if (member.equals(seat.occupant())) {
                if (seat.player().equals(player)) return;
                throw new IllegalArgumentException("member already occupies a seat");
            }
            if (seat.player().equals(player)) target = seat;
        }
        if (target == null || target.kind() != MatchLobby.Kind.HUMAN || target.occupant() != null) {
            throw new IllegalArgumentException("human seat is unavailable");
        }
        MatchLobby lobby = before.lobby()
                .withSeat(new MatchLobby.Seat(
                        target.player(),
                        target.kind(),
                        member,
                        target.faction(),
                        target.team(),
                        target.civilisation(),
                        target.colour()));
        current = new Snapshot(lobby, before.setup(), State.EDITING, "");
        publish();
    }

    public void ready(UUID member, boolean ready, long configurationRevision) {
        Snapshot before = editing();
        requireMember(before.lobby(), member);
        if (!backend.online(member) || backend.seated(member))
            throw new IllegalArgumentException("member is not available");
        MatchLobby lobby = before.lobby().withReady(member, ready, configurationRevision);
        if (lobby == before.lobby()) return;
        current = new Snapshot(lobby, before.setup(), State.EDITING, "");
        publish();
    }

    public void edit(UUID member, List<MatchLobby.Seat> seats, SkirmishSetup setup) {
        requireHost(member);
        Snapshot before = editing();
        MatchLobby candidate = before.lobby().withSeats(seats);
        for (MatchLobby.Seat seat : before.lobby().seats()) {
            if (seat.occupant() == null) continue;
            boolean retained = candidate.seats().stream()
                    .anyMatch(next ->
                            next.player().equals(seat.player()) && Objects.equals(next.occupant(), seat.occupant()));
            if (!retained) throw new IllegalArgumentException("configuration cannot reassign members");
        }
        for (MatchLobby.Seat seat : candidate.seats()) {
            if (seat.occupant() == null) continue;
            boolean admitted = before.lobby().seats().stream()
                    .anyMatch(old ->
                            old.player().equals(seat.player()) && Objects.equals(old.occupant(), seat.occupant()));
            if (!admitted) throw new IllegalArgumentException("members must join their own seats");
        }
        if (!setup.equals(before.setup())) candidate = candidate.settingsChanged();
        if (candidate == before.lobby() && setup.equals(before.setup())) return;
        current = new Snapshot(candidate, setup, State.EDITING, "");
        publish();
    }

    public void start(UUID member) {
        requireHost(member);
        Snapshot before = editing();
        if (backend.activeMatch()) throw new IllegalStateException("a match is already active");
        MatchLobby frozen = before.lobby().prepare();
        for (MatchLobby.Seat seat : frozen.seats()) {
            UUID occupant = seat.occupant();
            if (occupant != null && (!backend.online(occupant) || backend.seated(occupant))) {
                throw new IllegalArgumentException("a member is not available");
            }
        }
        backend.validate(frozen, before.setup());
        current = new Snapshot(frozen, before.setup(), State.PREPARING, "");
        try {
            preparation = Objects.requireNonNull(backend.begin(frozen, before.setup()));
        } catch (RuntimeException failure) {
            if (failure instanceof IncompleteCleanupException || backend.activeMatch()) {
                LOG.error("Lobby allocation failed with runtime state still retained", failure);
                current = new Snapshot(frozen, before.setup(), State.FAILED, "cleanup_failed");
                publish();
                return;
            }
            fail("preparation_failed", failure);
            return;
        }
        publish();
    }

    public void leave(UUID member) {
        Snapshot before = snapshot();
        requireMember(before.lobby(), member);
        if (before.state() == State.PREPARING) {
            fail("member_left", null);
            if (snapshot().state() == State.FAILED) return;
            before = snapshot();
        }
        if (before.state() != State.EDITING) throw new IllegalStateException("lobby is not editable");
        if (member.equals(host)) {
            current = new Snapshot(before.lobby(), before.setup(), State.CLOSED, "host_left");
            publish();
            return;
        }
        for (MatchLobby.Seat seat : before.lobby().seats()) {
            if (member.equals(seat.occupant())) {
                current = new Snapshot(
                        before.lobby()
                                .withSeat(new MatchLobby.Seat(
                                        seat.player(),
                                        seat.kind(),
                                        null,
                                        seat.faction(),
                                        seat.team(),
                                        seat.civilisation(),
                                        seat.colour())),
                        before.setup(),
                        State.EDITING,
                        "");
                publish();
                return;
            }
        }
    }

    public void tick() {
        Snapshot before = current;
        if (before == null || (before.state() != State.PREPARING && before.state() != State.EDITING)) return;
        for (MatchLobby.Seat seat : before.lobby().seats()) {
            UUID occupant = seat.occupant();
            if (occupant != null && !backend.online(occupant)) {
                if (before.state() == State.PREPARING) fail("member_disconnected", null);
                else leave(occupant);
                return;
            }
        }
        Preparation pending = preparation;
        if (before.state() != State.PREPARING || pending == null) return;
        Preparation.Status status;
        try {
            status = pending.poll();
        } catch (RuntimeException failure) {
            fail("preparation_failed", failure);
            return;
        }
        switch (status) {
            case PREPARING -> {}
            case FAILED -> fail("preparation_failed", null);
            case COMPLETE -> {
                preparation = null;
                current = new Snapshot(before.lobby(), before.setup(), State.STARTED, "");
                publish();
            }
        }
    }

    /** Cancel transient launch work before the owner attempts match persistence on server stop. */
    public void shutdown() {
        Snapshot before = current;
        if (before == null || before.state() == State.CLOSED) return;
        Preparation pending = preparation;
        if (before.state() == State.FAILED && pending == null) {
            throw new IncompleteCleanupException(new IllegalStateException("lobby cleanup remains unresolved"));
        }
        if (pending != null) {
            try {
                pending.cancel();
            } catch (RuntimeException failure) {
                current = new Snapshot(before.lobby(), before.setup(), State.FAILED, "cleanup_failed");
                publish();
                throw new IncompleteCleanupException(failure);
            }
            preparation = null;
        }
        current = new Snapshot(before.lobby(), before.setup(), State.CLOSED, "server_stopping");
        publish();
    }

    private void fail(String message, @Nullable RuntimeException failure) {
        Snapshot before = snapshot();
        if (failure != null) LOG.warn("Lobby preparation failed", failure);
        Preparation pending = preparation;
        if (pending != null) {
            try {
                pending.cancel();
            } catch (RuntimeException cleanupFailure) {
                LOG.error("Lobby preparation cleanup failed", cleanupFailure);
                current = new Snapshot(before.lobby(), before.setup(), State.FAILED, "cleanup_failed");
                publish();
                return;
            }
        }
        preparation = null;
        current = new Snapshot(before.lobby().preparationFailed(), before.setup(), State.EDITING, message);
        publish();
    }

    private Snapshot editing() {
        Snapshot before = snapshot();
        if (before.state() != State.EDITING) throw new IllegalStateException("lobby is not editable");
        return before;
    }

    private void requireHost(UUID member) {
        if (!backend.online(member) || (!member.equals(host) && !backend.mayHost(member))) {
            throw new IllegalArgumentException("only the host or an operator may change the lobby");
        }
    }

    private static void requireMember(MatchLobby lobby, UUID member) {
        if (lobby.seats().stream().noneMatch(seat -> member.equals(seat.occupant()))) {
            throw new IllegalArgumentException("not a lobby member");
        }
    }

    private void publish() {
        Snapshot value = snapshot();
        for (MatchLobby.Seat seat : value.lobby().seats()) {
            UUID member = seat.occupant();
            if (member != null && backend.online(member)) backend.send(member, value);
        }
    }
}
