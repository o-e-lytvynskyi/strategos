package dev.strategos.core.sim.nav;

import dev.strategos.core.port.JobHandle;
import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Each unit's path, by handle index (04 §9.3): the waypoints it follows and the next one, the status they came with,
 * the request it waits for (0 for none), the tick of its last request, and whether changed terrain wants a new one;
 * and the match's requests in flight, by id, with the tick each is due. All of it is part of the snapshot and the state
 * hash; only the flag an order sets for this tick's request is not.
 */
public final class PathStore {
    /** The tick of a unit that never asked: the repath limit does not hold it back. */
    public static final long NEVER = Long.MIN_VALUE;

    private Waypoints[] path = new Waypoints[0];
    private int[] cursor = new int[0];
    private byte[] status = new byte[0];
    private int[] request = new int[0];
    private long[] lastRequest = new long[0];
    private boolean[] repath = new boolean[0];
    private boolean[] wanted = new boolean[0];
    private int nextRequestId = 1;
    private final TreeMap<Integer, Pending> pending = new TreeMap<>();

    /**
     * Up to {@link #MAX} points to walk through in order: column centres, the last one the goal itself when the path
     * reaches it, each with the floor of its surface in 1/16 above the band's bottom. {@code truncated}: the search
     * found more, and the unit asks again when it reaches the {@link PathService#REASK_AT}th.
     */
    @CoreApi
    public static final class Waypoints {
        public static final int MAX = 64;
        public static final Waypoints NONE = new Waypoints(new double[0], new double[0], new int[0], false);

        private final double[] x;
        private final double[] z;
        private final int[] floor;
        private final boolean truncated;

        public Waypoints(double[] x, double[] z, int[] floor, boolean truncated) {
            if (x.length != z.length || x.length != floor.length) {
                throw new IllegalArgumentException("waypoint coordinates of different lengths: " + x.length + ", "
                        + z.length + ", " + floor.length);
            }
            if (x.length > MAX) {
                throw new IllegalArgumentException(x.length + " waypoints, more than " + MAX);
            }
            this.x = x.clone();
            this.z = z.clone();
            this.floor = floor.clone();
            this.truncated = truncated;
        }

        /** One waypoint. */
        public static Waypoints of(double x, double z, int floor) {
            return new Waypoints(new double[] {x}, new double[] {z}, new int[] {floor}, false);
        }

        public int count() {
            return x.length;
        }

        public double x(int i) {
            return x[i];
        }

        public double z(int i) {
            return z[i];
        }

        public int floor(int i) {
            return floor[i];
        }

        public boolean truncated() {
            return truncated;
        }

        /** Copies of the coordinates, for codecs. */
        public double[] xs() {
            return x.clone();
        }

        public double[] zs() {
            return z.clone();
        }

        public int[] floors() {
            return floor.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Waypoints other
                    && other.truncated == truncated
                    && Arrays.equals(other.x, x)
                    && Arrays.equals(other.z, z)
                    && Arrays.equals(other.floor, floor);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(x) * 31 + Arrays.hashCode(z) * 17 + Arrays.hashCode(floor) + (truncated ? 1 : 0);
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("Waypoints[");
            for (int i = 0; i < x.length; i++) {
                b.append(i == 0 ? "" : " ")
                        .append('(')
                        .append(x[i])
                        .append(", ")
                        .append(z[i])
                        .append(')');
            }
            return b.append(truncated ? " ...]" : "]").toString();
        }
    }

    /** One unit's path as a snapshot keeps it. */
    @CoreApi
    public record UnitPath(
            Waypoints waypoints, int cursor, PathStatus status, int request, long lastRequest, boolean repath) {
        /** A unit that never asked for a path. */
        public static final UnitPath NONE = new UnitPath(Waypoints.NONE, 0, PathStatus.OK, 0, NEVER, false);

        public UnitPath {
            if (cursor < 0 || cursor > waypoints.count()) {
                throw new IllegalArgumentException("waypoint cursor " + cursor + " outside 0.." + waypoints.count());
            }
        }
    }

    /** A request in flight: its job, and the tick its result is applied. */
    public record Pending(PathRequest request, long due, JobHandle<PathResult> job) {}

    /** A request in flight as a snapshot keeps it, its result already computed. */
    @CoreApi
    public record Delivery(PathRequest request, long due, PathResult result) {}

    /** The match's requests in flight and the next request id, as a snapshot keeps them. */
    @CoreApi
    public record Queue(int nextRequestId, List<Delivery> deliveries) {
        public static final Queue EMPTY = new Queue(1, List.of());

        public Queue {
            if (nextRequestId < 1) {
                throw new IllegalArgumentException("request ids start at 1, not " + nextRequestId);
            }
            deliveries = List.copyOf(deliveries);
        }
    }

