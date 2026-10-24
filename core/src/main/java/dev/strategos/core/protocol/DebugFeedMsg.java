package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code debug_feed} payload (PATH-10, 04 §15.2): one layer of the movement overlay for one subscriber, stamped
 * with the server tick it was built at, so the parts of one update share their stamp. Positions are whole 1/16 block
 * from the match's {@link Origin}, the world block of its local (0, 0) at its band's lowest Y. Every list has a bound,
 * so the largest payload stays under 64 KiB (spec §2.7).
 */
@CoreApi
public record DebugFeedMsg(DebugLayer layer, long tick, DebugFeedMsg.Payload payload) {
    /** A coordinate in 1/16 block lies in {@code [-COORD_LIMIT, COORD_LIMIT)}: three bytes on the wire. */
    public static final int COORD_LIMIT = 1 << 20;

    /** A page's surfaces: 256 columns of at most four. */
    public static final int MAX_CELLS = 1_024;

    public static final int MAX_PAGE = 1_023;
    public static final int MAX_FLOOR = 4_095;

    /** Paths in one payload; an update with more sends several. */
    public static final int MAX_LINES = 200;

    /** A path's points: where the unit stands, then at most 32 waypoints ahead. */
    public static final int MAX_POINTS = 33;

    /** Unit markers in one payload; an update with more sends several. */
    public static final int MAX_MARKS = 2_000;

    public static final int MAX_TIMINGS = 16;

    /** UTF-8 bytes of a timer's name. */
    public static final int NAME_BYTES = 24;

    public DebugFeedMsg {
        if (tick < 0) {
            throw new IllegalArgumentException("tick " + tick + " below 0");
        }
        boolean fits = switch (layer) {
            case SURFACES, COMPONENTS -> payload instanceof Cells;
            case PATHS -> payload instanceof Paths;
            case UNITS -> payload instanceof Marks;
            case PANEL -> payload instanceof Panel;
        };
        if (!fits) {
            throw new IllegalArgumentException(
                    layer + " cannot carry a " + payload.getClass().getSimpleName());
        }
    }

    /** What one layer carries. */
    public sealed interface Payload permits Cells, Paths, Marks, Panel {}

    /** The world block of a match's local (0, 0) at its band's lowest Y. */
    public record Origin(int x, int y, int z) {}

    /**
     * One surface: its column in the page ({@code x * 16 + z}), its floor in 1/16 above the band's bottom, and a value: on
     * {@link DebugLayer#SURFACES} its kind (bits 0–2, the core's {@code Surface} kinds) and its layer in the column (bits
     * 3–4, the topmost 0); on {@link DebugLayer#COMPONENTS} its component's label.
     */
    public record Cell(int column, int floor, int value) {
        public Cell {
            if (column < 0 || column > 255 || floor < 0 || floor > MAX_FLOOR || value < 0) {
                throw new IllegalArgumentException("cell " + column + ", floor " + floor + ", value " + value);
            }
        }
    }

    /** One page of a match's surfaces, at its page coordinates. */
    public record Cells(Origin origin, int pageX, int pageZ, List<Cell> cells) implements Payload {
        public Cells {
            if (pageX < 0 || pageX > MAX_PAGE || pageZ < 0 || pageZ > MAX_PAGE || cells.size() > MAX_CELLS) {
                throw new IllegalArgumentException(
                        "page " + pageX + ", " + pageZ + " with " + cells.size() + " surfaces");
            }
            cells = List.copyOf(cells);
        }
    }

    /** A point, in 1/16 block from the origin. */
    public record Point(int x, int y, int z) {
        public Point {
            if (!inRange(x) || !inRange(y) || !inRange(z)) {
                throw new IllegalArgumentException("point " + x + ", " + y + ", " + z + " too far from the origin");
            }
        }

