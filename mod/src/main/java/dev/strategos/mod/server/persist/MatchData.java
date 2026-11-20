package dev.strategos.mod.server.persist;

import com.mojang.serialization.DataResult;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.mod.server.match.MatchRoster;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

/**
 * One match's blob, {@code strategos_match_<id>} in its level's data storage: {@code {v: 2, epoch, snapshot, roster}}, the
 * snapshot in the core's schema with its own {@code "v"}, and disconnected-session durations in the roster.
 * Version 1 migrates valid legacy membership with a full reclaim window; current versions require explicit metadata.
 * Loading never throws (guard 1 of ADR-0018): a blob that does not decode is QUARANTINED, keeps its raw tag and is
 * never dirty, so the file stays as it was.
 */
public final class MatchData extends SavedData {
    public enum State {
        /** A running match: saving encodes its current snapshot. */
        LIVE,
        /** Read from disk, not yet attached to a match. */
        LOADED,
        /** Did not decode: the raw tag is kept, and the file is never rewritten. */
        QUARANTINED,
        /** Its match was disposed and its file deleted: never saved again. */
        DISPOSED
    }

    static final int VERSION = 2;

    private final MatchId id;
    private State state;
    private @Nullable Supplier<MatchSnapshot> snapshot;
    private @Nullable LongSupplier epoch;
    private @Nullable MatchSnapshot loaded;
    private long loadedEpoch;
    private @Nullable CompoundTag raw;
    private @Nullable String quarantineReason;
    private MatchRoster roster;
    private boolean launchPending;

    private MatchData(MatchId id, State state) {
        this(id, state, new MatchRoster());
    }

    private MatchData(MatchId id, State state, MatchRoster roster) {
        this.id = id;
        this.state = state;
        this.roster = roster;
    }

    public static String name(MatchId id) {
        return "strategos_match_" + id.value();
    }

    /** How vanilla reads the blob; nothing ever asks it for an empty one. */
    public static SavedData.Factory<MatchData> factory(MatchId id) {
        return factory(id, MatchRoster.DEFAULT_RECONNECT_NANOS);
    }

    public static SavedData.Factory<MatchData> factory(MatchId id, long reclaimNanos) {
        return new SavedData.Factory<>(
                () -> new MatchData(id, State.DISPOSED, new MatchRoster(System::nanoTime, reclaimNanos)),
                (tag, registries) -> load(id, tag, reclaimNanos));
    }

    /** A running match's blob; {@code epoch} is the registry's current save epoch. */
    public static MatchData live(MatchId id, Supplier<MatchSnapshot> snapshot, LongSupplier epoch) {
        return live(id, snapshot, epoch, MatchRoster.DEFAULT_RECONNECT_NANOS);
    }

    public static MatchData live(MatchId id, Supplier<MatchSnapshot> snapshot, LongSupplier epoch, long reclaimNanos) {
        MatchData data = new MatchData(id, State.LIVE, new MatchRoster(System::nanoTime, reclaimNanos));
        data.snapshot = snapshot;
        data.epoch = epoch;
        return data;
    }

    /** LOADED with the decoded snapshot, or QUARANTINED with the reason and the raw tag; never throws. */
    public static MatchData load(MatchId id, CompoundTag tag) {
        return load(id, tag, MatchRoster.DEFAULT_RECONNECT_NANOS);
    }

    public static MatchData load(MatchId id, CompoundTag tag, long reclaimNanos) {
        return load(id, tag, System::nanoTime, reclaimNanos);
    }

    static MatchData load(MatchId id, CompoundTag tag, LongSupplier clock, long reclaimNanos) {
        try {
            if (!tag.contains("v", Tag.TAG_ANY_NUMERIC)) {
                return quarantined(id, tag, "the blob has no version \"v\"");
            }
            int version = tag.getInt("v");
            if (version != 1 && version != VERSION) {
                return quarantined(id, tag, "blob version " + version + ", this build reads " + VERSION);
            }
            if (version == VERSION) {
                if (!tag.contains("launchPending", Tag.TAG_BYTE)
                        || (tag.getByte("launchPending") != 0 && tag.getByte("launchPending") != 1)) {
                    return quarantined(id, tag, "the blob has no valid lobby launch transaction flag");
                }
                if (tag.getBoolean("launchPending")) {
                    return quarantined(id, tag, "incomplete lobby launch transaction");
                }
            }
            if (!tag.contains("epoch", Tag.TAG_ANY_NUMERIC)) {
                return quarantined(id, tag, "the blob has no save epoch");
            }
            if (!tag.contains("snapshot", Tag.TAG_COMPOUND)) {
                return quarantined(id, tag, "the blob has no snapshot");
            }
            DataResult<MatchSnapshot> decoded =
                    MatchSnapshotCodec.decode(NbtBridge.dynamic(tag.getCompound("snapshot")), Migrations.production());
            if (decoded.error().isPresent()) {
                return quarantined(
                        id,
                        tag,
                        "the snapshot does not decode: " + decoded.error().get().message());
            }
            MatchSnapshot snapshot = decoded.getOrThrow();
            if (!snapshot.config().id().equals(id)) {
                return quarantined(
                        id, tag, "the blob holds match " + snapshot.config().id() + ", not " + id);
            }
            MatchData data = new MatchData(id, State.LOADED, new MatchRoster(clock, reclaimNanos));
            data.loaded = snapshot;
            data.loadedEpoch = tag.getLong("epoch");
            Tag rosterTag = tag.get("roster");
            if (version == VERSION && rosterTag == null) {
                return quarantined(id, tag, "the blob has no roster session envelope");
            }
            if (rosterTag != null) {
                if (!(rosterTag instanceof ListTag listed)
                        || (!listed.isEmpty() && listed.getElementType() != Tag.TAG_COMPOUND)) {
                    return quarantined(id, tag, "the roster is not a list of seats");
                }
                data.roster = version == 1
                        ? MatchRoster.load(listed, clock, reclaimNanos)
                        : MatchRoster.loadSessions(listed, clock, reclaimNanos);
                for (var member : data.roster.members()) {
                    var slot = data.roster.slotOf(member);
                    if (slot == null
                            || (!slot.isAdmin()
                                    && snapshot.diplomacy().factionOf(slot).isEmpty())) {
                        return quarantined(id, tag, "a roster seat has no primary authority binding");
                    }
                }
            }
            return data;
        } catch (RuntimeException e) {
            return quarantined(id, tag, "cannot read the blob: " + e);
        }
    }

