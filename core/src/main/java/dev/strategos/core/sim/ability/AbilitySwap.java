package dev.strategos.core.sim.ability;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An ability's index is its place in its set's id-sorted list, and live state holds it: an ability order keeps it in its
 * queue slot, a cooldown or a timed entry keeps the id and the source id the index gave. So a content swap may not move
 * the index of an ability the match is using ({@link #problem}), and a restore under another set maps what a save
 * holds through the save's id table ({@link #remap}); an ability the set lacks refuses the restore of an order that
 * uses it, as a missing template does.
 */
public final class AbilitySwap {
    private AbilitySwap() {}

    /** Why {@code next} cannot replace the match's set, because an ability in use would move or go, or null. */
    public static @Nullable String problem(MatchState state, TemplateSet next) {
        TemplateSet old = state.templates();
        List<String> used = new ArrayList<>(state.play().abilities().idsInUse(state.entities()));
        OrderQueues orders = state.orders();
        for (int i = 0; i < state.entities().highWater(); i++) {
            if (!state.entities().aliveAt(i)) {
                continue;
            }
            for (int k = 0; k < orders.size(i); k++) {
                OrderQueues.Order o = orders.order(i, k);
                if (o.kind() == OrderKind.ABILITY
                        && o.slot() >= 0
                        && o.slot() < old.abilities().size()) {
                    used.add(old.abilities().get(o.slot()).id());
                }
            }
        }
        for (String id : used) {
            if (next.abilityIndex(id) != old.abilityIndex(id)) {
                return "ability " + id + " is in use and must keep its index";
            }
        }
        return null;
    }

    /**
     * {@code saved} (unit {@code unit}'s queue) with each ability order's slot moved from its place in the saving set's
     * {@code table} to its place in {@code set}; the same object when it holds no ability order.
     */
    public static OrderQueues.Saved remap(OrderQueues.Saved saved, List<String> table, TemplateSet set, int unit) {
        boolean any = saved.kind() == OrderKind.ABILITY;
        for (OrderQueues.Order o : saved.next()) {
            any |= o.kind() == OrderKind.ABILITY;
        }
        if (!any) {
            return saved;
        }
        int slot = saved.kind() == OrderKind.ABILITY ? map(saved.slot(), table, set, unit) : saved.slot();
        List<OrderQueues.Order> next = new ArrayList<>();
        for (OrderQueues.Order o : saved.next()) {
            next.add(
                    o.kind() == OrderKind.ABILITY
                            ? new OrderQueues.Order(
                                    o.kind(), o.x(), o.z(), o.group(), map(o.slot(), table, set, unit), o.target())
                            : o);
        }
        return new OrderQueues.Saved(saved.kind(), saved.group(), slot, saved.target(), next);
    }

    private static int map(int slot, List<String> table, TemplateSet set, int unit) {
        if (slot < 0 || slot >= table.size()) {
            throw new IllegalArgumentException("unit " + Handle.toString(unit) + " has an ability order for index "
                    + slot + ", which the save's ability table lacks");
        }
        int index = set.abilityIndex(table.get(slot));
        if (index < 0) {
            throw new IllegalArgumentException("unit " + Handle.toString(unit) + " has an order using ability "
                    + table.get(slot) + ", which the template set lacks");
        }
        return index;
    }
}
