package dev.strategos.core.client.hud;

import dev.strategos.core.content.CivDef;
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
    private final boolean phaseByRule;

    /**
     * {@code civ} is the faction's civilisation, null when the set lacks it: a civilisation whose phase rule is not the
     * default tells the phase through the replicated {@code faction.phase()}, as the server's rule does.
     */
    public ClientFactionView(
            TemplateSet templates,
            @Nullable FactionState faction,
            List<BuildingState.Entry> buildings,
            @Nullable CivDef civ) {
        this.templates = templates;
        this.faction = faction;
        this.buildings = List.copyOf(buildings);
        CivDef.Rule rule = civ == null ? null : civ.rules().get("phase");
        this.phaseByRule = rule != null && !"default".equals(rule.id());
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
            TechId id = new TechId("strategos:phase_" + phase);
            if (!phaseByRule) {
                return researched(id); // the default rule: today's answer
            }
            TechDef def =
                    templates.tech(id); // as PhaseRule's variant default: the phase reaches the named one's ordinal
            return def != null && def.isPhase() && faction != null && faction.phase() >= def.ordinal();
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
