package dev.strategos.mod.server.match;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Immutable pre-match configuration and ready acknowledgements. This value neither authenticates a network sender
 * nor creates a match. UUID membership stays outside core; player seats and economy factions are distinct domains.
 * Readiness is one prerequisite for preparation, not complete validation of the map, supported assets or setup.
 *
 * <p>AFTER-M6: LobbyService must validate its real setup and authenticated caller, then freeze this value and the
 * accepted setup atomically before invoking M6 preparation. T3b owns admission, payload privacy and partial-match
 * cleanup; rolling back this value alone is not world cleanup. No spectator or combat-mode selection is added here.
 */
public final class MatchLobby {
    public static final int MAX_SEATS = 4;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum Kind {
        HUMAN,
        AI,
        CLOSED
    }

    public enum Phase {
        EDITING,
        PREPARING
    }

    /** Configuration only: readiness cannot be smuggled into a configuration replacement. */
    public record Seat(
            PlayerSlot player,
            Kind kind,
            @Nullable UUID occupant,
            FactionSlot faction,
            int team,
            String civilisation,
            int colour) {
        public Seat {
            if (player.isAdmin() || player.value() >= MAX_SEATS) {
                throw new IllegalArgumentException("lobby player outside 0..3");
            }
            if (kind != Kind.HUMAN && occupant != null) {
                throw new IllegalArgumentException("only human seats have UUID occupants");
            }
            if (team < 0 || team > FactionSlot.MAX || colour < 0 || colour > FactionSlot.MAX) {
                throw new IllegalArgumentException("lobby team or colour outside 0..15");
            }
            if (civilisation.length() > 128
                    || !RESOURCE_ID.matcher(civilisation).matches()) {
                throw new IllegalArgumentException("civilisation must be a bounded resource id");
            }
        }
    }

    private final int factionCapacity;
    private final List<Seat> seats;
    private final Set<PlayerSlot> ready;
    private final Phase phase;
    private final long revision;
    private final long configurationRevision;

    private MatchLobby(
            int factionCapacity,
            List<Seat> seats,
            Set<PlayerSlot> ready,
            Phase phase,
            long revision,
            long configurationRevision) {
        this.factionCapacity = factionCapacity;
        this.seats = seats;
        this.ready = Set.copyOf(ready);
        this.phase = phase;
        this.revision = revision;
        this.configurationRevision = configurationRevision;
    }

    public static MatchLobby create(int factionCapacity, List<Seat> seats) {
        return new MatchLobby(factionCapacity, validate(factionCapacity, seats), Set.of(), Phase.EDITING, 0, 0);
    }

    private static List<Seat> validate(int capacity, List<Seat> input) {
        if (capacity < 1 || capacity > FactionSlot.MAX + 1) {
            throw new IllegalArgumentException("lobby faction capacity outside 1..16");
        }
        if (input.isEmpty() || input.size() > MAX_SEATS) {
            throw new IllegalArgumentException("lobby needs 1..4 configured seats");
        }
        List<Seat> ordered = input.stream()
                .sorted(Comparator.comparingInt(s -> s.player().value()))
                .toList();
        Set<PlayerSlot> players = new HashSet<>();
        Set<UUID> members = new HashSet<>();
        var factions = new HashMap<FactionSlot, Seat>();
        var colours = new HashMap<Integer, FactionSlot>();
        for (Seat seat : ordered) {
            if (!players.add(seat.player())) throw new IllegalArgumentException("duplicate lobby player slot");
            UUID member = seat.occupant();
            if (member != null && !members.add(member)) throw new IllegalArgumentException("duplicate lobby member");
            if (seat.faction().value() >= capacity)
                throw new IllegalArgumentException("faction outside lobby capacity");
            if (seat.kind() == Kind.CLOSED) continue;
            Seat first = factions.putIfAbsent(seat.faction(), seat);
            if (first != null
                    && (first.team() != seat.team()
                            || first.colour() != seat.colour()
                            || !first.civilisation().equals(seat.civilisation()))) {
                throw new IllegalArgumentException("one faction needs consistent team, civilisation and colour");
            }
            FactionSlot owner = colours.putIfAbsent(seat.colour(), seat.faction());
            if (owner != null && !owner.equals(seat.faction())) {
                throw new IllegalArgumentException("different factions need distinct colours");
            }
        }
        return ordered;
    }

