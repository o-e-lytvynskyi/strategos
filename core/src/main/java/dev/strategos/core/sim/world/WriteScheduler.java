package dev.strategos.core.sim.world;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * The order of block writes (04 §11.7, BLD-15), shared by the mod's {@code BlockWriter} and the test kit's {@code
 * FakeWorld}:
 *
 * <ul>
 *   <li>a budget of writes per tick, which the caller passes ({@link #BUDGET} for matches);
 *   <li>sites served round-robin in {@link WriteSite} order, starting after the last site served;
 *   <li>a site writes everything it has, up to the budget left, in one burst, at most every {@link #BURST_INTERVAL}
 *       ticks, so a render section is re-meshed a few times a second rather than every tick;
 *   <li>inside a site, {@link WritePriority} order, first in first out within each priority.
 * </ul>
 *
 * Deterministic: the same enqueues and ticks give the same writes. Not thread-safe.
 *
 * @param <W> what a write carries
 */
@CoreApi
public final class WriteScheduler<W> {
    public static final int BUDGET = 64;
    public static final int BURST_INTERVAL = 4;

    private final TreeMap<WriteSite, Site<W>> sites = new TreeMap<>();
    private @Nullable WriteSite lastServed;
    private int pending;

    /** One site's queues, one per priority, and when it last wrote. */
    private static final class Site<W> {
        final List<ArrayDeque<W>> queues = new ArrayList<>(WritePriority.COUNT);
        boolean flushed;
        long lastFlush;
        int size;

        Site() {
            for (int i = 0; i < WritePriority.COUNT; i++) {
                queues.add(new ArrayDeque<>());
            }
        }

        boolean coolingAt(long tick) {
            return flushed && tick - lastFlush < BURST_INTERVAL;
        }

        W poll() {
            for (ArrayDeque<W> queue : queues) {
                W write = queue.poll();
                if (write != null) {
                    size--;
                    return write;
                }
            }
            throw new IllegalStateException("the site has nothing left");
        }
    }

    public void enqueue(WriteSite site, WritePriority priority, W write) {
        Site<W> s = sites.computeIfAbsent(site, k -> new Site<>());
        s.queues.get(priority.rank()).add(write);
        s.size++;
        pending++;
    }

    /** Drops every write a match's sites still wait to make: the match is gone, and so is its picture. */
    public void drop(int match) {
        Iterator<Map.Entry<WriteSite, Site<W>>> it = sites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<WriteSite, Site<W>> entry = it.next();
            if (entry.getKey().match() == match) {
                pending -= entry.getValue().size;
                it.remove();
            }
        }
    }

    /** Writes waiting, over all sites. */
    public int pending() {
        return pending;
    }

    /** The writes waiting at one site. */
    public int pending(WriteSite site) {
        Site<W> s = sites.get(site);
        return s == null ? 0 : s.size;
    }

    /**
     * The writes of {@code tick}, at most {@code budget}, handed to {@code out} in order. Returns the sites that wrote
     * their last waiting write in this drain, in the order they were served.
     */
    public List<WriteSite> drain(long tick, int budget, BiConsumer<WriteSite, W> out) {
        dropIdle(tick);
        List<WriteSite> emptied = new ArrayList<>();
        if (budget <= 0 || pending == 0) {
            return emptied;
        }
        List<Map.Entry<WriteSite, Site<W>>> order = new ArrayList<>();
        WriteSite last = lastServed;
        if (last != null) {
            order.addAll(sites.tailMap(last, false).entrySet());
            order.addAll(sites.headMap(last, true).entrySet());
        } else {
            order.addAll(sites.entrySet());
        }
        int left = budget;
        for (Map.Entry<WriteSite, Site<W>> entry : order) {
            Site<W> site = entry.getValue();
            if (left == 0) {
                break;
            }
            if (site.size == 0 || site.coolingAt(tick)) {
                continue;
            }
            int burst = Math.min(left, site.size);
            for (int i = 0; i < burst; i++) {
                out.accept(entry.getKey(), site.poll());
            }
            left -= burst;
            pending -= burst;
            site.flushed = true;
            site.lastFlush = tick;
            lastServed = entry.getKey();
            if (site.size == 0) {
                emptied.add(entry.getKey());
            }
        }
        return emptied;
    }

    /** Forgets empty sites once their cool-down is over, so a site that empties and refills still waits its turn. */
    private void dropIdle(long tick) {
        Iterator<Site<W>> it = sites.values().iterator();
        while (it.hasNext()) {
            Site<W> site = it.next();
            if (site.size == 0 && !site.coolingAt(tick)) {
                it.remove();
            }
        }
    }
}
