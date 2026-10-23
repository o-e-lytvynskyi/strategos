package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.protocol.RejectReason;
import java.util.ArrayList;
import java.util.List;

/**
 * Economy notifications (UI-10): "not enough resources" from a refused command (naming the resources it lacked),
 * "population capped" from a queue's housing notice, and "deposit exhausted" from a worker left idle. Each fires once
 * per cause: while a cause is live another message of it renews it without firing again, and it lapses
 * {@value #SHOW_TICKS} client ticks after its last message; the cap's cause lasts until the faction's population is
 * below its cap again. The newest live notices are shown.
 */
public final class EconomyNotices {
    /** How long a notice stays after its last message: five seconds of client ticks. */
    public static final int SHOW_TICKS = 100;

    /** The most notices shown at once. */
    public static final int MOST = 3;

    /** What a notice is about. */
    public enum Cause {
        NOT_ENOUGH_RESOURCES,
        POPULATION_CAPPED,
        DEPOSIT_EXHAUSTED
    }

    /** A live notice: its cause, its text's detail, how many messages it took, and when it last had one. */
    public record Shown(Cause cause, String detail, int count, long last) {}

    private final List<Shown> live = new ArrayList<>();
    private final List<Shown> fired = new ArrayList<>();

    /**
     * Takes a feedback message at client tick {@code now}; {@code resources} names the match's resources, for what a
     * refusal lacked.
     */
    public void accept(FeedbackMsg msg, long now, List<String> resources) {
        switch (msg) {
            case Feedback f
            when f.reason() == RejectReason.NOT_ENOUGH_RESOURCES ->
                raise(Cause.NOT_ENOUGH_RESOURCES, lacking(f.missing(), resources), now);
            case NoticeMsg n when n.kind() == NoticeKind.HOUSING_NEEDED -> raise(Cause.POPULATION_CAPPED, "", now);
            case NoticeMsg n when n.kind() == NoticeKind.IDLE_WORKER -> raise(Cause.DEPOSIT_EXHAUSTED, "", now);
            default -> {}
        }
    }

    /** The faction's population now: below its cap, the cap's notice lapses. */
    public void population(int used, int cap) {
        if (used < cap) {
            live.removeIf(s -> s.cause() == Cause.POPULATION_CAPPED);
        }
    }

    /** The notices live at client tick {@code now}, the newest first, at most {@value #MOST}. */
    public List<Shown> shown(long now) {
        live.removeIf(s -> s.cause() != Cause.POPULATION_CAPPED && now - s.last() >= SHOW_TICKS);
        List<Shown> out = new ArrayList<>(live);
        out.sort((a, b) -> Long.compare(b.last(), a.last()));
        return List.copyOf(out.size() > MOST ? out.subList(0, MOST) : out);
    }

    /** How many fired notices are kept, the newest. */
    static final int KEPT = 64;

    /** The notices that fired, in order, the newest {@value #KEPT}: one per cause. */
    public List<Shown> fired() {
        return List.copyOf(fired);
    }

    private void raise(Cause cause, String detail, long now) {
        shown(now);
        for (int k = 0; k < live.size(); k++) {
            Shown s = live.get(k);
            if (s.cause() == cause && s.detail().equals(detail)) {
                live.set(k, new Shown(cause, detail, s.count() + 1, now));
                return;
            }
        }
        Shown s = new Shown(cause, detail, 1, now);
        live.add(s);
        fired.add(s);
        if (fired.size() > KEPT) {
            fired.remove(0);
        }
    }

    /** The resources a refusal lacked, by name, as "food, wood". */
    private static String lacking(List<Long> missing, List<String> resources) {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < missing.size(); r++) {
            if (missing.get(r) > 0) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(r < resources.size() ? resources.get(r) : "?");
            }
        }
        return out.toString();
    }
}
