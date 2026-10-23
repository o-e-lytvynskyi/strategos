package dev.strategos.core.client.order;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The command one right click makes (UI-05.3–4): a Move of the selection to the match-local point, queued behind the
 * current orders with Shift; none without a selection. Attack on an enemy comes with ORD-04.
 */
public final class OrderIntents {
    private OrderIntents() {}

    public static @Nullable Move move(List<Integer> selection, double localX, double localZ, boolean shift) {
        if (selection.isEmpty()) {
            return null;
        }
        int[] handles = selection.stream().mapToInt(Integer::intValue).toArray();
        return new Move(UnitSet.of(handles), new Vec2d(localX, localZ), shift ? QueueMode.QUEUE : QueueMode.REPLACE, 0);
    }
}
