package dev.strategos.core.client.hud;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import java.util.List;

/**
 * The command card's standing-order buttons (ORD-07): patrol and guard, for a selection with a unit that walks; a click
 * arms the targeting, whose clicks send the order.
 */
final class StandingCard {
    /** The cells the buttons take: the first two of the row above stop, where abilities past the fourth would go. */
    static final int PATROL_SLOT = 10;

    static final int GUARD_SLOT = 11;

    /** The abilities that still show beside the buttons ({@link CommandCard#abilitySlot}'s cells below theirs). */
    static final int ABILITIES_BESIDE = 4;

    private StandingCard() {}

    /** Whether the card offers patrol and guard for units of {@code unitTemplates}, one entry for each selected unit. */
    static boolean offered(TemplateSet templates, List<Integer> unitTemplates) {
        ValuePath p = templates.valuePaths().byPath(CorePaths.WALK_SPEED);
        if (p == null) {
            return false;
        }
        for (int t : unitTemplates) {
            if (templates.has(t) && templates.value(t, p.id()) > 0) {
                return true;
            }
        }
        return false;
    }

    static void buttons(List<CommandCard.Button> out) {
        out.add(new CommandCard.Button(CommandCard.Kind.PATROL, PATROL_SLOT, -1, List.of(), true));
        out.add(new CommandCard.Button(CommandCard.Kind.GUARD, GUARD_SLOT, -1, List.of(), true));
    }
}
