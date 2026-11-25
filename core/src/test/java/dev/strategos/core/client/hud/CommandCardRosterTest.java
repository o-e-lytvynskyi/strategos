package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** CIV-01: the command card and the phase names follow the viewer's civilisation; the default's roster hides nothing. */
class CommandCardRosterTest {
    private static final TemplateSet T = CommandCardTest.T;
    private static final int WORKER = T.byId(TemplateSet.WORKER).netIndex();
    private static final int FARMSTEAD = CommandCardTest.index("strategos:building/farmstead");
    private static final int CENTRE = CommandCardTest.index("strategos:building/civic_centre");
    private static final int STOREHOUSE = CommandCardResearchTest.STOREHOUSE;
    private static final int WOOD_1 = CommandCardResearchTest.WOOD_1;

    /** A civilisation whose roster lacks the worker, the farmstead and the first wood tech. */
    private static final CivDef LEAN = lean("[\"phase.a\", \"phase.b\"]");

    private static CivDef lean(String phases) {
        JsonObject roster = new JsonObject();
        JsonArray templates = new JsonArray();
        for (UnitTemplate t : T.all()) {
            if (t.netIndex() != WORKER && t.netIndex() != FARMSTEAD) {
                templates.add(t.id().value());
            }
        }
        JsonArray techs = new JsonArray();
        for (TechDef t : T.techs()) {
            if (t.netIndex() != WOOD_1) {
                techs.add(t.id().value());
            }
        }
        roster.add("templates", templates);
        roster.add("techs", techs);
        JsonObject json = JsonParser.parseString("{\"phases\":" + phases + "}").getAsJsonObject();
        json.add("roster", roster);
        List<TemplateProblem> problems = new ArrayList<>();
        CivDef def = CivDef.fromJson(new CivId("t:lean"), json, "lean.json", problems);
        assertThat(problems).isEmpty();
        return Objects.requireNonNull(def);
    }

    private static List<CommandCard.Button> buttons(
            BuildingState.@Nullable Entry selected, List<Integer> units, boolean buildPage, CivDef civ) {
        FactionState faction = CommandCardResearchTest.faction(1_000, 1_000, List.of());
        ClientMatchModel model =
                selected == null ? CommandCardTest.model(faction) : CommandCardTest.model(faction, selected);
        return CommandCard.of(
                        new CommandCard.Input(
                                T,
                                CommandCardTest.RESOURCES,
                                model.faction(),
                                units,
                                buildPage,
                                model.buildings(),
                                false,
                                null,
                                civ),
                        selected == null ? null : model.buildings().get(0))
                .buttons();
    }

    @Test
    void theCardHidesWhatTheRosterLacksAndTheDefaultHidesNothing() {
        BuildingState.Entry centre = CommandCardResearchTest.building(1, CENTRE, List.of(), 0);
        List<Integer> own = buttons(centre, List.of(), false, LEAN).stream()
                .filter(b -> b.kind() == CommandCard.Kind.TRAIN)
                .map(CommandCard.Button::template)
                .toList();
        assertThat(own).doesNotContain(WORKER);
        CivDef all = Objects.requireNonNull(T.civ(CivId.DEFAULT));
        assertThat(buttons(centre, List.of(), false, all).stream()
                        .filter(b -> b.kind() == CommandCard.Kind.TRAIN)
                        .map(CommandCard.Button::template))
                .contains(WORKER);
    }

    @Test
    void aTechOutsideTheRosterIsNotOffered() {
        BuildingState.Entry shed = CommandCardResearchTest.building(2, STOREHOUSE, List.of(), 0);
        assertThat(buttons(shed, List.of(), false, LEAN).stream().map(CommandCard.Button::template))
                .doesNotContain(WOOD_1);
        assertThat(buttons(shed, List.of(), false, Objects.requireNonNull(T.civ(CivId.DEFAULT))).stream()
                        .map(CommandCard.Button::template))
                .contains(WOOD_1);
    }

    @Test
    void theBuildPageAndTheBuildKeyLeaveOutABuildingOutsideTheRoster() {
        BuildingState.Entry none = null; // no building selected: workers' own page
        List<Integer> builders = List.of(WORKER);
        CivDef all = Objects.requireNonNull(T.civ(CivId.DEFAULT));
        assertThat(buttons(none, builders, true, all).stream().map(CommandCard.Button::template))
                .contains(FARMSTEAD);
        assertThat(buttons(none, builders, true, LEAN).stream().map(CommandCard.Button::template))
                .doesNotContain(FARMSTEAD);
        assertThat(CommandCard.placeable(T, null, all)).contains(FARMSTEAD);
        assertThat(CommandCard.placeable(T, null, LEAN)).doesNotContain(FARMSTEAD);
        assertThat(CommandCard.placeable(T, null))
                .as("no civilisation narrows nothing")
                .contains(FARMSTEAD);
    }

    @Test
    void aPhaseIsNamedByTheCivilisationElseByTheDefaultKeys() {
        assertThat(PhasePanel.phaseKey(LEAN, 1)).isEqualTo("phase.b");
        assertThat(PhasePanel.phaseKey(LEAN, 2)).as("past its list").isEqualTo("strategos.phase.2");
        assertThat(PhasePanel.phaseKey(null, 0)).isEqualTo("strategos.phase.0");
        TechDef phase = Objects.requireNonNull(T.tech(new TechId("strategos:phase_town")));
        PhasePanel panel = new PhasePanel(phase, List.of());
        assertThat(panel.title(LEAN)).isEqualTo("phase.b");
        assertThat(panel.title(null)).isEqualTo(phase.name());
        assertThat(panel.title(lean("[]"))).as("a civilisation with no names").isEqualTo(phase.name());
    }
}
