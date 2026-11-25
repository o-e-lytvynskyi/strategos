package dev.strategos.core.client.hud;

import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.List;

/** The lists a building's command card reads as the viewer's faction sees them (TECH-05): effective, not base. */
@CoreApi
public final class CardLists {
    private CardLists() {}

    /** What {@code view}'s faction's {@code building} trains: its effective {@code Trainer/units} (TECH-05). */
    public static List<Integer> trains(TemplateSet templates, ClientFactionView view, int building) {
        return unitsNamed(templates, view.tokens(building, "Trainer/units"));
    }

    static List<Integer> unitsNamed(TemplateSet templates, List<String> ids) {
        List<Integer> out = new ArrayList<>();
        for (String id : ids) {
            for (UnitTemplate t : templates.all()) {
                if (t.id().value().equals(id)) {
                    out.add(t.netIndex());
                }
            }
        }
        return out;
    }

    /**
     * What {@code view}'s faction's {@code building} researches (TECH-05): its effective {@code Researcher/techs}, the
     * base list with what its civilisation's bonuses and researched techs add, as the server's research rule reads it.
     */
    public static List<TechDef> researches(TemplateSet templates, ClientFactionView view, int building) {
        return techsNamed(templates, view.tokens(building, "Researcher/techs"));
    }

    static List<TechDef> techsNamed(TemplateSet templates, List<String> ids) {
        List<TechDef> out = new ArrayList<>();
        for (String id : ids) {
            TechDef tech;
            try {
                tech = templates.tech(new TechId(id));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (tech != null) {
                out.add(tech);
            }
        }
        return out;
    }
}
