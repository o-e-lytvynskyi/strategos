package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.Collection;
import java.util.Optional;

/**
 * Orders given on the minimap (UI-20): a right click on the image is one {@link Move} of the selection to the
 * column it shows, queued behind the units' orders when Shift is held. The mapping is {@link Minimap}'s.
 */
public final class MinimapOrders {
    private MinimapOrders() {}

    /**
     * The command a right click at GUI point ({@code px}, {@code py}) sends: none when it misses the image or nothing
     * is selected, otherwise exactly one move to the mapped match-local point.
     */
    public static Optional<Move> rightClick(
            Minimap minimap, double px, double py, Collection<Integer> selection, boolean queue) {
        if (selection.isEmpty() || !minimap.contains(px, py)) {
            return Optional.empty();
        }
        double[] at = minimap.toWorld(px, py);
        int[] units = selection.stream().mapToInt(Integer::intValue).toArray();
        return Optional.of(new Move(UnitSet.of(units), new Vec2d(at[0], at[1]), mode(queue), 0));
    }

    private static QueueMode mode(boolean queue) {
        return queue ? QueueMode.QUEUE : QueueMode.REPLACE;
    }
}
