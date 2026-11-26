package dev.strategos.core.client.select;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Delete's second press (UI-17): the first press on a selection asks, and only a second press on the same selection
 * within {@link #WINDOW} ticks confirms, so a stray key never wipes an army. Any other selection, or a press after the
 * window, asks afresh.
 */
public final class DeleteConfirm {
    /** Ticks the question stays open: three seconds. */
    public static final long WINDOW = 60;

    private @Nullable String asked;
    private long askedAt;

    /**
     * A press at tick {@code now} on the selection of units {@code handles} and building {@code building} (0 for none):
     * true when it confirms and the delete should go; false when it only asks (see {@link #open}).
     */
    public boolean press(List<Integer> handles, int building, long now) {
        String key = handles + "/" + building;
        if (key.equals(asked) && now - askedAt <= WINDOW) {
            asked = null;
            return true;
        }
        asked = key;
        askedAt = now;
        return false;
    }

    /** Whether a question is open at tick {@code now}. */
    public boolean open(long now) {
        return asked != null && now - askedAt <= WINDOW;
    }
}
