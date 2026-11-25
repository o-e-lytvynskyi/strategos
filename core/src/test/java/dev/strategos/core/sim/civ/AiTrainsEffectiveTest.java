package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The computer player asks what a building trains the way production does: the faction's effective
 * {@code Trainer/units} (a civilisation's bonus included) and its roster. The probe civilisation (M10 T9b) found it
 * reading the template's static list, so a bonus that adds a unit to a building was invisible to the AI.
 */
class AiTrainsEffectiveTest {
    private static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    private static final TemplateId RAM = new TemplateId("strategos:unit/ram");
    private static final TemplateId INFANTRY = new TemplateId("strategos:unit/infantry");

    /** A bonus that adds the ram to the barracks of the lean civilisation's factions only. */
    private static final String RAM_AT_BARRACKS =
            "[{\"path\":\"Trainer/units\",\"op\":\"tokens\",\"tokens\":\"+strategos:unit/ram\",\"filter\":\"Barracks\"}]";

    @Test
    void aBonusThatAddsAUnitToABuildingIsWhatTheAiSeesForItsFactionOnly() {
        SimHarness h = CivScene.match(CivScene.lean(RAM_AT_BARRACKS), List.of(CivScene.LEAN, CivId.DEFAULT));

        assertThat(h.queries().trains(new FactionSlot(0), BARRACKS, RAM))
                .as("the lean faction's barracks train the ram through its bonus")
                .isTrue();
        assertThat(h.queries().trains(new FactionSlot(1), BARRACKS, RAM))
                .as("a default faction's barracks do not")
                .isFalse();
        assertThat(h.queries().trains(new FactionSlot(1), BARRACKS, INFANTRY))
                .as("the static list still holds")
                .isTrue();
    }

    @Test
    void aUnitOutsideTheRosterIsNotTrainedWhateverTheListSays() {
        SimHarness h = CivScene.match(CivScene.lean("[]"), List.of(CivScene.LEAN, CivId.DEFAULT));
        TemplateId worker = CivScene.WORKER;
        TemplateId centre = CivScene.CENTRE;

        assertThat(h.queries().trains(new FactionSlot(1), centre, worker)).isTrue();
        assertThat(h.queries().trains(new FactionSlot(0), centre, worker))
                .as("the lean roster lacks the worker")
                .isFalse();
    }
}
