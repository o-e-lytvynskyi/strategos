package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.PhasePanel;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchFactionView;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TECH-04 in L1: the City phase's requirements (Town reached, three Town-tier buildings), its price above Town's, that
 * it unlocks nothing yet, that it becomes phase 2 through the civilisation's phase rule and that the HUD names it.
 */
class CityPhaseTest {
    static final TechId TOWN = new TechId("strategos:phase_town");
    static final TechId CITY = new TechId("strategos:phase_city");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    static final TemplateId SENTRY = new TemplateId("strategos:building/sentry_tower");
    static final TemplateId ARSENAL = new TemplateId("strategos:building/arsenal");
    static final TemplateId TEMPLE = new TemplateId("strategos:building/temple");

    /** A match with plenty of every resource, and five houses so that Town is affordable. */
    private static ProdScene rich() {
        ProdScene p = ProdScene.of(5_000, 5_000);
        for (String r : List.of("stone", "iron")) {
            p.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), r, 5_000));
        }
        p.h.step();
        for (int k = 0; k < 5; k++) {
            spawn(p, HOUSE, k);
        }
        return p;
    }

    /** Slot {@code k}: the first five are the houses' grid, the next ones a row along the far edge. */
    private static int spawn(ProdScene p, TemplateId template, int k) {
        int x = k < 5 ? 14 + 10 * (k % 4) : 8 + 14 * (k - 5);
        int z = k < 5 ? 14 + 10 * (k / 4) : 50;
        p.h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(0), x, z, 0));
        p.h.step();
        int[] all = p.s.buildings().handles();
        return all[all.length - 1];
    }

    private static CommandResult research(ProdScene p, TechId tech) {
        p.h.submit(ProdScene.P0, new Research(p.centre, tech));
        p.h.step();
        return p.h.results().get(p.h.results().size() - 1);
    }

    private static boolean untilResearched(ProdScene p, int limit) {
        for (int t = 0; t < limit; t++) {
            for (OutputEvent e : p.h.step().events()) {
                if (e instanceof TechResearched) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void reachTown(ProdScene p) {
        assertThat(research(p, TOWN).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(untilResearched(p, 1_300)).isTrue();
    }

    @Test
    void requirementsTable() {
        record Row(
                String name,
                boolean town,
                List<TemplateId> built,
                int foundations,
                CommandOutcome outcome,
                RejectReason reason) {}
        List<Row> rows = List.of(
                new Row(
                        "before Town",
                        false,
                        List.of(),
                        0,
                        CommandOutcome.REJECTED,
                        RejectReason.UNSUPPORTED), // the civic centre lists City only once Town is in
                new Row("Town and none", true, List.of(), 0, CommandOutcome.REJECTED, RejectReason.REQUIREMENTS),
                new Row(
                        "Town and two",
                        true,
                        List.of(TOWER, TEMPLE),
                        0,
                        CommandOutcome.REJECTED,
                        RejectReason.REQUIREMENTS),
                new Row(
                        "two and a foundation",
                        true,
                        List.of(TOWER, TEMPLE),
                        1,
                        CommandOutcome.REJECTED,
                        RejectReason.REQUIREMENTS),
                new Row(
                        "two and a sentry tower, which is no Town building",
                        true,
                        List.of(TOWER, TEMPLE, SENTRY),
                        0,
                        CommandOutcome.REJECTED,
                        RejectReason.REQUIREMENTS),
                new Row(
                        "tower, arsenal, temple",
                        true,
                        List.of(TOWER, ARSENAL, TEMPLE),
                        0,
                        CommandOutcome.ACCEPTED,
                        null),
                new Row("three towers", true, List.of(TOWER, TOWER, TOWER), 0, CommandOutcome.ACCEPTED, null),
                new Row("four", true, List.of(TOWER, TOWER, ARSENAL, TEMPLE), 0, CommandOutcome.ACCEPTED, null));
        for (Row row : rows) {
            ProdScene p = rich();
            if (row.town) {
                reachTown(p);
            }
            int k = 5;
            for (TemplateId t : row.built) {
                spawn(p, t, k++);
            }
            for (int i = 0; i < row.foundations; i++) {
                int site = spawn(p, TOWER, k++);
                p.s.buildings().setState(site, BuildingStore.FOUNDATION, 0);
            }
            CommandResult result = research(p, CITY);
            assertThat(result.outcome()).as(row.name).isEqualTo(row.outcome);
            assertThat(result.firstReason()).as(row.name).isEqualTo(row.reason);
        }
    }

    @Test
    void cityIsPhaseTwoThroughTheCivilisationsRule() {
        ProdScene p = rich();
        assertThat(Techs.phase(p.s, 0)).isZero();
        reachTown(p);
        spawn(p, TOWER, 5); // the Town buildings can be placed only once Town is in
        spawn(p, ARSENAL, 6);
        spawn(p, TEMPLE, 7);
        assertThat(Techs.phase(p.s, 0)).isEqualTo(1);
        MatchFactionView view = new MatchFactionView(p.s, 0);
        assertThat(view.reached("city")).isFalse();
        assertThat(view.completeBuildings("Town")).isEqualTo(3);
        assertThat(view.reached("town")).isTrue();
        CommandResult asked = research(p, CITY);
        assertThat(asked.firstReason()).isNull();
        assertThat(untilResearched(p, 1_900)).isTrue();
        assertThat(Techs.phase(p.s, 0)).as("the default rule, by ordinal").isEqualTo(2);
        assertThat(view.reached("city")).isTrue();
        assertThat(view.reached("town")).isTrue();
        assertThat(new MatchFactionView(p.s, 1).reached("city"))
                .as("another faction's")
                .isFalse();
    }

    @Test
    void cityCostsAndTakesMoreThanTownAndUnlocksNothingYet() {
        TemplateSet set = TemplateSet.builtin();
        TechDef town = set.tech(TOWN);
        TechDef city = set.tech(CITY);
        assertThat(city).isNotNull();
        assertThat(city.isPhase()).isTrue();
        assertThat(city.ordinal()).isEqualTo(2);
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            assertThat(city.cost().get(resource))
                    .as("City's %s is above Town's", resource)
                    .isGreaterThan(town.cost().get(resource));
        }
        assertThat(city.ticks()).isGreaterThan(town.ticks());
        assertThat(city.unlocks()).as("T16 adds the City technologies").isEmpty();
        assertThat(town.unlocks())
                .as("Town puts City on the civic centre's list")
                .contains(CITY.value());
    }

    @Test
    void theHudNamesTheDefaultCivilisationsThirdPhaseCity() {
        TemplateSet set = TemplateSet.builtin();
        var civ = set.civ(CivId.DEFAULT);
        assertThat(civ).isNotNull();
        assertThat(PhasePanel.phaseKey(civ, 2)).isEqualTo("strategos.phase.2"); // en_us: "City"
        assertThat(new PhasePanel(set.tech(CITY), List.of()).title(civ)).isEqualTo("strategos.phase.2");
    }
}
