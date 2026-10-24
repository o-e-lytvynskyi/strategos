package dev.strategos.mod.client.debug;

import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.protocol.DebugSubscribe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the movement overlay shows (PATH-10): whether it is on, which layers it draws, and the feed's newest data. Pages
 * of surfaces and components stay until they lie more than a page beyond the subscribed radius, by then the server has
 * forgotten them too, so a page that comes back into the area is sent again; paths and unit markers are replaced by
 * each newer update, whose parts share its stamp. It holds no client class, so L2 tests it; the client's holder keeps
 * it, and only the client thread touches it.
 */
public final class ClientDebugState {
    private boolean enabled;
    private int drawn = DebugLayer.ALL;
    private final Map<Long, DebugFeedMsg.Cells> surfaces = new LinkedHashMap<>();
    private final Map<Long, DebugFeedMsg.Cells> components = new LinkedHashMap<>();
    private final Update<DebugFeedMsg.PathLine> paths = new Update<>();
    private final Update<DebugFeedMsg.Mark> marks = new Update<>();
    private DebugFeedMsg.@Nullable Panel panel;
    private DebugFeedMsg.@Nullable Panel previousPanel;

    /** The newest update of one layer: its stamp, its match's origin and the items of all its parts. */
    public static final class Update<T> {
        private long stamp = -1;
        private DebugFeedMsg.Origin origin = new DebugFeedMsg.Origin(0, 0, 0);
        private final List<T> items = new ArrayList<>();

        void accept(long stamp, DebugFeedMsg.Origin origin, List<T> part) {
            if (stamp < this.stamp) {
                return;
            }
            if (stamp > this.stamp) {
                this.stamp = stamp;
                items.clear();
            }
            this.origin = origin;
            items.addAll(part);
        }

        void clear() {
            stamp = -1;
            items.clear();
        }

        public DebugFeedMsg.Origin origin() {
            return origin;
        }

        public List<T> items() {
            return items;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** Turns the overlay on or off; off forgets the data. */
    public void enable(boolean on) {
        enabled = on;
        if (!on) {
            clearData();
        }
    }

    /** The radius the overlay subscribes with. */
    public int radius() {
        return DebugSubscribe.DEFAULT_RADIUS;
    }

    public boolean draws(DebugLayer layer) {
        return layer.in(drawn);
    }

    /** Draws {@code layer} or stops drawing it; returns whether it is drawn now. */
    public boolean toggle(DebugLayer layer) {
        drawn ^= layer.bit();
        return draws(layer);
    }

    /** One payload of the feed, from the client's thread. */
    public void accept(DebugFeedMsg msg) {
        switch (msg.payload()) {
            case DebugFeedMsg.Cells c -> (msg.layer() == DebugLayer.SURFACES ? surfaces : components).put(key(c), c);
            case DebugFeedMsg.Paths p -> paths.accept(msg.tick(), p.origin(), p.lines());
            case DebugFeedMsg.Marks m -> marks.accept(msg.tick(), m.origin(), m.marks());
            case DebugFeedMsg.Panel p -> {
                previousPanel = panel;
                panel = p;
            }
        }
    }

    /** Drops the pages more than {@code radius() + 16} blocks from world ({@code x}, {@code z}) on either axis. */
    public void prune(double x, double z) {
        double reach = radius() + 16;
        surfaces.values().removeIf(c -> far(c, x, z, reach));
        components.values().removeIf(c -> far(c, x, z, reach));
    }

    public Collection<DebugFeedMsg.Cells> surfaces() {
        return surfaces.values();
    }

    public Collection<DebugFeedMsg.Cells> components() {
        return components.values();
    }

    public Update<DebugFeedMsg.PathLine> paths() {
        return paths;
    }

    public Update<DebugFeedMsg.Mark> marks() {
        return marks;
    }

    public DebugFeedMsg.@Nullable Panel panel() {
        return panel;
    }

    /** The overlay off and its data gone: the player left the server, which forgot the subscription. */
    public void reset() {
        enabled = false;
        drawn = DebugLayer.ALL;
        clearData();
    }

    /** The panel's text, a line each; none before the first panel. */
    public List<String> panelLines() {
        DebugFeedMsg.Panel p = panel;
        List<String> lines = new ArrayList<>();
        if (p == null) {
            return lines;
        }
        lines.add("Strategos m" + p.matchId() + " t" + p.matchTick() + ": " + p.units() + " units");
        lines.add("paths: queue " + p.queue() + ", latency p95 " + p.latencyP95() + " ticks, compute "
                + computePerTick() + " ms/tick");
        lines.add("asked " + p.requests() + ": " + p.decided() + " at once, " + p.ok() + " ok, " + p.partial()
                + " partial, " + p.noPath() + " none, " + p.superseded() + " superseded");
        lines.add("expansions " + p.expansions() + ", at most " + p.maxExpansions() + " in one search");
        StringBuilder timings = new StringBuilder("p95 ms:");
        for (DebugFeedMsg.Timing t : p.timings()) {
            timings.append(' ').append(t.name()).append(' ').append(millis(t.p95()));
        }
        lines.add(timings.toString());
        int[] tiers = new int[4];
        for (DebugFeedMsg.Mark m : marks.items()) {
            tiers[Math.min(m.tier(), 3)]++;
        }
        lines.add("tiers in view: " + tiers[3] + " active, " + tiers[1] + " moving, " + tiers[2] + " working, "
                + tiers[0] + " idle"); // UNIT-05
        return lines;
    }

    /** The path jobs' main-thread time per tick between the last two panels of one match, or "?" before. */
    private String computePerTick() {
        DebugFeedMsg.Panel p = panel;
        DebugFeedMsg.Panel before = previousPanel;
        if (p == null || before == null || before.matchId() != p.matchId() || p.matchTick() <= before.matchTick()) {
            return "?";
        }
        return millis((p.joinNanos() - before.joinNanos()) / (p.matchTick() - before.matchTick()));
    }

    private void clearData() {
        surfaces.clear();
        components.clear();
        paths.clear();
        marks.clear();
        panel = null;
        previousPanel = null;
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1e6);
    }

    /** A page's world X and Z, packed: pages of two matches never share them. */
    private static long key(DebugFeedMsg.Cells c) {
        long x = c.origin().x() + c.pageX() * 16L;
        long z = c.origin().z() + c.pageZ() * 16L;
        return (x << 32) | (z & 0xFFFF_FFFFL);
    }

    private static boolean far(DebugFeedMsg.Cells c, double x, double z, double reach) {
        double x0 = c.origin().x() + c.pageX() * 16.0;
        double z0 = c.origin().z() + c.pageZ() * 16.0;
        double dx = Math.max(x0 - x, x - (x0 + 16));
        double dz = Math.max(z0 - z, z - (z0 + 16));
        return Math.max(dx, dz) > reach;
    }
}
