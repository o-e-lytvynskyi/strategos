package dev.strategos.core.sim.journal;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.jspecify.annotations.Nullable;

/**
 * The binary journal file ({@code .strj}, 04 §5.5): a header, then records in tick order.
 *
 * <pre>
 * file    := "STRJ" u16(version) string(command catalogue fingerprint) varint(checkpointEvery) record*
 * record  := 'S' varlong(tick) bytes(snapshot: JSON with "v", deflated) varint(0, reserved)
 *          | 'L' long(slice hash) bytes(slice, run-length)      after its segment's 'S', once per slice
 *          | 'K' varlong(tick) varint(n) input*
 *          | 'C' varlong(tick) long(state hash)
 *          | 'E' varlong(last tick) varlong(live tick) long(live hash)
 * </pre>
 *
 * A segment runs from its 'S' to the next 'S' or the 'E', and holds the slices of its start's terrain pages (which the
 * snapshot lists, schema 2) and of its inputs', so it replays on its own. Snapshots migrate when read, as saves do; a
 * file written by another command catalogue is refused, since its command numbers mean other things. Version 1, before
 * terrain, still reads.
 */
@CoreApi
public final class JournalFormat {
    public static final int VERSION = 2;

    /** "STRJ" in ASCII. */
    private static final int MAGIC = 0x5354_524A;

    private static final int FINGERPRINT_BYTES = 4_096;
    private static final int MAX_DEFLATED = 64 << 20;
    private static final int MAX_INFLATED = 512 << 20;
    private static final int MAX_INPUTS_PER_TICK = 1 << 16;
    private static final Gson GSON = new Gson();

    private JournalFormat() {}

    public static byte[] encode(JournalExport export) {
        Journal journal = export.journal();
        WireWriter out = new WireWriter(4_096);
        out.writeInt(MAGIC);
        out.writeShort(VERSION);
        out.writeString(CommandKinds.DEFAULT.fingerprint(), FINGERPRINT_BYTES);
        out.writeVarInt(journal.checkpointEvery());
        for (Journal.Segment segment : journal.segments()) {
            out.writeByte('S');
            out.writeVarLong(segment.startTick());
            out.writeBytes(deflate(snapshotJson(segment.start())));
            out.writeVarInt(0);
            for (long hash : segment.sliceHashes()) {
                out.writeByte('L');
                out.writeLong(hash);
                out.writeBytes(Objects.requireNonNull(segment.slice(hash)));
            }
            List<TickInputs> ticks = segment.nonEmptyTicks();
            long[] checkpointTicks = segment.checkpointTicks();
            long[] checkpointHashes = segment.checkpointHashes();
            int t = 0;
            int c = 0;
            while (t < ticks.size() || c < checkpointTicks.length) {
                boolean inputsFirst = c == checkpointTicks.length
                        || (t < ticks.size() && ticks.get(t).tick() <= checkpointTicks[c]);
                if (inputsFirst) {
                    TickInputs tick = ticks.get(t++);
                    out.writeByte('K');
                    out.writeVarLong(tick.tick());
                    out.writeVarInt(tick.events().size());
                    for (InputEvent event : tick.events()) {
                        InputCodec.write(out, event);
                    }
                } else {
                    out.writeByte('C');
                    out.writeVarLong(checkpointTicks[c]);
                    out.writeLong(checkpointHashes[c]);
                    c++;
                }
            }
        }
        out.writeByte('E');
        out.writeVarLong(journal.lastTick());
        out.writeVarLong(export.liveTick());
        out.writeLong(export.liveHash());
        return out.toByteArray();
    }