    public void ensureCapacity(int size) {
        if (size > path.length) {
            int old = path.length;
            int capacity = Math.max(size, old * 2);
            path = Arrays.copyOf(path, capacity);
            Arrays.fill(path, old, capacity, Waypoints.NONE);
            cursor = Arrays.copyOf(cursor, capacity);
            status = Arrays.copyOf(status, capacity);
            request = Arrays.copyOf(request, capacity);
            lastRequest = Arrays.copyOf(lastRequest, capacity);
            Arrays.fill(lastRequest, old, capacity, NEVER);
            repath = Arrays.copyOf(repath, capacity);
            wanted = Arrays.copyOf(wanted, capacity);
        }
    }

    /** A new unit at index {@code i}: no path, no request, never asked. */
    public void reset(int i) {
        set(i, UnitPath.NONE);
        wanted[i] = false;
    }

    public Waypoints path(int i) {
        return path[i];
    }

    public int cursor(int i) {
        return cursor[i];
    }

    public PathStatus status(int i) {
        return PathStatus.ofCode(status[i]);
    }

    /** The request id the unit waits for, 0 for none: a result with another id is superseded. */
    public int request(int i) {
        return request[i];
    }

    public long lastRequest(int i) {
        return lastRequest[i];
    }

    public boolean repath(int i) {
        return repath[i];
    }

    /** The unit's path as a snapshot keeps it. */
    public UnitPath unitPath(int i) {
        return new UnitPath(path[i], cursor[i], status(i), request[i], lastRequest[i], repath[i]);
    }

    public void set(int i, UnitPath p) {
        path[i] = p.waypoints();
        cursor[i] = p.cursor();
        status[i] = (byte) p.status().code();
        request[i] = p.request();
        lastRequest[i] = p.lastRequest();
        repath[i] = p.repath();
    }

    /** A new path to follow from its first waypoint; the request it answers is no longer awaited. */
    public void follow(int i, Waypoints w, PathStatus s) {
        path[i] = w;
        cursor[i] = 0;
        status[i] = (byte) s.code();
        request[i] = 0;
    }

    /** No path and no request: a stop or an arrival; a result still in flight will be dropped. */
    public void clear(int i) {
        path[i] = Waypoints.NONE;
        cursor[i] = 0;
        status[i] = (byte) PathStatus.OK.code();
        request[i] = 0;
        repath[i] = false;
    }

    /** The unit reached its next waypoint. */
    public void advance(int i) {
        cursor[i]++;
    }

    /** The unit waits for request {@code id}, made at {@code tick}; its current path stays until the result. */
    public void awaiting(int i, int id, long tick) {
        request[i] = id;
        lastRequest[i] = tick;
        repath[i] = false;
    }

    /** A request decided at once, without an id. */
    public void decided(int i, long tick) {
        lastRequest[i] = tick;
        repath[i] = false;
    }

    public void setRepath(int i, boolean value) {
        repath[i] = value;
    }

    /** An order arrived this tick: the unit asks for a path in this tick's request pass. */
    public void want(int i) {
        wanted[i] = true;
    }

    /** Whether an order arrived this tick, clearing the flag. */
    public boolean takeWanted(int i) {
        boolean w = wanted[i];
        wanted[i] = false;
        return w;
    }

    /** Takes the next request id. */
    public int nextRequestId() {
        return nextRequestId++;
    }

    /** The id the next request will take. */
    public int peekNextRequestId() {
        return nextRequestId;
    }

    public void put(Pending p) {
        pending.put(p.request().id(), p);
    }

    /** The requests in flight, by id; ids grow with the tick they were made, so due ticks never decrease. */
    public Collection<Pending> pending() {
        return pending.values();
    }

    /** The first request in flight, removed, if it is due by {@code tick}; null otherwise. */
    public @Nullable Pending pollDue(long tick) {
        Map.Entry<Integer, Pending> first = pending.firstEntry();
        if (first == null || first.getValue().due() > tick) {
            return null;
        }
        pending.pollFirstEntry();
        return first.getValue();
    }

    /**
     * The queue as a snapshot keeps it: every job in flight joined (quiesced), its result kept with its due tick. A
     * {@code STRICT} result does not depend on when its job ran, so joining early changes nothing.
     */
    public Queue queue() {
        List<Delivery> deliveries = new ArrayList<>(pending.size());
        for (Pending p : pending.values()) {
            deliveries.add(new Delivery(p.request(), p.due(), p.job().join()));
        }
        return new Queue(nextRequestId, deliveries);
    }

    /** Restores the queue: each result is delivered at its due tick as if its job had just finished. */
    public void restore(Queue queue) {
        nextRequestId = queue.nextRequestId();
        pending.clear();
        for (Delivery d : queue.deliveries()) {
            PathResult result = d.result();
            put(new Pending(d.request(), d.due(), new JobHandle<>() {
                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public PathResult join() {
                    return result;
                }
            }));
        }
    }
}
