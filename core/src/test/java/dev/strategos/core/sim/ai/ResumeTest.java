package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MATCH-04.1 in L1 at its minutes: the first loop with both slots the computer's, its buildings, nodes, queues and the
 * computers' plans, saved at minute 10 and resumed from the snapshot, reaches minute 15 with the hash of the run that
 * never stopped. It found three things a restore did not bring back: the animals counted in a population Gaia lacks,
 * fields blocking the farmers on them, and the live alerts, whose loss woke the computers anew.
 */
class ResumeTest {
    @Test
    void reloadAtTenEqualsFifteenUninterrupted() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        Scenario scenario = HeadlessMatch.firstLoop();
        List<Scenario.Slot> computers = new ArrayList<>();
        for (Scenario.Slot slot : scenario.slots()) {
            computers.add(new Scenario.Slot(ScenarioSetup.AI + "first_loop", slot.resources(), slot.entities()));
        }
        String script = HeadlessMatch.read("ai/first_loop.json");
        for (Command c : ScenarioSetup.commands(
                new Scenario(scenario.map(), scenario.victory(), computers),
                ground.startSlots(),
                TemplateSet.builtin(),
                p -> script)) {
            h.submit(PlayerSlot.ADMIN, c);
        }
        while (h.tick() < 10 * 1_200) {
            h.step();
        }
        MatchSnapshot ten = h.core().snapshot();
        assertThat(ten.buildings()).as("a town by minute 10").hasSizeGreaterThan(3);
        SimHarness back = SimHarness.restore(
                ten, TemplateSet.builtin(), h.core().journal().segments().get(0)::slice);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        while (h.tick() < 15 * 1_200) {
            h.step();
            back.step();
            assertThat(back.stateHash()).as("tick %d", h.tick()).isEqualTo(h.stateHash());
        }
        assertThat(back.tick()).isEqualTo(h.tick());
        assertThat(back.stateHash()).as("minute 15").isEqualTo(h.stateHash());
    }
}
