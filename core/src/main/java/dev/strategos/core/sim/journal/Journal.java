package dev.strategos.core.sim.journal;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A read-only journal: contiguous segments, oldest first, each a start snapshot, the inputs of every tick after it that
 * had any (empty ticks are implicit) up to its last tick, and state-hash checkpoints. Replaying from any segment's
 * snapshot must reproduce that segment's checkpoints and every later one.
 */
@CoreApi
public final class Journal {
    public static final int DEFAULT_CHECKPOINT_EVERY = 20;

    /** One hour of ticks: older segments are dropped. */
    public static final int DEFAULT_MAX_TICKS = 72_000;

    /** A new segment, with a fresh snapshot, every minute of game time. */
    public static final int SEGMENT_TICKS = 1_200;

    private final int checkpointEvery;
    private final List<Segment> segments;

    public Journal(int checkpointEvery, List<Segment> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("a journal has at least one segment");
        }
        for (int i = 1; i < segments.size(); i++) {
            if (segments.get(i).startTick() != segments.get(i - 1).lastTick()) {
                throw new IllegalArgumentException(
                        "segment " + i + " starts at " + segments.get(i).startTick() + ", the one before ends at "
                                + segments.get(i - 1).lastTick());
            }
        }
        this.checkpointEvery = checkpointEvery;
        this.segments = List.copyOf(segments);
    }

    /** Oldest first. */
    public List<Segment> segments() {
        return segments;
    }

    /** A journal of segment {@code index} alone, which replays from that segment's snapshot. */
    public Journal segment(int index) {
        return new Journal(checkpointEvery, List.of(segments.get(index)));
    }

    public MatchSnapshot start() {
        return segments.get(0).start();
    }

    public long startTick() {
        return start().tick();
    }

    public long lastTick() {
        return segments.get(segments.size() - 1).lastTick();
    }

    /** In tick order, across the segments. */
    public List<TickInputs> nonEmptyTicks() {
        List<TickInputs> all = new ArrayList<>();
        for (Segment s : segments) {
            all.addAll(s.nonEmptyTicks());
        }
        return all;
    }

    public int checkpointEvery() {
        return checkpointEvery;
    }

    public long[] checkpointTicks() {
        long[] all = new long[0];
        for (Segment s : segments) {
            all = concat(all, s.checkpointTicks);
        }
        return all;
    }

    public long[] checkpointHashes() {
        long[] all = new long[0];
        for (Segment s : segments) {
            all = concat(all, s.checkpointHashes);
        }
        return all;
    }

    private static long[] concat(long[] a, long[] b) {
        long[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * One segment: its start snapshot, its last tick, its non-empty ticks and its checkpoints, all after the start, and
     * the encoded slices of every terrain page it holds (its start's and its inputs'), so it replays on its own.
     */
    @CoreApi
    public static final class Segment {
        private final MatchSnapshot start;
        private final long lastTick;
        private final List<TickInputs> nonEmptyTicks;
        private final long[] checkpointTicks;
        private final long[] checkpointHashes;
        private final Map<Long, byte[]> slices;

        /** A segment without terrain. */
        public Segment(
                MatchSnapshot start,
                long lastTick,
                List<TickInputs> nonEmptyTicks,
                long[] checkpointTicks,
                long[] checkpointHashes) {
            this(start, lastTick, nonEmptyTicks, checkpointTicks, checkpointHashes, new TreeMap<>());
        }

        public Segment(
                MatchSnapshot start,
                long lastTick,
                List<TickInputs> nonEmptyTicks,
                long[] checkpointTicks,
                long[] checkpointHashes,
                Map<Long, byte[]> slices) {
            if (checkpointTicks.length != checkpointHashes.length) {
                throw new IllegalArgumentException("one hash per checkpoint tick");
            }
            if (lastTick < start.tick()) {
                throw new IllegalArgumentException("segment ends at " + lastTick + " before its start " + start.tick());
            }
            long previous = start.tick();
            for (TickInputs t : nonEmptyTicks) {
                if (t.tick() <= previous || t.tick() > lastTick) {
                    throw new IllegalArgumentException("inputs of tick " + t.tick() + " out of order or outside ("
                            + start.tick() + ", " + lastTick + "]");
                }
                previous = t.tick();
            }
            this.start = start;
            this.lastTick = lastTick;
            this.nonEmptyTicks = List.copyOf(nonEmptyTicks);
            this.checkpointTicks = checkpointTicks.clone();
            this.checkpointHashes = checkpointHashes.clone();
            this.slices = Collections.unmodifiableMap(new TreeMap<>(slices));
            for (PageRef ref : start.pages()) {
                if (!this.slices.containsKey(ref.slice())) {
                    throw new IllegalArgumentException("the segment at " + start.tick() + " lacks the slice of page ("
                            + ref.x() + ", " + ref.z() + ")");
                }
            }
        }

        /** A slice of this segment by its hash (a copy), or null. */
        public byte @Nullable [] slice(long hash) {
            byte[] slice = slices.get(hash);
            return slice == null ? null : slice.clone();
        }

        /** The hashes of the slices this segment holds, ascending. */
        public Set<Long> sliceHashes() {
            return slices.keySet();
        }

        public MatchSnapshot start() {
            return start;
        }

        public long startTick() {
            return start.tick();
        }

        public long lastTick() {
            return lastTick;
        }

        public List<TickInputs> nonEmptyTicks() {
            return nonEmptyTicks;
        }

        public long[] checkpointTicks() {
            return checkpointTicks.clone();
        }

        public long[] checkpointHashes() {
            return checkpointHashes.clone();
        }
    }
}
