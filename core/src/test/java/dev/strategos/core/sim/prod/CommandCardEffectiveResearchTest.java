package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.CardLists;
import dev.strategos.core.client.hud.ClientFactionView;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * TECH-05: the command card lists what a building researches, trains and lets workers place as the faction's stat
 * service tells it, base tokens plus those techs add. The client runs {@code TokenLists} over its template copy and
 * the research it was told; this proves it agrees with the server's {@code blockTokens}, and with what {@code
 * ResearchRules} accepts, for a set of research states.
 */
class CommandCardEffectiveResearchTest {
    static final TemplateSet T = TemplateSet.builtin();
    static final List<String> LISTS = List.of("Researcher/techs", "Trainer/units", "Builder/buildings");

    private static TechDef tech(String id) {
        return Objects.requireNonNull(T.tech(new TechId("strategos:" + id)), id);
    }

    private static ClientFactionView client(List<String> researched) {
        List<Integer> indexes = new ArrayList<>();
        for (String id : researched) {
            indexes.add(tech(id).netIndex());
        }
        FactionState f = new FactionState(
                1,
                0,
                10,
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                0,
                0,
                0,
                List.of(0, 0, 0, 0),
                indexes,
                0);
        return new ClientFactionView(T, f, List.of(), null);
    }

    private static List<List<String>> states() {
        List<String> all =
                T.techs().stream().map(t -> t.id().value().substring(10)).toList();
        return List.of(
                List.of(),
                List.of("phase_town"),
                List.of("phase_town", "phase_city"),
                List.of("phase_town", "gather_wood_1", "gather_wood_2", "iron_weapons", "chainmail"),
                List.of("phase_town", "phase_city", "iron_weapons", "diamond_weapons", "netherite_weapons"),
                all);
    }

    @Test
    void theClientsListsAreTheServersForEveryResearchState() {
        for (List<String> researched : states()) {
            ProdScene p = ProdScene.of(0, 0);
            MatchState s = p.s;
            for (String id : researched) {
                Techs.complete(s, 0, tech(id));
            }
            ClientFactionView view = client(researched);
            for (String list : LISTS) {
                ValuePath path = T.tokenPaths().byPath(list);
                if (path == null) {
                    continue;
                }
                for (int t : CommandCard.buildings(T)) {
                    List<String> server = s.stats().blockTokens(t, 0, path.id());
                    assertThat(view.tokens(t, list))
                            .as("%s of %s after %s", list, T.byIndex(t).id(), researched)
                            .isEqualTo(server == null ? List.of() : server);
                }
                for (var unit : T.all()) {
                    List<String> server = s.stats().blockTokens(unit.netIndex(), 0, path.id());
                    assertThat(view.tokens(unit.netIndex(), list))
                            .as("%s of %s after %s", list, unit.id(), researched)
                            .isEqualTo(server == null ? List.of() : server);
                }
            }
        }
    }

    @Test
    void theClientsResearchButtonsAreWhatTheServerAcceptsToResearch() {
        for (List<String> researched : states()) {
            ProdScene p = ProdScene.of(0, 0);
            for (String id : researched) {
                Techs.complete(p.s, 0, tech(id));
            }
            List<TechDef> shown =
                    CardLists.researches(T, client(researched), p.s.buildings().template(p.centre));
            for (TechDef def : T.techs()) {
                assertThat(shown.contains(def))
                        .as("%s on the civic centre after %s", def.id(), researched)
                        .isEqualTo(Techs.researches(p.s, p.centre, def)); // the check ResearchRules makes
            }
        }
    }

    @Test
    void townsTiersAndEquipmentAppearOnTheirBuildingsOnlyAfterTown() {
        int storehouse = Objects.requireNonNull(T.byId(new TemplateId("strategos:building/storehouse")))
                .netIndex();
        int barracks = Objects.requireNonNull(T.byId(new TemplateId("strategos:building/barracks")))
                .netIndex();
        int centre = Objects.requireNonNull(T.byId(new TemplateId("strategos:building/civic_centre")))
                .netIndex();
        ClientFactionView before = client(List.of());
        ClientFactionView town = client(List.of("phase_town"));
        ClientFactionView city = client(List.of("phase_town", "phase_city"));
        assertThat(ids(CardLists.researches(T, before, storehouse))).doesNotContain("gather_wood_2", "carry_2");
        assertThat(ids(CardLists.researches(T, town, storehouse)))
                .contains("gather_wood_2", "gather_stone_2", "gather_iron_2", "carry_2")
                .doesNotContain("gather_wood_3");
        assertThat(ids(CardLists.researches(T, city, storehouse))).contains("gather_wood_3", "carry_3");
        assertThat(ids(CardLists.researches(T, before, barracks))).doesNotContain("iron_weapons", "chainmail");
        assertThat(ids(CardLists.researches(T, town, barracks)))
                .contains("iron_weapons", "chainmail", "diamond_weapons", "iron_armour");
        assertThat(ids(CardLists.researches(T, before, centre))).doesNotContain("phase_city");
        assertThat(ids(CardLists.researches(T, town, centre))).contains("phase_city");
    }

    private static List<String> ids(List<TechDef> techs) {
        return techs.stream().map(t -> t.id().value().substring(10)).toList();
    }
}
