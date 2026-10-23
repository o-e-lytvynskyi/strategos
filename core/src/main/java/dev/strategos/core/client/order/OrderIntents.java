package dev.strategos.core.client.order;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The command one right click makes (UI-05.3–4, ORD-04.1): on an enemy's body an Attack on it, else a Move of the
 * selection to the match-local point; with attack-move armed (its key, then the click) an AttackMove there instead.
 * Each joins the current orders with Shift; none without a selection.
 */
public final class OrderIntents {
    private OrderIntents() {}

    /**
     * The right click's command: {@code enemy} is the handle of the enemy unit clicked, 0 for the ground or a unit of
     * one's own.
     */
    public static @Nullable Command context(
            List<Integer> selection, int enemy, double localX, double localZ, boolean shift, boolean attackMove) {
        if (selection.isEmpty()) {
            return null;
        }
        UnitSet units =
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray());
        QueueMode mode = shift ? QueueMode.QUEUE : QueueMode.REPLACE;
        if (attackMove) {
            return new AttackMove(units, new Vec2d(localX, localZ), mode);
        }
        if (enemy != 0) {
            return new Attack(units, enemy, mode);
        }
        return new Move(units, new Vec2d(localX, localZ), mode, 0);
    }

    public static @Nullable Move move(List<Integer> selection, double localX, double localZ, boolean shift) {
        if (selection.isEmpty()) {
            return null;
        }
        int[] handles = selection.stream().mapToInt(Integer::intValue).toArray();
        return new Move(UnitSet.of(handles), new Vec2d(localX, localZ), shift ? QueueMode.QUEUE : QueueMode.REPLACE, 0);
    }
}
