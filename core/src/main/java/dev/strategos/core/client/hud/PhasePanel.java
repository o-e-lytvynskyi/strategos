package dev.strategos.core.client.hud;

import dev.strategos.core.content.FactionView;
import dev.strategos.core.content.Requirement;
import dev.strategos.core.content.Requirements;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The phase panel above the command card (UI-13, spec §2.13): with an own finished building selected that researches
 * a phase the faction has not reached, one row per leaf of {@code Requirements.checklist} of that phase's requirement,
 * with its amounts: the server's own evaluation, fed by the client's view of the faction.
 */
@CoreApi
public record PhasePanel(TechDef phase, List<Row> rows) {
    /** One leaf: its label, whether it holds, and its amounts. */
    public record Row(String label, boolean met, int have, int need) {}

    public PhasePanel {
        rows = List.copyOf(rows);
    }

    /** The panel for {@code selected}, or null when it researches no phase still ahead. */
    public static @Nullable PhasePanel of(
            TemplateSet templates, FactionView view, BuildingState.@Nullable Entry selected) {
        if (selected == null || !selected.own() || selected.state() != CommandCard.COMPLETE) {
            return null;
        }
        for (TechDef tech : CommandCard.researches(templates, selected.template())) {
            if (tech.isPhase() && !view.researched(tech.id())) {
                List<Row> rows = new ArrayList<>();
                for (Requirements.Leaf leaf : Requirements.checklist(tech.requires(), view)) {
                    rows.add(new Row(label(leaf), leaf.met(), leaf.have(), leaf.need()));
                }
                return new PhasePanel(tech, rows);
            }
        }
        return null;
    }

    /** A leaf as the panel and a disabled button name it. */
    static String label(Requirements.Leaf leaf) {
        return switch (leaf.leaf()) {
            case Requirement.Tech t ->
                "research " + t.tech().value().substring(t.tech().value().indexOf(':') + 1);
            case Requirement.Phase p -> p.name() + " phase";
            case Requirement.Buildings b -> b.count() + " " + b.filter() + " (" + leaf.have() + ")";
            default -> leaf.leaf().toString();
        };
    }
}
