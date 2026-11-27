package dev.strategos.core.client.order;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The command a command-card ability button, or the click after it, makes (UNIT-10): a {@link UseAbility} for the
 * selected units that have the ability. An ability that takes no target goes to the front of the units' orders, which
 * resume after it, so a charge does not end the march it speeds; one with a target replaces the orders, or with Shift
 * joins them.
 */
public final class AbilityIntents {
    private AbilityIntents() {}

    /** The units of {@code selection} whose template has {@code def}; {@code templateOf} gives a unit's template index, -1 for none. */
    public static int[] owners(
            TemplateSet templates, List<Integer> selection, IntUnaryOperator templateOf, AbilityDef def) {
        return selection.stream()
                .filter(h -> has(templates, templateOf.applyAsInt(h), def))
                .mapToInt(Integer::intValue)
                .toArray();
    }

    /** Whether template {@code template} has ability {@code def}. */
    public static boolean has(TemplateSet templates, int template, AbilityDef def) {
        for (AbilityDef own : templates.abilitiesOf(template)) {
            if (own.id().equals(def.id())) {
                return true;
            }
        }
        return false;
    }

    /** {@code def} used by {@code owners} on {@code target}, null for no owner. */
    public static @Nullable UseAbility use(int[] owners, AbilityDef def, Target target, boolean shift) {
        if (owners.length == 0) {
            return null;
        }
        QueueMode mode =
                def.aim() == AbilityDef.Aim.NONE ? QueueMode.PUSH_FRONT : shift ? QueueMode.QUEUE : QueueMode.REPLACE;
        return new UseAbility(UnitSet.of(owners), new AbilityId(def.id()), target, mode);
    }
}
