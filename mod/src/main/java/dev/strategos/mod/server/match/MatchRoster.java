package dev.strategos.mod.server.match;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Who sits in a match (NET-01, spec §2.7): players by UUID, each in a player slot, which controls the faction of the
 * same number; a slot holds one player. The administrator's slot seats observers, any number of them, who see every
 * faction and issue nothing (the gateway refuses their commands). A command's issuer is the slot bound to its sender's UUID, never a name or a
 * payload field. Saved with the match's blob; the registry keeps a player in one match at a time.
 */
public final class MatchRoster {
    private final TreeMap<UUID, PlayerSlot> seats = new TreeMap<>();

    public @Nullable PlayerSlot slotOf(UUID player) {
        return seats.get(player);
    }

    /** Seats {@code player} in {@code slot}: a player's (0 to 15) which no one else holds, or the observers'. */
    void seat(UUID player, PlayerSlot slot) {
        if (slot.isAdmin()) {
            seats.put(player, slot);
            return;
        }
        for (Map.Entry<UUID, PlayerSlot> e : seats.entrySet()) {
            if (e.getValue().equals(slot) && !e.getKey().equals(player)) {
                throw new MatchRefusedException("slot " + slot.value() + " is taken");
            }
        }
        seats.put(player, slot);
    }

    void unseat(UUID player) {
        seats.remove(player);
    }

    /** Every seated player, by UUID. */
    public List<UUID> members() {
        return List.copyOf(seats.keySet());
    }

    /** The players whose slot controls {@code faction}. */
    public List<UUID> of(FactionSlot faction) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, PlayerSlot> e : seats.entrySet()) {
            if (e.getValue().value() == faction.value()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return seats.isEmpty();
    }

    /** {@code [{uuid: [I; …], slot: n}, …]} by UUID. */
    public ListTag save() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerSlot> e : seats.entrySet()) {
            CompoundTag seat = new CompoundTag();
            seat.put("uuid", NbtUtils.createUUID(e.getKey()));
            seat.putInt("slot", e.getValue().value());
            list.add(seat);
        }
        return list;
    }

    /** A roster from {@link #save()}'s list; a seat that does not read throws. */
    public static MatchRoster load(ListTag list) {
        MatchRoster roster = new MatchRoster();
        for (int k = 0; k < list.size(); k++) {
            CompoundTag seat = list.getCompound(k);
            if (!seat.contains("uuid", Tag.TAG_INT_ARRAY) || !seat.contains("slot", Tag.TAG_ANY_NUMERIC)) {
                throw new IllegalArgumentException("roster seat " + k + " has no uuid or slot");
            }
            roster.seat(UUIDUtil.uuidFromIntArray(seat.getIntArray("uuid")), new PlayerSlot(seat.getInt("slot")));
        }
        return roster;
    }
}