    public int factionCapacity() {
        return factionCapacity;
    }

    public List<Seat> seats() {
        return seats;
    }

    public Set<PlayerSlot> readyPlayers() {
        return ready;
    }

    public Phase phase() {
        return phase;
    }
    /** Changes for every real state update, including readiness. */
    public long revision() {
        return revision;
    }
    /** Only settings changes/rollback invalidate ready acknowledgements; other peers' ready toggles do not. */
    public long configurationRevision() {
        return configurationRevision;
    }

    /** Empty human seats block readiness; AI/closed seats need no acknowledgement. No-human is never ready. */
    public boolean allHumansReady() {
        boolean human = false;
        for (Seat seat : seats) {
            if (seat.kind() != Kind.HUMAN) continue;
            human = true;
            if (seat.occupant() == null || !ready.contains(seat.player())) return false;
        }
        return human;
    }

    /** The service supplies its authenticated sender's UUID, never a client-supplied issuer. */
    public MatchLobby withReady(UUID member, boolean value, long expectedConfigurationRevision) {
        requireEditing();
        if (expectedConfigurationRevision != configurationRevision) {
            throw new IllegalStateException("lobby settings changed; ready acknowledgement is stale");
        }
        PlayerSlot player = seats.stream()
                .filter(s -> s.kind() == Kind.HUMAN && member.equals(s.occupant()))
                .map(Seat::player)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a lobby member"));
        if (ready.contains(player) == value) return this;
        Set<PlayerSlot> next = new HashSet<>(ready);
        if (value) next.add(player);
        else next.remove(player);
        return new MatchLobby(factionCapacity, seats, next, phase, nextRevision(), configurationRevision);
    }

    /** Trusted configuration replacement; authentication is the service's responsibility. */
    public MatchLobby withSeats(List<Seat> replacement) {
        requireEditing();
        List<Seat> checked = validate(factionCapacity, replacement);
        if (seats.equals(checked)) return this;
        return new MatchLobby(factionCapacity, checked, Set.of(), phase, nextRevision(), nextConfigurationRevision());
    }

    public MatchLobby withSeat(Seat replacement) {
        requireEditing();
        List<Seat> next = new ArrayList<>(seats);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).player().equals(replacement.player())) {
                next.set(i, replacement);
                return withSeats(next);
            }
        }
        throw new IllegalArgumentException("player slot is not configured");
    }

    public MatchLobby withFactionCapacity(int capacity) {
        requireEditing();
        if (capacity == factionCapacity) return this;
        return new MatchLobby(
                capacity, validate(capacity, seats), Set.of(), phase, nextRevision(), nextConfigurationRevision());
    }

    /** Signal a real map/rules/setup change held by the service; it invalidates every old acknowledgement. */
    public MatchLobby settingsChanged() {
        requireEditing();
        return new MatchLobby(factionCapacity, seats, Set.of(), phase, nextRevision(), nextConfigurationRevision());
    }

    /** Freeze the aggregate; the service separately checks full start validity and performs preparation. */
    public MatchLobby prepare() {
        requireEditing();
        if (!allHumansReady()) throw new IllegalStateException("human seats are not ready");
        return new MatchLobby(factionCapacity, seats, ready, Phase.PREPARING, nextRevision(), configurationRevision);
    }

    /** Restore editing with fresh acknowledgements required; actual failed-match cleanup belongs to the service. */
    public MatchLobby preparationFailed() {
        if (phase != Phase.PREPARING) throw new IllegalStateException("lobby is not preparing");
        return new MatchLobby(
                factionCapacity, seats, Set.of(), Phase.EDITING, nextRevision(), nextConfigurationRevision());
    }

    private void requireEditing() {
        if (phase != Phase.EDITING) throw new IllegalStateException("lobby configuration is frozen");
    }

    private long nextRevision() {
        return Math.incrementExact(revision);
    }

    private long nextConfigurationRevision() {
        return Math.incrementExact(configurationRevision);
    }
}
