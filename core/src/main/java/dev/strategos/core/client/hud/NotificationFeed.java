package dev.strategos.core.client.hud;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Vec2d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The notification feed (UI-21, spec §2.12): what happened, in priority order. Alerts (under attack, a wave) come
 * before a phase, a phase before research, research before training and building complete, those before the economy's
 * notices. Each kind has its rate limit: under attack once per {@value #UNDER_ATTACK_EVERY} ticks for each 32-block
 * area, each economy notice once per {@value #ECONOMY_EVERY} ticks; the rest once per their key. At most {@value
 * #VISIBLE} entries show, each fading {@value #FADE_TICKS} ticks after it came. Client ticks throughout.
 */
@CoreApi
public final class NotificationFeed {
    public static final int VISIBLE = 6;
    public static final int FADE_TICKS = 160;
    public static final int UNDER_ATTACK_EVERY = 200;
    public static final int ECONOMY_EVERY = 300;
    public static final int AREA = 32;

    /** What an entry is about, with its priority: lower comes first. */
    @CoreApi
    public enum Kind {
        UNDER_ATTACK(0, true),
        WAVE(0, true),
        PHASE(1, false),
        RESEARCH(2, false),
        TRAINED(3, false),
        BUILT(3, false),
        ECONOMY(4, false);

        private final int priority;
        private final boolean alert;

        Kind(int priority, boolean alert) {
            this.priority = priority;
            this.alert = alert;
        }

        public int priority() {
            return priority;
        }

        /** An alert: J jumps to the newest one with a place. */
        public boolean alert() {
            return alert;
        }
    }

    /** An entry: its kind, its text (a lang key and its arguments), the tick it came and the place J jumps to. */
    public record Entry(
            Kind kind,
            String key,
            List<String> args,
            long tick,
            @Nullable Vec2d at) {
        public Entry {
            args = List.copyOf(args);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Long> lastByKey = new TreeMap<>();

    /**
     * Offers an entry at tick {@code now}: under its rate limit it is dropped and false comes back; otherwise it joins
     * the feed and true comes back, for its sound. {@code at} is the world column of an alert, null for none;
     * {@code limitKey} names what the limit counts by besides the kind (a resource, a template), for an alert its
     * area is used instead.
     */
    public boolean offer(Kind kind, String key, List<String> args, long now, @Nullable Vec2d at, String limitKey) {
        String counted = kind + "|" + (kind == Kind.UNDER_ATTACK && at != null ? area(at) : limitKey);
        Long last = lastByKey.get(counted);
        if (last != null && now - last < every(kind)) {
            return false;
        }
        lastByKey.put(counted, now);
        entries.add(new Entry(kind, key, args, now, at));
        entries.removeIf(e -> now - e.tick() >= FADE_TICKS);
        return true;
    }

    /** The entries shown at tick {@code now}: not faded, by priority, the newest first within one, at most six. */
    public List<Entry> visible(long now) {
        List<Entry> live = new ArrayList<>();
        for (Entry e : entries) {
            if (now - e.tick() < FADE_TICKS) {
                live.add(e);
            }
        }
        live.sort(Comparator.comparingInt((Entry e) -> e.kind().priority())
                .thenComparing(Comparator.comparingLong(Entry::tick).reversed()));
        return List.copyOf(live.size() > VISIBLE ? live.subList(0, VISIBLE) : live);
    }

    /** How opaque an entry is at {@code now}: 1 until its last two seconds, then down to 0. */
    public static float alpha(Entry e, long now) {
        long left = FADE_TICKS - (now - e.tick());
        return left >= 40 ? 1f : Math.max(0f, left / 40f);
    }

    /** The place of the newest alert that has one, faded or not; null when none came. */
    public @Nullable Vec2d newestAlert() {
        for (int k = entries.size() - 1; k >= 0; k--) {
            Entry e = entries.get(k);
            if (e.kind().alert() && e.at() != null) {
                return e.at();
            }
        }
        return null;
    }

    private static long every(Kind kind) {
        return switch (kind) {
            case UNDER_ATTACK -> UNDER_ATTACK_EVERY;
            case ECONOMY -> ECONOMY_EVERY;
            default -> 1;
        };
    }

    private static String area(Vec2d at) {
        return Math.floorDiv((int) Math.floor(at.x()), AREA) + "," + Math.floorDiv((int) Math.floor(at.z()), AREA);
    }
}
