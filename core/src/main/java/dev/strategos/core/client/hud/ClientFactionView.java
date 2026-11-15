package dev.strategos.core.client.hud;

import dev.strategos.core.content.ClassFilter;
import dev.strategos.core.content.FactionView;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The own faction as the client knows it, read the way the server's {@code MatchFactionView} reads a match (UI-13): the
 * researched techs and the phase from {@code faction_state}, the finished buildings from {@code building_state}. So
 * the phase panel's checklist is {@code Requirements.checklist} itself, the server's code.
 */
@CoreApi
public final class ClientFactionView implements FactionView {
    private final TemplateSet templates;
    private final @Nullable FactionState faction;
    private final List<BuildingState.Entry> buildings;

    public ClientFactionView(
            TemplateSet templates, @Nullable FactionState faction, List<BuildingState.Entry> buildings) {
        this.templates = templates;
        this.faction = faction;
        this.buildings = List.copyOf(buildings);
    }

    @Override
    public boolean researched(TechId tech) {
        TechDef def = templates.tech(tech);
        return def != null && faction != null && faction.researched().contains(def.netIndex());
    }

    @Override
    public boolean reached(String phase) {
        if ("village".equals(phase)) {
            return true;
        }
        try {
            return researched(new TechId("strategos:phase_" + phase));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public int completeBuildings(String classFilter) {
        ClassFilter filter = ClassFilter.compile(classFilter, templates.tokens());
        int n = 0;
        for (BuildingState.Entry b : buildings) {
            if (b.own()
                    && b.state() == CommandCard.COMPLETE
                    && templates.has(b.template())
                    && filter.matches(templates
                            .tokens()
                            .bits(templates.byIndex(b.template()).classes()))) {
                n++;
            }
        }
        return n;
    }
}
