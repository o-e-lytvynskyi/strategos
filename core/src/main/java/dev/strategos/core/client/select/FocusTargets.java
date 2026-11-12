package dev.strategos.core.client.select;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Which target each of the player's units was last told to attack (ORD-05's marker): the client keeps what it sent,
 * since the server sends no orders back. A selection whose units all hold the same target shows a ring on it; a spread
 * attack, or any order that is not an attack, holds none, and a queued attack is only kept for a unit with none.
 */
public final class FocusTargets {
    private final Map<Integer, Integer> targets = new HashMap<>();

    /** {@code units} were ordered to attack {@code target}; with {@code queued} only those with no target take it. */
    public void focus(int[] units, int target, boolean queued) {
        for (int unit : units) {
            if (queued) {
                targets.putIfAbsent(unit, target);
            } else {
                targets.put(unit, target);
            }
        }
    }

    /** {@code units} were given an order that is not a focused attack. */
    public void release(int[] units) {
        for (int unit : units) {
            targets.remove(unit);
        }
    }

    /** The target every unit of {@code selection} holds; null for an empty selection, or units that differ or hold none. */
    public @Nullable Integer shared(Collection<Integer> selection) {
        Integer shared = null;
        for (int unit : selection) {
            Integer target = targets.get(unit);
            if (target == null || (shared != null && !shared.equals(target))) {
                return null;
            }
            shared = target;
        }
        return shared;
    }
}