    private static MatchData quarantined(MatchId id, CompoundTag tag, String reason) {
        MatchData data = new MatchData(id, State.QUARANTINED);
        data.raw = tag.copy();
        data.quarantineReason = reason;
        return data;
    }

    public MatchId id() {
        return id;
    }

    public State state() {
        return state;
    }

    public @Nullable MatchSnapshot loadedSnapshot() {
        return loaded;
    }

    /** The epoch the loaded blob was saved in. */
    public long loadedEpoch() {
        return loadedEpoch;
    }

    public @Nullable String quarantineReason() {
        return quarantineReason;
    }

    /** Who sits in the match: the runtime's roster, saved with the snapshot. */
    public MatchRoster roster() {
        return roster;
    }

    public boolean isLobbyLaunchPending() {
        return launchPending;
    }

    /** Brackets production allocation/setup/admission; interruption fails closed on the next load. */
    public void beginLobbyLaunch() {
        if (state != State.LIVE || launchPending) {
            throw new IllegalStateException("only a live match without a pending launch can begin one");
        }
        launchPending = true;
        setDirty();
    }

    public void completeLobbyLaunch() {
        if (state != State.LIVE || !launchPending) {
            throw new IllegalStateException("only a pending live lobby launch can complete");
        }
        launchPending = false;
        setDirty();
    }

    /** LOADED to LIVE once its match is restored: from now on saving encodes that match. */
    public void attach(Supplier<MatchSnapshot> snapshot, LongSupplier epoch) {
        if (state != State.LOADED) {
            throw new IllegalStateException("only a LOADED blob attaches, this one is " + state);
        }
        this.state = State.LIVE;
        this.snapshot = snapshot;
        this.epoch = epoch;
        this.loaded = null;
    }

    /** DISPOSED: the caller deletes the file; the cached object is never dirty again, so nothing rewrites it. */
    public void dispose() {
        state = State.DISPOSED;
        snapshot = null;
        epoch = null;
        loaded = null;
        setDirty(false);
    }

    /** Only LIVE and LOADED blobs are ever written: a quarantined file stays byte for byte as it was. */
    @Override
    public boolean isDirty() {
        return (state == State.LIVE || state == State.LOADED) && super.isDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        switch (state) {
            case LIVE -> {
                Supplier<MatchSnapshot> live = snapshot;
                LongSupplier saveEpoch = epoch;
                if (live == null || saveEpoch == null) {
                    throw new IllegalStateException("a LIVE blob without its match");
                }
                write(tag, saveEpoch.getAsLong(), live.get(), roster, launchPending);
            }
            case LOADED -> {
                MatchSnapshot kept = loaded;
                if (kept == null) {
                    throw new IllegalStateException("a LOADED blob without its snapshot");
                }
                write(tag, loadedEpoch, kept, roster, false);
            }
            case QUARANTINED -> {
                CompoundTag kept = raw;
                return kept == null ? tag : kept.copy();
            }
            case DISPOSED -> {}
        }
        return tag;
    }

    /** Current blobs always name the session envelope, even when nobody is seated. */
    private static void write(
            CompoundTag tag, long epoch, MatchSnapshot snapshot, MatchRoster roster, boolean launchPending) {
        tag.putInt("v", VERSION);
        tag.putLong("epoch", epoch);
        tag.putBoolean("launchPending", launchPending);
        tag.put("snapshot", MatchSnapshotCodec.encode(NbtOps.INSTANCE, snapshot));
        tag.put("roster", roster.saveSessions());
    }
}
