package dev.strategos.core.client.hud;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.protocol.FormationShape;
import java.util.List;

/**
 * The command card's formation buttons (ORD-06): a box and a line, for a selection of two or more units that walk; a
 * click arms the next ground click, which marches the selection in that shape.
 */
final class FormationCard {
    /** The cells the buttons take, beside stop's column: above the stances, where abilities past the sixth would go. */
    static final int BOX_SLOT = 12;

    static final int LINE_SLOT = 13;

    private FormationCard() {}

    /** Whether the card offers formations for units of {@code unitTemplates}, one entry for each selected unit. */
    static boolean offered(TemplateSet templates, List<Integer> unitTemplates) {
        int walking = 0;
        for (int t : unitTemplates) {
            walking += walks(templates, t) ? 1 : 0;
        }
        return walking >= 2;
    }

    static void buttons(List<CommandCard.Button> out) {
        out.add(button(BOX_SLOT, FormationShape.BOX));
        out.add(button(LINE_SLOT, FormationShape.LINE));
    }

    private static CommandCard.Button button(int slot, FormationShape shape) {
        return new CommandCard.Button(CommandCard.Kind.FORMATION, slot, shape.code(), List.of(), true);
    }

    private static boolean walks(TemplateSet templates, int template) {
        ValuePath p = templates.valuePaths().byPath(CorePaths.WALK_SPEED);
        return p != null && templates.has(template) && templates.value(template, p.id()) > 0;
    }
}
