package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CIV-01's binding (M10 §2.2): each faction's civilisation and roster, and the refusals when the set lacks one. */
class FactionCivsTest {
    private static MatchConfig config(List<CivId> civs) {
        return new MatchConfig(
                new MatchId(1), 1L, new MatchBounds(32, 32, 0, 16, 1.0), 2, MatchRules.DEFAULTS, List.of(), civs);
    }

    @Test
    void anEmptyListBindsEveryFactionToTheDefaultWithTheWildcardRoster() {
        FactionCivs civs = FactionCivs.bind(config(List.of()), TemplateSet.builtin());
        for (int f = 0; f < 2; f++) {
            assertThat(civs.of(f).id()).isEqualTo(CivId.DEFAULT);
            assertThat(civs.hasTemplate(f, 0)).isTrue();
            assertThat(civs.hasTech(f, 0)).isTrue();
        }
        assertThat(civs.of(2)).as("Gaia has no civilisation").isNull();
        assertThat(civs.hasTemplate(2, 0)).as("and may own anything").isTrue();
    }

    @Test
    void aRosterIsABitsetOverTheNetIndices() {
        TemplateSet set = CivScene.lean("[]");
        FactionCivs civs = FactionCivs.bind(config(List.of(CivScene.LEAN, CivId.DEFAULT)), set);
        int worker = set.byId(CivScene.WORKER).netIndex();
        int house = set.byId(new TemplateId("strategos:building/house")).netIndex();
        assertThat(civs.hasTemplate(0, worker)).isFalse();
        assertThat(civs.hasTemplate(0, house)).isTrue();
        assertThat(civs.hasTemplate(1, worker)).isTrue();
        assertThat(civs.hasTech(0, set.tech(CivScene.WOOD_1).netIndex())).isFalse();
        assertThat(civs.hasTech(1, set.tech(CivScene.WOOD_1).netIndex())).isTrue();
    }

    @Test
    void aMatchNeedingACivilisationTheSetLacksDoesNotStart() {
        assertThatThrownBy(() -> SimHarness.builder()
                        .civs(List.of(CivScene.LEAN, CivId.DEFAULT))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("t:lean")
                .hasMessageContaining("lacks");
    }

    @Test
    void aSetWithCivilisationsButNoDefaultRefusesAnEmptyList() {
        TemplateSet noDefault = CivScene.withoutDefault();
        assertThat(noDefault.civ(CivId.DEFAULT)).isNull();
        assertThatThrownBy(() -> SimHarness.builder().templates(noDefault).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CivId.DEFAULT.value());
        SimHarness ok = SimHarness.builder()
                .templates(noDefault)
                .civs(List.of(CivScene.LEAN, CivScene.LEAN))
                .build();
        assertThat(KernelAccess.state(ok.core()).civs().of(1).id()).isEqualTo(CivScene.LEAN);
    }

    @Test
    void aSwapThatRemovesACivilisationInUseIsRefused() {
        SimHarness h = CivScene.match(CivScene.lean("[]"), List.of(CivScene.LEAN, CivId.DEFAULT));
        MatchState s = KernelAccess.state(h.core());
        TemplateSet before = s.templates();
        h.schedule(h.tick() + 1, new ContentSwap(TemplateSet.builtin()));
        h.runTicks(2);
        assertThat(s.templates()).as("the set is kept").isSameAs(before);
        assertThat(s.civs().of(0).id()).isEqualTo(CivScene.LEAN);
        assertThat(s.templateSwapProblem(TemplateSet.builtin())).contains("t:lean");
    }

    @Test
    void aSwapKeepingTheCivilisationsIsAccepted() {
        SimHarness h = CivScene.match(CivScene.lean("[]"), List.of(CivScene.LEAN, CivId.DEFAULT));
        MatchState s = KernelAccess.state(h.core());
        TemplateSet next = CivScene.lean(CivScene.WOOD_BONUS);
        assertThat(s.templateSwapProblem(next)).isNull();
        h.schedule(h.tick() + 1, new ContentSwap(next));
        h.runTicks(2);
        assertThat(s.templates()).isSameAs(next);
    }

    @Test
    void aRestoreThatCannotResolveACivilisationFailsLikeAMissingTemplate() {
        SimHarness h = CivScene.match(CivScene.lean("[]"), List.of(CivScene.LEAN, CivId.DEFAULT));
        MatchSnapshot snapshot = h.core().snapshot();
        assertThatThrownBy(() -> SimHarness.restore(snapshot, TemplateSet.builtin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("t:lean");
    }

    @Test
    void aListOfTheWrongSizeIsRefused() {
        assertThatThrownBy(() -> config(List.of(CivId.DEFAULT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 civilisations for 2 factions");
    }

    @Test
    void eachFactionsRulesAreResolvedAtBindAndAMissingEntryIsTheDefault() {
        TemplateSet set = CivScene.with(TestRules.civ("{\"phase\":{\"id\":\"buildings\"}}"));
        FactionCivs civs = FactionCivs.bind(config(List.of(CivScene.LEAN, CivId.DEFAULT)), set, TestRules.catalogue());

        assertThat(civs.rules(0).phase()).as("the selected variant").isNotSameAs(PhaseRule.DEFAULT);
        assertThat(civs.rules(0).defeat()).as("no entry: the seam's default").isSameAs(DefeatRule.DEFAULT);
        assertThat(civs.rules(1)).isEqualTo(CivRules.DEFAULTS);
        assertThat(civs.rules(2)).as("Gaia").isEqualTo(CivRules.DEFAULTS);
    }

    @Test
    void aRuleTheCatalogueLacksDoesNotBind() {
        TemplateSet set = CivScene.with(TestRules.civ("{\"phase\":{\"id\":\"buildings\"}}"));
        MatchConfig config = config(List.of(CivScene.LEAN, CivId.DEFAULT));

        assertThat(FactionCivs.problem(config, set)).contains("t:lean").contains("unknown rule 'buildings'");
        assertThatThrownBy(() -> FactionCivs.bind(config, set))
                .isInstanceOf(CivBindException.class)
                .hasMessageContaining("unknown rule 'buildings'");
        assertThat(FactionCivs.problem(config, set, TestRules.catalogue())).isNull();
        assertThatThrownBy(() -> SimHarness.builder()
                        .templates(set)
                        .civs(List.of(CivScene.LEAN, CivId.DEFAULT))
                        .build())
                .isInstanceOf(CivBindException.class);
    }

    @Test
    void aSwapToASetWithAnUnknownRuleIsRefused() {
        SimHarness h = CivScene.match(CivScene.lean("[]"), List.of(CivScene.LEAN, CivId.DEFAULT));
        MatchState s = KernelAccess.state(h.core());
        TemplateSet bad = CivScene.with(TestRules.civ("{\"upkeep\":{\"id\":\"income\"}}"));

        assertThat(s.templateSwapProblem(bad)).contains("unknown rule 'income'");
    }
}
