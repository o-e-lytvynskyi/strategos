package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/** ECO-08 on the map: the first loop's scenario puts a quarry and an iron mine by each start of the proving ground. */
class FirstLoopDepositsTest {
    @Test
    void aQuarryAndAnIronMineByEachStart() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        for (Command c :
                ScenarioSetup.commands(HeadlessMatch.firstLoop(), ground.startSlots(), TemplateSet.builtin())) {
            h.submit(PlayerSlot.ADMIN, c);
        }
        h.step();
        MatchState s = KernelAccess.state(h.core());
        int deposits = 0;
        for (int id = 1; id <= s.nodes().count(); id++) {
            deposits += s.nodes().kind(id) == MaterialKind.DEPOSIT ? 1 : 0;
        }
        assertThat(deposits).isEqualTo(4);
    }
}
