package dev.strategos.core.client;

import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.shared.CoreApi;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The first match's hints (UI-14, spec §2.15): how to order after the first selection, the idle button after the
 * first idle worker, a house when the population is two below its cap. Each shows once per profile: dismissed, its id
 * joins the seen ones, which the client config keeps. One shows at a time, the first in this order whose condition
 * holds; once shown it stays until dismissed.
 */
@CoreApi
public final class HintBook {
    /** A hint, with its id in the config and its text's lang key. */
    @CoreApi
    public enum Hint {
        FIRST_ORDER("first_order"),
        IDLE_WORKER("idle_worker"),
        BUILD_HOUSE("build_house");

        private final String id;

        Hint(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String key() {
            return "strategos.hint." + id;
        }
    }

    private final Set<String> seen;
    private @Nullable Hint showing;

    public HintBook(List<String> seen) {
        this.seen = new TreeSet<>(seen);
    }

    /**
     * The hint to show now: the one showing, else the first unseen whose condition holds. {@code selected}: whether
     * the player has anything selected; {@code faction}: its last state, null before the first.
     */
    public @Nullable Hint current(boolean selected, @Nullable FactionState faction) {
        if (showing != null) {
            return showing;
        }
        for (Hint h : Hint.values()) {
            if (!seen.contains(h.id()) && holds(h, selected, faction)) {
                showing = h;
                return h;
            }
        }
        return null;
    }

    /** The shown hint dismissed: it joins the seen ones; whether one was showing. */
    public boolean dismiss() {
        Hint h = showing;
        if (h == null) {
            return false;
        }
        seen.add(h.id());
        showing = null;
        return true;
    }

    /** The ids seen, sorted, for the config. */
    public List<String> seen() {
        return List.copyOf(seen);
    }

    private static boolean holds(Hint h, boolean selected, @Nullable FactionState f) {
        return switch (h) {
            case FIRST_ORDER -> selected;
            case IDLE_WORKER -> f != null && f.idleWorkers() > 0;
            case BUILD_HOUSE -> f != null && f.populationCap() > 0 && f.populationCap() - f.populationUsed() <= 2;
        };
    }
}