    /** Reads a whole file; anything malformed throws {@link WireException} with the reason. */
    public static JournalExport decode(byte[] bytes) {
        WireReader in = new WireReader(bytes);
        if (in.readInt() != MAGIC) {
            throw new WireException("not a Strategos journal (no STRJ header)");
        }
        int version = in.readUnsignedShort();
        if (version != VERSION && version != 1) {
            throw new WireException("journal format version " + version + "; this build reads 1 and " + VERSION);
        }
        String fingerprint = in.readString(FINGERPRINT_BYTES);
        if (!CommandKinds.DEFAULT.reads(fingerprint)) {
            throw new WireException("journal written with commands [" + fingerprint + "]; this build has ["
                    + CommandKinds.DEFAULT.fingerprint() + "]");
        }
        int checkpointEvery = in.readVarInt();
        List<Journal.Segment> segments = new ArrayList<>();
        @Nullable SegmentBuilder current = null;
        while (true) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 'S' -> {
                    long tick = in.readVarLong();
                    MatchSnapshot snapshot = snapshot(inflate(in.readBytes(MAX_DEFLATED)));
                    if (snapshot.tick() != tick) {
                        throw new WireException(
                                "segment at tick " + tick + " holds a snapshot of tick " + snapshot.tick());
                    }
                    if (in.readVarInt() != 0) {
                        throw new WireException("terrain pages in a journal this build cannot read");
                    }
                    if (current != null) {
                        segments.add(current.build(tick));
                    }
                    current = new SegmentBuilder(snapshot);
                }
                case 'L' -> {
                    SegmentBuilder segment = require(current, tag);
                    long hash = in.readLong();
                    segment.slices.put(hash, in.readBytes(MAX_DEFLATED));
                }
                case 'K' -> {
                    SegmentBuilder segment = require(current, tag);
                    long tick = in.readVarLong();
                    int n = in.readVarInt(MAX_INPUTS_PER_TICK);
                    List<InputEvent> events = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        events.add(InputCodec.read(in, segment.slices::get));
                    }
                    segment.ticks.add(new TickInputs(tick, events));
                }
                case 'C' -> {
                    SegmentBuilder segment = require(current, tag);
                    segment.addCheckpoint(in.readVarLong(), in.readLong());
                }
                case 'E' -> {
                    SegmentBuilder segment = require(current, tag);
                    long lastTick = in.readVarLong();
                    long liveTick = in.readVarLong();
                    long liveHash = in.readLong();
                    in.expectEnd();
                    segments.add(segment.build(lastTick));
                    try {
                        return new JournalExport(new Journal(checkpointEvery, segments), liveTick, liveHash);
                    } catch (IllegalArgumentException e) {
                        throw new WireException(String.valueOf(e.getMessage()));
                    }
                }
                default -> throw new WireException("unknown journal record '" + (char) tag + "' (" + tag + ")");
            }
        }
    }

    private static SegmentBuilder require(@Nullable SegmentBuilder current, int tag) {
        if (current == null) {
            throw new WireException("record '" + (char) tag + "' before the first segment");
        }
        return current;
    }

    private static byte[] snapshotJson(MatchSnapshot snapshot) {
        JsonElement json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot);
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }

    private static MatchSnapshot snapshot(byte[] json) {
        JsonElement element;
        try {
            element = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            throw new WireException("a segment snapshot is not JSON: " + e.getMessage());
        }
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, element), Migrations.production())
                .getOrThrow(message -> new WireException("a segment snapshot does not decode: " + message));
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] chunk = new byte[8_192];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] deflated) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(deflated);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, deflated.length * 4));
            byte[] chunk = new byte[8_192];
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new WireException("a truncated compressed snapshot");
                }
                out.write(chunk, 0, n);
                if (out.size() > MAX_INFLATED) {
                    throw new WireException("a snapshot above " + MAX_INFLATED + " bytes");
                }
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new WireException("a corrupt compressed snapshot: " + e.getMessage());
        } finally {
            inflater.end();
        }
    }

    private static final class SegmentBuilder {
        private final MatchSnapshot start;
        private final List<TickInputs> ticks = new ArrayList<>();
        private final Map<Long, byte[]> slices = new TreeMap<>();
        private long[] checkpointTicks = new long[16];
        private long[] checkpointHashes = new long[16];
        private int checkpoints;

        SegmentBuilder(MatchSnapshot start) {
            this.start = start;
        }

        void addCheckpoint(long tick, long hash) {
            if (checkpoints == checkpointTicks.length) {
                checkpointTicks = Arrays.copyOf(checkpointTicks, checkpoints * 2);
                checkpointHashes = Arrays.copyOf(checkpointHashes, checkpoints * 2);
            }
            checkpointTicks[checkpoints] = tick;
            checkpointHashes[checkpoints] = hash;
            checkpoints++;
        }

        Journal.Segment build(long lastTick) {
            try {
                return new Journal.Segment(
                        start,
                        lastTick,
                        ticks,
                        Arrays.copyOf(checkpointTicks, checkpoints),
                        Arrays.copyOf(checkpointHashes, checkpoints),
                        slices);
            } catch (IllegalArgumentException e) {
                throw new WireException(String.valueOf(e.getMessage()));
            }
        }
    }
}
