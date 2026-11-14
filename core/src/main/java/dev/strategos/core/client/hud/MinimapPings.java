package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.MatchNews;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The minimap's pings (UI-20): a ring at the position of each new alert and wave muster, for {@value #TICKS} ticks
 * (three seconds). It watches the client model's running counts, so a news item is pinged once.
 */
public final class MinimapPings {
    /** How long a ping shows, in client ticks. */
    public static final int TICKS = 60;

    /** One ping: match-local position and the tick it began. */
    public record Ping(double x, double z, long start) {
        /** How far the ring has grown, 0 at its start to 1 at its end. */
        public double progress(long now) {
            return Math.min(1, Math.max(0, (now - start) / (double) TICKS));
        }
    }

    private final List<Ping> pings = new ArrayList<>();
    private int alertsSeen;
    private int wavesSeen;

    /** Takes the news counts and latest items of the model at client time {@code now}; drops the expired pings. */
    public void observe(int alerts, @Nullable MatchNews alert, int waves, @Nullable MatchNews wave, long now) {
        if (alerts > alertsSeen) {
            alertsSeen = alerts;
            add(alert, now);
        }
        if (waves > wavesSeen) {
            wavesSeen = waves;
            add(wave, now);
        }
        pings.removeIf(p -> now - p.start() >= TICKS);
    }

    private void add(@Nullable MatchNews news, long now) {
        if (news != null) {
            pings.add(new Ping(Math.round(news.x()), Math.round(news.z()), now));
        }
    }

    /** The pings showing at {@code now}. */
    public List<Ping> active(long now) {
        List<Ping> out = new ArrayList<>(pings.size());
        for (Ping p : pings) {
            if (now - p.start() < TICKS) {
                out.add(p);
            }
        }
        return out;
    }

    /** A new match: no pings, no counts. */
    public void reset() {
        pings.clear();
        alertsSeen = 0;
        wavesSeen = 0;
    }
}
