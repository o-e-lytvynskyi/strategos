package dev.strategos.mod.server.match;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.diplomacy.DiplomacyView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Who sits in a match (NET-01, spec §2.7): players by UUID, each in a player slot whose primary faction is resolved through
 * the current diplomacy binding; a slot holds one player. The administrator's slot seats observers, any number of them, who see every
 * faction and issue nothing (the gateway refuses their commands). A command's issuer is the slot bound to its sender's UUID, never a name or a
 * payload field. Saved with the match's blob; the registry keeps a player in one match at a time.
 */
public final class MatchRoster {
    public static final long DEFAULT_RECONNECT_NANOS = ReconnectSettings.DEFAULT_SECONDS * 1_000_000_000L;
    public static final long MAX_RECONNECT_NANOS = ReconnectSettings.MAX_SECONDS * 1_000_000_000L;

    private record SeatState(PlayerSlot slot, boolean connected, long reservedAt, long remaining, long generation) {}

    private final TreeMap<UUID, SeatState> seats = new TreeMap<>();
    private long nextGeneration;
    private final LongSupplier clock;
    private final long reclaimNanos;

    public MatchRoster() {
        this(System::nanoTime, DEFAULT_RECONNECT_NANOS);
    }

    /** The adapter clock is monotonic and continues while the simulation is paused. */
    public MatchRoster(LongSupplier clock, long reclaimNanos) {
        this.clock = Objects.requireNonNull(clock);
        if (reclaimNanos <= 0 || reclaimNanos > MAX_RECONNECT_NANOS) {
            throw new IllegalArgumentException("reclaim duration outside the supported range");
        }
        this.reclaimNanos = reclaimNanos;
    }

    /** The configured window for a future disconnect; explicit saved remaining durations are independent. */
    public long reclaimWindowNanos() {
        return reclaimNanos;
    }

    public @Nullable PlayerSlot slotOf(UUID player) {
        SeatState seat = seats.get(player);
        return seat == null ? null : seat.slot();
    }

    /** A live per-player transport generation; absent/disconnected seats have none. Never persisted. */
    public long generationOf(UUID player) {
        SeatState seat = seats.get(player);
        return seat == null || !seat.connected() ? 0 : seat.generation();
    }

    private long newGeneration() {
        nextGeneration = Math.incrementExact(nextGeneration);
        return nextGeneration;
    }

    /** Seats {@code player} in {@code slot}: a player's (0 to 15) which no one else holds, or the observers'. */
    void seat(UUID player, PlayerSlot slot) {
        if (slot.isAdmin()) {
            seats.put(player, new SeatState(slot, true, 0, reclaimNanos, newGeneration()));
            return;
        }
        for (Map.Entry<UUID, SeatState> e : seats.entrySet()) {
            if (e.getValue().slot().equals(slot) && !e.getKey().equals(player)) {
                throw new MatchRefusedException("slot " + slot.value() + " is taken");
            }
        }
        seats.put(player, new SeatState(slot, true, 0, reclaimNanos, newGeneration()));
    }

    void unseat(UUID player) {
        seats.remove(player);
    }

    public boolean isConnected(UUID player) {
        SeatState seat = seats.get(player);
        return seat != null && seat.connected();
    }

    /** Authenticated lifecycle only: an expired reservation is never revived, and a current login is idempotent. */
    boolean connect(UUID player) {
        SeatState seat = seats.get(player);
        if (seat == null) return false;
        if (seat.connected()) return true;
        if (remainingNanos(player) == 0) return false;
        seats.put(player, new SeatState(seat.slot(), true, 0, reclaimNanos, newGeneration()));
        return true;
    }

    /** A full view is a new transport epoch, without reseating or extending a reservation. */
    void renew(UUID player) {
        SeatState seat = seats.get(player);
        if (seat != null && seat.connected())
            seats.put(player, new SeatState(seat.slot(), true, seat.reservedAt(), seat.remaining(), newGeneration()));
    }

    /** Repeated disconnect notifications cannot restart a reserved seat's timer. */
    public void disconnect(UUID player) {
        SeatState seat = seats.get(player);
        if (seat != null && seat.connected()) {
            seats.put(player, new SeatState(seat.slot(), false, clock.getAsLong(), reclaimNanos, seat.generation()));
        }
    }

