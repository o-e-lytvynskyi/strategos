package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchFactionView;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.prod.Techs;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: each quiet seam's default is today's behaviour moved behind it, bit for bit: the phase is the highest researched
 * phase ordinal, a faction holds while it has a finished building of its centre class, every resource is used, upkeep
 * does nothing, and a match whose civilisation lists every default id explicitly hashes like one with no rules at all.
 */
class SeamDefaultsTest {
    private static final TechId TOWN = new TechId("strategos:phase_town");
    private static final TechId CITY = new TechId("strategos:phase_city");

    private static SimHarness match() {
        return CivScene.match(TemplateSet.builtin(), List.of());
    }

    @Test
    void theDefaultPhaseIsTheHighestResearchedPhaseOrdinal() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        TemplateSet set = s.templates();

        assertThat(Techs.phase(s, 0)).as("Village, none researched").isZero();
        s.progress().complete(0, TOWN);
        assertThat(Techs.phase(s, 0)).isEqualTo(set.tech(TOWN).ordinal()).isEqualTo(1);
        assertThat(Techs.phase(s, 1))
                .as("another faction's research is its own")
                .isZero();
        if (set.tech(CITY) != null) {
            s.progress().complete(0, CITY);
            assertThat(Techs.phase(s, 0)).isEqualTo(set.tech(CITY).ordinal());
        }
        assertThat(s.civs().rules(0).phase()).isSameAs(PhaseRule.DEFAULT);
        assertThat(Techs.phase(s, 0)).isEqualTo(Techs.highestResearchedPhase(s, 0));
    }

    @Test
    void requirementsAndReplicationAskTheDefaultPhaseRuleAndKeepToday() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        MatchFactionView view = new MatchFactionView(s, 0);

        assertThat(view.reached("village")).isTrue();
        assertThat(view.reached("town")).isFalse();
        assertThat(ReplicationService.factionState(h.queries(), 1, new FactionSlot(0))
                        .phase())
                .isZero();
        s.progress().complete(0, TOWN);
        assertThat(view.reached("town")).isTrue();
        assertThat(view.reached("city")).as("a phase not researched").isFalse();
        assertThat(view.reached("nonesuch")).isFalse();
        assertThat(ReplicationService.factionState(h.queries(), 1, new FactionSlot(0))
                        .phase())
                .isEqualTo(1);
    }

    @Test
    void theDefaultDefeatRuleHoldsWhileAFinishedCentreStands() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());

        assertThat(DefeatRule.DEFAULT.holds(s, 0)).isTrue();
        assertThat(DefeatRule.DEFAULT.holds(s, 1)).isTrue();
        for (int handle : s.buildings().handles()) {
            if (s.buildings().faction(handle) == 1) {
                s.queueDamage(
                        dev.strategos.core.sim.combat.Damage.lethal(handle, dev.strategos.core.shared.Handle.NONE));
            }
        }
        h.runTicks(2);
        assertThat(DefeatRule.DEFAULT.holds(s, 1)).as("its centre fell").isFalse();
        assertThat(DefeatRule.DEFAULT.holds(s, 0)).isTrue();
    }

    @Test
    void theDefeatRuleReadsTheCivilisationsCentreClass() {
        JsonObject lean = TestRules.civ("{}");
        lean.addProperty("centreClass", "House");
        TemplateSet set = CivScene.with(lean);
        SimHarness h = CivScene.match(set, List.of(CivScene.LEAN, CivId.DEFAULT));
        MatchState s = KernelAccess.state(h.core());

        assertThat(DefeatRule.DEFAULT.holds(s, 0))
                .as("a civic centre is no House")
                .isFalse();
        assertThat(DefeatRule.DEFAULT.holds(s, 1)).isTrue();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(0), 20, 20, 0));
        h.step();
        assertThat(DefeatRule.DEFAULT.holds(s, 0)).isTrue();
    }

    @Test
    void everyResourceIsUsedAndUpkeepDoesNothing() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        long before = h.stateHash();
        int resources = s.treasury().resources();

        for (int f = 0; f < 3; f++) {
            for (int r = 0; r < resources; r++) {
                assertThat(s.civs().rules(f).resource().uses(f, r)).isTrue();
            }
        }
        long granted = s.treasury().granted(0, 0);
        UpkeepRule.DEFAULT.step(s, 0);
        assertThat(s.treasury().granted(0, 0)).isEqualTo(granted);
        assertThat(h.stateHash()).isEqualTo(before);
        assertThat(s.civs().rules(0)).isEqualTo(CivRules.DEFAULTS);
        assertThat(s.civs().rules(5)).isEqualTo(CivRules.DEFAULTS);
    }

    @Test
    void thePopulationDefaultIsHousingUnderTheMatchMaximumRefusedAsHousingNeeded() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());
        long before = h.stateHash();

        assertThat(s.civs().rules(0).population()).isSameAs(PopulationRule.DEFAULT);
        assertThat(PopulationRule.DEFAULT.refusal()).isEqualTo(NoticeKind.HOUSING_NEEDED);
        for (int f = 0; f < 2; f++) {
            assertThat(s.populationCap(f))
                    .as("the centre's twenty")
                    .isEqualTo(
                            Math.min(s.population().housing(f), s.population().max()))
                    .isEqualTo(20);
        }
        s.population().house(0, 7);
        assertThat(s.populationCap(0)).isEqualTo(27);
        s.population().setMax(10);
        assertThat(s.populationCap(0)).as("the match maximum caps it").isEqualTo(10);
        assertThat(PopulationRule.DEFAULT.fits(
                        s.population(), 0, 10 - s.population().used(0)))
                .isTrue();
        assertThat(PopulationRule.DEFAULT.fits(
                        s.population(), 0, 11 - s.population().used(0)))
                .isFalse();
        assertThat(h.stateHash()).as("the counters are not hashed").isEqualTo(before);
    }

    @Test
    void theDeliveryDefaultCreditsNothingInPlaceSoTheLoadGoesToADropsite() {
        SimHarness h = match();
        MatchState s = KernelAccess.state(h.core());

        assertThat(s.civs().rules(0).delivery()).isSameAs(DeliveryRule.DEFAULT);
        assertThat(DeliveryRule.DEFAULT.creditInPlace(s, 0)).isFalse();
    }

    @Test
    void aCivilisationListingEveryDefaultIdHashesLikeOneWithoutRules() {
        TemplateSet bare = TemplateSet.builtin();
        JsonObject explicit = JsonParser.parseString("{\"roles\":{\"house\":\"strategos:building/house\"},\"rules\":{"
                        + "\"population\":{\"id\":\"default\"},\"delivery\":{\"id\":\"default\"},"
                        + "\"construction\":{\"id\":\"default\"},\"upkeep\":{\"id\":\"default\"},"
                        + "\"resources\":{\"id\":\"default\"},\"phase\":{\"id\":\"default\"},"
                        + "\"placement\":{\"id\":\"default\"},\"defeat\":{\"id\":\"default\"}}}")
                .getAsJsonObject();
        CivDef def = CivDef.fromJson(CivId.DEFAULT, explicit, "d.json", new java.util.ArrayList<>());
        assertThat(def).isNotNull();
        assertThat(RuleCatalogue.DEFAULT.problems(def)).isEmpty();
        TemplateSet withRules = CivScene.withDefault(explicit);

        SimHarness a = CivScene.match(bare, List.of());
        SimHarness b = CivScene.match(withRules, List.of());
        a.runTicks(60);
        b.runTicks(60);

        assertThat(KernelAccess.state(b.core()).civs().rules(0)).isEqualTo(CivRules.DEFAULTS);
        assertThat(b.stateHash()).isEqualTo(a.stateHash());
    }
}
