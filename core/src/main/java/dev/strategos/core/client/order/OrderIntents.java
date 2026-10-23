package dev.strategos.core.client.order;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The command one right click makes (UI-05.3–4, ORD-04.1, ECO-05): on an enemy's body an Attack on it, on a block
 * that yields a resource a Gather there, else a Move of the selection to the match-local point; with attack-move armed
 * (its key, then the click) an AttackMove there instead. Each joins the current orders with Shift; none without a
 * selection.
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

    /**
     * The right click on a block that yields a resource (ECO-05): the selection gathers at the match-local block (x, y,
     * z), which the server resolves to its node; units that do not gather it are refused there.
     */
    public static @Nullable Gather gather(List<Integer> selection, int x, int y, int z, boolean shift) {
        if (selection.isEmpty()) {
            return null;
        }
        return new Gather(
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray()),
                x,
                y,
                z,
                shift ? QueueMode.QUEUE : QueueMode.REPLACE);
    }

    public static @Nullable Move move(List<Integer> selection, double localX, double localZ, boolean shift) {
        if (selection.isEmpty()) {
            return null;
        }
        int[] handles = selection.stream().mapToInt(Integer::intValue).toArray();
        return new Move(UnitSet.of(handles), new Vec2d(localX, localZ), shift ? QueueMode.QUEUE : QueueMode.REPLACE, 0);
    }

    /** First person's follow-me (CAM-07): the selection follows the player; none without a selection. */
    public static @Nullable Follow follow(List<Integer> selection) {
        if (selection.isEmpty()) {
            return null;
        }
        return Follow.replace(
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray()));
    }
}