    /** Zero for absent/expired seats; connected seats retain the full window for a later disconnect. */
    public long remainingNanos(UUID player) {
        SeatState seat = seats.get(player);
        if (seat == null) return 0;
        if (seat.connected()) return reclaimNanos;
        long elapsed = Math.max(0, clock.getAsLong() - seat.reservedAt());
        return Math.max(0, seat.remaining() - elapsed);
    }

    /** Every seated player, by UUID. */
    public List<UUID> members() {
        return List.copyOf(seats.keySet());
    }

    /** Legacy identity-only membership, for old roster fixtures. Live routing requires the authority overload. */
    public List<UUID> of(FactionSlot faction) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, SeatState> e : seats.entrySet()) {
            if (e.getValue().slot().value() == faction.value()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** Private faction audiences include primary participants only, never recipients of control grants. */
    public List<UUID> of(FactionSlot faction, DiplomacyView authority) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, SeatState> e : seats.entrySet()) {
            if (authority.factionOf(e.getValue().slot()).filter(faction::equals).isPresent()) out.add(e.getKey());
        }
        return out;
    }

    public boolean isEmpty() {
        return seats.isEmpty();
    }

    /** Legacy membership rows, used by old-save fixtures; current blobs use {@link #saveSessions()}. */
    public ListTag save() {
        return save(false);
    }

    /** Current membership plus remaining duration; never serializes a live connection or clock origin. */
    public ListTag saveSessions() {
        return save(true);
    }

    private ListTag save(boolean sessions) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, SeatState> e : seats.entrySet()) {
            CompoundTag seat = new CompoundTag();
            seat.put("uuid", NbtUtils.createUUID(e.getKey()));
            seat.putInt("slot", e.getValue().slot().value());
            if (sessions) seat.putLong("remainingNanos", remainingNanos(e.getKey()));
            list.add(seat);
        }
        return list;
    }

    /** Valid legacy membership restores disconnected with the default reclaim window. */
    public static MatchRoster load(ListTag list) {
        return load(list, System::nanoTime, DEFAULT_RECONNECT_NANOS);
    }

    public static MatchRoster load(ListTag list, LongSupplier clock, long reclaimNanos) {
        return load(list, clock, reclaimNanos, false);
    }

    public static MatchRoster loadSessions(ListTag list) {
        return loadSessions(list, System::nanoTime, DEFAULT_RECONNECT_NANOS);
    }

    public static MatchRoster loadSessions(ListTag list, LongSupplier clock, long reclaimNanos) {
        return load(list, clock, reclaimNanos, true);
    }

    private static MatchRoster load(ListTag list, LongSupplier clock, long reclaimNanos, boolean sessions) {
        MatchRoster roster = new MatchRoster(clock, reclaimNanos);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("roster is not a list of seats");
        }
        long restoredAt = clock.getAsLong();
        for (int k = 0; k < list.size(); k++) {
            CompoundTag seat = list.getCompound(k);
            if (!seat.contains("uuid", Tag.TAG_INT_ARRAY) || !seat.contains("slot", Tag.TAG_ANY_NUMERIC)) {
                throw new IllegalArgumentException("roster seat " + k + " has no uuid or slot");
            }
            UUID member = UUIDUtil.uuidFromIntArray(seat.getIntArray("uuid"));
            if (roster.seats.containsKey(member)) {
                throw new IllegalArgumentException("duplicate roster UUID");
            }
            long remaining = reclaimNanos;
            if (sessions) {
                if (!seat.contains("remainingNanos", Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("roster seat has no remaining duration");
                }
                remaining = seat.getLong("remainingNanos");
                if (remaining < 0 || remaining > MAX_RECONNECT_NANOS) {
                    throw new IllegalArgumentException("remaining reclaim duration outside the supported range");
                }
            }
            PlayerSlot slot = new PlayerSlot(seat.getInt("slot"));
            roster.seat(member, slot);
            roster.seats.put(member, new SeatState(slot, false, restoredAt, remaining, roster.nextGeneration));
        }
        return roster;
    }
}
