package dev.strategos.mod.server.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.strategos.core.shared.MatchId;
import dev.strategos.mod.server.match.MatchRecord;
import dev.strategos.mod.server.match.MatchRefusedException;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

/**
 * The server's saved matches, {@code strategos_registry} in the overworld's data storage: {@code {v: 1, epoch, nextId,
 * records, arenaRulesApplied, nextArenaSlot}}, the last two optional (M1, absent before). It hands out match ids and
 * arena slots (neither ever reused), counts save epochs and records that an arena world got its rules. Loading never
 * throws: a registry that does not decode is quarantined, keeps its raw tag, is never rewritten and refuses new saved
 * matches.
 */
public final class RegistryData extends SavedData {
    public static final String NAME = "strategos_registry";

    static final int VERSION = 1;

    private static final Codec<List<MatchRecord>> RECORDS = MatchRecord.CODEC.listOf();

    private final TreeMap<Integer, MatchRecord> records = new TreeMap<>();
    private long epoch;
    private int nextId = 1;
    private boolean arenaRulesApplied;
    private int nextArenaSlot;
    private @Nullable CompoundTag raw;
    private @Nullable String quarantineReason;

    public RegistryData() {}

    public static SavedData.Factory<RegistryData> factory() {
        return new SavedData.Factory<>(RegistryData::new, RegistryData::load);
    }

    /** The registry, or a quarantined one with the reason and the raw tag; never throws. */
    public static RegistryData load(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            if (!tag.contains("v", Tag.TAG_ANY_NUMERIC) || tag.getInt("v") != VERSION) {
                return quarantined(tag, "registry version " + tag.get("v") + ", this build reads " + VERSION);
            }
            if (!tag.contains("epoch", Tag.TAG_ANY_NUMERIC) || !tag.contains("nextId", Tag.TAG_ANY_NUMERIC)) {
                return quarantined(tag, "the registry has no epoch or next id");
            }
            Tag listed = tag.get("records");
            if (listed == null) {
                return quarantined(tag, "the registry has no records");
            }
            DataResult<List<MatchRecord>> decoded = NbtBridge.decode(RECORDS, listed);
            if (decoded.error().isPresent()) {
                return quarantined(
                        tag,
                        "the records do not decode: " + decoded.error().get().message());
            }
            RegistryData data = new RegistryData();
            data.epoch = tag.getLong("epoch");
            data.nextId = tag.getInt("nextId");
            data.arenaRulesApplied = tag.getBoolean("arenaRulesApplied");
            data.nextArenaSlot = tag.getInt("nextArenaSlot");
            if (data.nextArenaSlot < 0) {
                return quarantined(tag, "the next arena slot " + data.nextArenaSlot + " is negative");
            }
            for (MatchRecord record : decoded.getOrThrow()) {
                if (record.id().value() >= data.nextId) {
                    return quarantined(tag, "record " + record.id() + " is not below the next id " + data.nextId);
                }
                if (data.records.put(record.id().value(), record) != null) {
                    return quarantined(tag, "record " + record.id() + " is listed twice");
                }
            }
            return data;
        } catch (RuntimeException e) {
            return quarantined(tag, "cannot read the registry: " + e);
        }
    }

    /** A registry whose file vanilla could not read: never put in the storage, so the file is never overwritten. */
    public static RegistryData unreadable(String reason) {
        return quarantined(new CompoundTag(), reason);
    }

    private static RegistryData quarantined(CompoundTag tag, String reason) {
        RegistryData data = new RegistryData();
        data.raw = tag.copy();
        data.quarantineReason = reason;
        return data;
    }

    public @Nullable String quarantineReason() {
        return quarantineReason;
    }

    public MatchId allocateId() {
        requireUsable();
        MatchId id = new MatchId(nextId++);
        setDirty();
        return id;
    }

    /** The next arena slot, {@link #allocateArenaSlot()} not called. */
    public int nextArenaSlot() {
        return nextArenaSlot;
    }

    /** The next arena slot, counted as used even when the match then fails. */
    public int allocateArenaSlot() {
        requireUsable();
        int slot = nextArenaSlot++;
        setDirty();
        return slot;
    }

    public boolean arenaRulesApplied() {
        return arenaRulesApplied;
    }

    public void markArenaRulesApplied() {
        requireUsable();
        arenaRulesApplied = true;
        setDirty();
    }

    public long epoch() {
        return epoch;
    }

    /** The next save epoch: counted at every server stop. */
    public long nextEpoch() {
        requireUsable();
        epoch++;
        setDirty();
        return epoch;
    }

    /** In id order. */
    public Collection<MatchRecord> records() {
        return List.copyOf(records.values());
    }

    public @Nullable MatchRecord record(MatchId id) {
        return records.get(id.value());
    }

    public void put(MatchRecord record) {
        requireUsable();
        records.put(record.id().value(), record);
        setDirty();
    }

    public void remove(MatchId id) {
        requireUsable();
        if (records.remove(id.value()) != null) {
            setDirty();
        }
    }

    /** A quarantined registry is never written: its file stays as it was. */
    @Override
    public boolean isDirty() {
        return raw == null && super.isDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag kept = raw;
        if (kept != null) {
            return kept.copy();
        }
        tag.putInt("v", VERSION);
        tag.putLong("epoch", epoch);
        tag.putInt("nextId", nextId);
        tag.putBoolean("arenaRulesApplied", arenaRulesApplied);
        tag.putInt("nextArenaSlot", nextArenaSlot);
        tag.put("records", NbtBridge.encode(RECORDS, List.copyOf(records.values())));
        return tag;
    }

    private void requireUsable() {
        if (quarantineReason != null) {
            throw new MatchRefusedException(
                    "the match registry blob is quarantined, so saved matches are off: " + quarantineReason);
        }
    }
}
