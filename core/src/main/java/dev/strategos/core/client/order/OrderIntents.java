package dev.strategos.core.client.order;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.Capture;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Guard;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Patrol;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Vec2d;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The command one right click makes (UI-05.3–4, ORD-04.1, ECO-05, CMB-10): on an enemy's body an Attack on it, on a block
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
     * The right click on a unit or building with a modifier (ORD-05): with {@code spread} (Alt) the selection's attack
     * is shared over the enemies round the target; with {@code force} (Ctrl) it may be on one's own unit or building.
     * Spread wins over force; none without a selection.
     */
    public static @Nullable Command attack(
            List<Integer> selection, int target, boolean shift, boolean force, boolean spread) {
        if (selection.isEmpty()) {
            return null;
        }
        UnitSet units =
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray());
        QueueMode mode = shift ? QueueMode.QUEUE : QueueMode.REPLACE;
        return spread ? new AttackSpread(units, target, mode) : new Attack(units, target, mode, force);
    }

    /**
     * The right click on an enemy's building with the capture key held (CMB-10): the selection captures it rather than
     * attacking it; none without a selection.
     */
    public static @Nullable Capture capture(List<Integer> selection, int building) {
        if (selection.isEmpty()) {
            return null;
        }
        return new Capture(
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray()), building);
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

    /**
     * The click that ends a formation move's targeting (ORD-06): the selection marches to the match-local point in one
     * formation of {@code shape}; it only replaces the orders, as the server takes nothing else. None without a
     * selection.
     */
    public static @Nullable FormationMove formation(
            List<Integer> selection, double localX, double localZ, FormationShape shape) {
        if (selection.isEmpty()) {
            return null;
        }
        return FormationMove.replace(
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray()), new Vec2d(localX, localZ), shape);
    }

    /**
     * The click that ends a patrol's targeting (ORD-07): the selection patrols between the match-local {@code points},
     * replacing its orders; one point is a patrol between each unit's own place and it. None without a selection or a
     * point.
     */
    public static @Nullable Patrol patrol(List<Integer> selection, List<Vec2d> points) {
        if (selection.isEmpty() || points.isEmpty()) {
            return null;
        }
        return Patrol.replace(
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray()), points);
    }

    /** The click that ends a guard's targeting (ORD-07): the selection guards {@code charge}; none without a selection. */
    public static @Nullable Guard guard(List<Integer> selection, int charge) {
        if (selection.isEmpty() || charge == 0) {
            return null;
        }
        return Guard.replace(
                UnitSet.of(selection.stream().mapToInt(Integer::intValue).toArray()), charge);
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
