package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * An order sent again resets a unit's stuck count (the ladder climbs only on an unbroken count), so a bot that told its
 * wave to attack on every think kept units held up in a crowd round a burning centre from ever reaching the ladder's
 * end: one stood 640 ticks without progress in a one-block lane (EconomyBotTest seed 3). A wave already on its target is
 * left alone.
 */
class FieldWaveTest {
    @Test
    void aWaveOnItsTargetIsNotReorderedEveryThink() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 100, 100, 8);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "field", DefenceTest.QUIET));
        h.step();
        BattleBotTest.bot(h).memory().set(Slots.WAVE, 1); // a wave has already gone out: the bot keeps pushing it
        h.runTicks(300); // they arrive and settle on the centre
        long before = BattleBotTest.bot(h).commands();
        h.runTicks(400);
        assertThat(BattleBotTest.bot(h).commands() - before)
                .as("commands in 400 ticks to a wave that is burning the centre")
                .isLessThanOrEqualTo(3);
    }
}