        /** The point nearest {@code (x, y, z)} blocks from the origin. */
        public static Point ofBlocks(double x, double y, double z) {
            return new Point(sixteenths(x), sixteenths(y), sixteenths(z));
        }

        private static boolean inRange(int v) {
            return v >= -COORD_LIMIT && v < COORD_LIMIT;
        }

        private static int sixteenths(double blocks) {
            return (int) Math.max(-COORD_LIMIT, Math.min(COORD_LIMIT - 1, Math.round(blocks * 16)));
        }
    }

    /**
     * One unit's path: its handle; how its last search ended, by the core's {@code PathStatus} code (0 reaches the goal,
     * 1 partial, 2 no path, 3 busy); whether a request is in flight; and the line from where it stands through the
     * waypoints ahead, or straight to its target while it waits with none.
     */
    public record PathLine(int handle, int status, boolean waiting, List<Point> points) {
        public PathLine {
            if (status < 0 || status > 3 || points.isEmpty() || points.size() > MAX_POINTS) {
                throw new IllegalArgumentException("path of status " + status + " with " + points.size() + " points");
            }
            points = List.copyOf(points);
        }
    }

    /** The paths of the units in the area. */
    public record Paths(Origin origin, List<PathLine> lines) implements Payload {
        public Paths {
            if (lines.size() > MAX_LINES) {
                throw new IllegalArgumentException(lines.size() + " paths, more than " + MAX_LINES);
            }
            lines = List.copyOf(lines);
        }
    }

    /**
     * One unit's marker: its handle, where it stands, its activity tier (UNIT-05's code: 0 idle, 1 moving, 2 working, 3 active), its ticks without
     * progress, and the rung of the stuck ladder it is on (0 none).
     */
    public record Mark(int handle, Point at, int tier, int stuck, int rung) {
        public Mark {
            if (tier < 0 || tier > 255 || stuck < 0 || rung < 0 || rung > 255) {
                throw new IllegalArgumentException("mark of tier " + tier + ", stuck " + stuck + ", rung " + rung);
            }
        }
    }

    /** The markers of the units in the area. */
    public record Marks(Origin origin, List<Mark> marks) implements Payload {
        public Marks {
            if (marks.size() > MAX_MARKS) {
                throw new IllegalArgumentException(marks.size() + " marks, more than " + MAX_MARKS);
            }
            marks = List.copyOf(marks);
        }
    }

    /** One timer's 95th percentile over its window, in nanoseconds. */
    public record Timing(String name, long p95) {}

    /**
     * The match's numbers: its id, last tick and units; the path service's queue depth and its counts since the match
     * started (requests, those decided at once, results by status, results dropped as superseded, expansions and the
     * most of one search); the 95th percentile of the latency of its last results, in ticks; its time joining jobs on
     * the main thread, in nanoseconds since the match started; and the 95th percentile of each phase timer.
     */
    public record Panel(
            int matchId,
            long matchTick,
            int units,
            int queue,
            long requests,
            long decided,
            long ok,
            long partial,
            long noPath,
            long superseded,
            long expansions,
            int maxExpansions,
            long latencyP95,
            long joinNanos,
            List<Timing> timings)
            implements Payload {
        public Panel {
            if (matchId < 0
                    || matchTick < 0
                    || units < 0
                    || queue < 0
                    || (requests | decided | ok | partial | noPath | superseded | expansions) < 0
                    || maxExpansions < 0
                    || latencyP95 < 0
                    || joinNanos < 0
                    || timings.size() > MAX_TIMINGS) {
                throw new IllegalArgumentException("a panel with a negative number or " + timings.size() + " timings");
            }
            timings = List.copyOf(timings);
        }
    }

