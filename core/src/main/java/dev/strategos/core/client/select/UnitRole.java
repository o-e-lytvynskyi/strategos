package dev.strategos.core.client.select;

import dev.strategos.core.content.TemplateSet;
import java.util.ArrayList;
import java.util.List;

/**
 * The two kinds of unit the hotkeys pick out (UI-17): a worker gathers; a soldier fights and gathers nothing (infantry,
 * cavalry, siege, ranged or melee, whatever else it is). A horseman who hunts is a soldier; a worker who fights is
 * still a worker.
 */
public enum UnitRole {
    WORKER,
    SOLDIER;

    /** Whether a unit of template {@code template} has this role; false for a template the set lacks. */
    public boolean has(TemplateSet templates, int template) {
        if (!templates.has(template)) {
            return false;
        }
        List<String> classes = templates.byIndex(template).classes();
        if (this == WORKER) {
            return classes.contains("Gatherer");
        }
        return !classes.contains("Gatherer")
                && (classes.contains("Infantry")
                        || classes.contains("Cavalry")
                        || classes.contains("Siege")
                        || classes.contains("Melee")
                        || classes.contains("Ranged"));
    }

    /** The handles of faction {@code faction}'s units in {@code seen} that have this role, in the list's order. */
    public List<Integer> select(List<Seen> seen, int faction, TemplateSet templates) {
        List<Integer> out = new ArrayList<>();
        for (Seen s : seen) {
            if (s.faction() == faction && has(templates, s.template())) {
                out.add(s.handle());
            }
        }
        return out;
    }
}
