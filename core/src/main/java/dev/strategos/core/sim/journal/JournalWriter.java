package dev.strategos.core.sim.journal;

import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.SliceSource;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Records a match's journal in segments; public but not {@code @CoreApi}: only the core writes it (rule B2 keeps the
 * mod out). A segment starts on every tick that is a multiple of {@code segmentTicks}, with a snapshot taken at the end
 * of that tick; the segments kept never span more than {@code maxTicks}.
 */
public final class JournalWriter {
    private final int checkpointEvery;
    private final int maxTicks;
    private final int segmentTicks;
    private final ArrayDeque<Journal.Segment> closed = new ArrayDeque<>();
    private MatchSnapshot start;
    private long lastTick;
    private final List<TickInputs> ticks = new ArrayList<>();
    private long[] checkpointTicks = new long[16];
    private long[] checkpointHashes = new long[16];
    private int checkpoints;
    /** The slice of every page now published, by page key. */
    private final Map<Long, Long> pages = new TreeMap<>();
    /** Encoded slices: those of the published pages and of the current segment's inputs. */
    private final Map<Long, byte[]> slices = new TreeMap<>();
    /** The current segment's slices: its start pages' and its inputs'. */
    private final Map<Long, byte[]> segmentSlices = new TreeMap<>();

    /** Segments of {@link Journal#SEGMENT_TICKS}, or of {@code maxTicks} when that is shorter. */
    public JournalWriter(MatchSnapshot start, int checkpointEvery, int maxTicks) {
        this(start, SliceSource.NONE, checkpointEvery, maxTicks);
    }

    /** As above, the start snapshot's pages taking their slices from {@code startSlices}. */
    public JournalWriter(MatchSnapshot start, SliceSource startSlices, int checkpointEvery, int maxTicks) {
        this(start, checkpointEvery, maxTicks, Math.min(Journal.SEGMENT_TICKS, maxTicks));
        for (PageRef ref : start.pages()) {
            byte[] slice = startSlices.slice(ref.slice());
            if (slice == null) {
                throw new IllegalArgumentException("no slice for page (" + ref.x() + ", " + ref.z() + ") of the start");
            }
            pages.put(key(ref.x(), ref.z()), ref.slice());
            slices.put(ref.slice(), slice);
            segmentSlices.put(ref.slice(), slice);
        }
    }

    public JournalWriter(MatchSnapshot start, int checkpointEvery, int maxTicks, int segmentTicks) {
        if (checkpointEvery < 1 || maxTicks < 1 || segmentTicks < 1 || segmentTicks > maxTicks) {
            throw new IllegalArgumentException("journal needs positive limits with segments no longer than the whole: "
                    + checkpointEvery + ", " + maxTicks + ", " + segmentTicks);
        }
        this.checkpointEvery = checkpointEvery;
        this.maxTicks = maxTicks;
        this.segmentTicks = segmentTicks;
        this.start = start;
        this.lastTick = start.tick();
    }

    public int checkpointEvery() {
        return checkpointEvery;
    }

    /** A tick's canonical inputs; an empty tick only moves the last tick. */
    public void record(TickInputs canonical) {
        if (canonical.tick() != lastTick + 1) {
            throw new IllegalArgumentException("recording tick " + canonical.tick() + " after tick " + lastTick);
        }
        if (!canonical.events().isEmpty()) {
            ticks.add(canonical);
        }
        for (InputEvent event : canonical.events()) {
            if (event instanceof TerrainPage page) {
                byte[] slice = page.slice();
                pages.put(key(page.coord().x(), page.coord().z()), page.sliceHash());
                slices.put(page.sliceHash(), slice);
                segmentSlices.put(page.sliceHash(), slice);
            }
        }
        lastTick = canonical.tick();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    public void checkpoint(long tick, long hash) {
        if (checkpoints == checkpointTicks.length) {
            checkpointTicks = Arrays.copyOf(checkpointTicks, checkpoints * 2);
            checkpointHashes = Arrays.copyOf(checkpointHashes, checkpoints * 2);
        }
        checkpointTicks[checkpoints] = tick;
        checkpointHashes[checkpoints] = hash;
        checkpoints++;
    }

    /** Whether the tick just recorded ends the current segment. */
    public boolean segmentDue() {
        return lastTick > start.tick() && lastTick % segmentTicks == 0;
    }

    /**
     * Closes the current segment at the last tick and starts the next from {@code next}, a snapshot of that tick; then
     * drops the oldest segments, so the kept ones span at most {@code maxTicks}.
     */
    public void startSegment(MatchSnapshot next) {
        if (next.tick() != lastTick) {
            throw new IllegalArgumentException("a segment snapshot of tick " + next.tick() + " at tick " + lastTick);
        }
        closed.addLast(current());
        start = next;
        ticks.clear();
        checkpoints = 0;
        slices.keySet().retainAll(pages.values());
        segmentSlices.clear();
        for (PageRef ref : next.pages()) {
            byte[] slice = slices.get(ref.slice());
            if (slice == null) {
                throw new IllegalStateException(
                        "no slice for page (" + ref.x() + ", " + ref.z() + ") of the new segment");
            }
            segmentSlices.put(ref.slice(), slice);
        }
        while (!closed.isEmpty() && closed.peekFirst().startTick() < lastTick - maxTicks + segmentTicks) {
            closed.removeFirst();
        }
    }

    /** An immutable copy of the journal so far. */
    public Journal view() {
        List<Journal.Segment> all = new ArrayList<>(closed);
        all.add(current());
        return new Journal(checkpointEvery, all);
    }

    private Journal.Segment current() {
        return new Journal.Segment(
                start,
                lastTick,
                ticks,
                Arrays.copyOf(checkpointTicks, checkpoints),
                Arrays.copyOf(checkpointHashes, checkpoints),
                segmentSlices);
    }
}