    /** The layer's code, the stamp, then the layer's payload. */
    public static final WireCodec<DebugFeedMsg> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, DebugFeedMsg value) {
            out.writeByte(value.layer().code());
            out.writeVarLong(value.tick());
            switch (value.payload()) {
                case Cells c -> CELLS.write(out, c);
                case Paths p -> PATHS.write(out, p);
                case Marks m -> MARKS.write(out, m);
                case Panel p -> PANEL.write(out, p);
            }
        }

        @Override
        public DebugFeedMsg read(WireReader in) {
            DebugLayer layer = DebugLayer.ofCode(in.readUnsignedByte());
            long tick = in.readVarLong();
            try {
                Payload payload = switch (layer) {
                    case SURFACES, COMPONENTS -> CELLS.read(in);
                    case PATHS -> PATHS.read(in);
                    case UNITS -> MARKS.read(in);
                    case PANEL -> PANEL.read(in);
                };
                return new DebugFeedMsg(layer, tick, payload);
            } catch (IllegalArgumentException e) {
                throw new WireException(String.valueOf(e.getMessage()));
            }
        }

        @Override
        public int maxSize() {
            int payload =
                    Math.max(Math.max(CELLS.maxSize(), PATHS.maxSize()), Math.max(MARKS.maxSize(), PANEL.maxSize()));
            return 1 + 9 + payload;
        }
    };

    /** Three zigzag varints of whole blocks. */
    static final WireCodec<Origin> ORIGIN = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Origin value) {
            out.writeZigZag(value.x()).writeZigZag(value.y()).writeZigZag(value.z());
        }

        @Override
        public Origin read(WireReader in) {
            return new Origin(in.readZigZagInt(), in.readZigZagInt(), in.readZigZagInt());
        }

        @Override
        public int maxSize() {
            return 3 * 5;
        }
    };

    /** The origin, the page, a count, then per surface its column as a byte, its floor and its value. */
    static final WireCodec<Cells> CELLS = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Cells value) {
            ORIGIN.write(out, value.origin());
            out.writeVarInt(value.pageX())
                    .writeVarInt(value.pageZ())
                    .writeVarInt(value.cells().size());
            for (Cell cell : value.cells()) {
                out.writeByte(cell.column()).writeVarInt(cell.floor()).writeVarInt(cell.value());
            }
        }

        @Override
        public Cells read(WireReader in) {
            Origin origin = ORIGIN.read(in);
            int pageX = in.readVarInt(MAX_PAGE);
            int pageZ = in.readVarInt(MAX_PAGE);
            int count = in.readVarInt(MAX_CELLS);
            List<Cell> cells = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                cells.add(new Cell(in.readUnsignedByte(), in.readVarInt(MAX_FLOOR), in.readVarInt()));
            }
            return new Cells(origin, pageX, pageZ, cells);
        }

        @Override
        public int maxSize() {
            return ORIGIN.maxSize() + 2 + 2 + 2 + MAX_CELLS * (1 + 2 + 5);
        }
    };

    /** Three zigzag varints, each at most three bytes. */
    static final WireCodec<Point> POINT = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Point value) {
            out.writeZigZag(value.x()).writeZigZag(value.y()).writeZigZag(value.z());
        }

        @Override
        public Point read(WireReader in) {
            return new Point(in.readZigZagInt(), in.readZigZagInt(), in.readZigZagInt());
        }

        @Override
        public int maxSize() {
            return 3 * 3;
        }
    };

    /** The origin, a count, then per path its handle, status, flag, point count and points. */
    static final WireCodec<Paths> PATHS = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Paths value) {
            ORIGIN.write(out, value.origin());
            out.writeVarInt(value.lines().size());
            for (PathLine line : value.lines()) {
                out.writeInt(line.handle()).writeByte(line.status()).writeBoolean(line.waiting());
                out.writeVarInt(line.points().size());
                for (Point point : line.points()) {
                    POINT.write(out, point);
                }
            }
        }

        @Override
        public Paths read(WireReader in) {
            Origin origin = ORIGIN.read(in);
            int count = in.readVarInt(MAX_LINES);
            List<PathLine> lines = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                int handle = in.readInt();
                int status = in.readUnsignedByte();
                boolean waiting = in.readBoolean();
                int n = in.readVarInt(MAX_POINTS);
                List<Point> points = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    points.add(POINT.read(in));
                }
                lines.add(new PathLine(handle, status, waiting, points));
            }
            return new Paths(origin, lines);
        }

        @Override
        public int maxSize() {
            return ORIGIN.maxSize() + 2 + MAX_LINES * (4 + 1 + 1 + 1 + MAX_POINTS * POINT.maxSize());
        }
    };

    /** The origin, a count, then per marker its handle, point, tier, stuck ticks and rung. */
    static final WireCodec<Marks> MARKS = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Marks value) {
            ORIGIN.write(out, value.origin());
            out.writeVarInt(value.marks().size());
            for (Mark mark : value.marks()) {
                out.writeInt(mark.handle());
                POINT.write(out, mark.at());
                out.writeByte(mark.tier()).writeVarInt(mark.stuck()).writeByte(mark.rung());
            }
        }

        @Override
        public Marks read(WireReader in) {
            Origin origin = ORIGIN.read(in);
            int count = in.readVarInt(MAX_MARKS);
            List<Mark> marks = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                marks.add(new Mark(
                        in.readInt(), POINT.read(in), in.readUnsignedByte(), in.readVarInt(), in.readUnsignedByte()));
            }
            return new Marks(origin, marks);
        }

        @Override
        public int maxSize() {
            return ORIGIN.maxSize() + 2 + MAX_MARKS * (4 + POINT.maxSize() + 1 + 5 + 1);
        }
    };

    /** Every number as a varint, then a count and each timing's name and percentile. */
    static final WireCodec<Panel> PANEL = new WireCodec<>() {
        @Override
        public void write(WireWriter out, Panel v) {
            out.writeVarInt(v.matchId())
                    .writeVarLong(v.matchTick())
                    .writeVarInt(v.units())
                    .writeVarInt(v.queue());
            out.writeVarLong(v.requests()).writeVarLong(v.decided()).writeVarLong(v.ok());
            out.writeVarLong(v.partial()).writeVarLong(v.noPath()).writeVarLong(v.superseded());
            out.writeVarLong(v.expansions()).writeVarInt(v.maxExpansions());
            out.writeVarLong(v.latencyP95()).writeVarLong(v.joinNanos());
            out.writeVarInt(v.timings().size());
            for (Timing timing : v.timings()) {
                out.writeString(timing.name(), NAME_BYTES).writeVarLong(timing.p95());
            }
        }

        @Override
        public Panel read(WireReader in) {
            int matchId = in.readVarInt();
            long matchTick = in.readVarLong();
            int units = in.readVarInt();
            int queue = in.readVarInt();
            long requests = in.readVarLong();
            long decided = in.readVarLong();
            long ok = in.readVarLong();
            long partial = in.readVarLong();
            long noPath = in.readVarLong();
            long superseded = in.readVarLong();
            long expansions = in.readVarLong();
            int maxExpansions = in.readVarInt();
            long latencyP95 = in.readVarLong();
            long joinNanos = in.readVarLong();
            int count = in.readVarInt(MAX_TIMINGS);
            List<Timing> timings = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                timings.add(new Timing(in.readString(NAME_BYTES), in.readVarLong()));
            }
            return new Panel(
                    matchId,
                    matchTick,
                    units,
                    queue,
                    requests,
                    decided,
                    ok,
                    partial,
                    noPath,
                    superseded,
                    expansions,
                    maxExpansions,
                    latencyP95,
                    joinNanos,
                    timings);
        }

        @Override
        public int maxSize() {
            // four varints of int, ten of long; then the timings' count and each name and percentile
            return 4 * 5 + 10 * 9 + 1 + MAX_TIMINGS * (1 + NAME_BYTES + 9);
        }
    };
}
