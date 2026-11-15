package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AnnounceWave;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.WaveAnnounced;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-04.1 in L1: a wave is announced at its muster's start naming a leave tick at least 30 seconds ahead; it musters at
 * a camp at least 40 blocks from the base's centre, marked by cosmetic blocks that come down when the wave leaves.
 */
class WaveTest {
    private record Placed(long tick, WorldEffect effect) {}

    @Test
    void announcedThirtySecondsAhead() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 20, 20, 6);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "wave", BattleBotTest.script(4, 0, "{}")));
        List<WaveAnnounced> announced = new ArrayList<>();
        long announcedAt = -1;
        long leftAt = -1;
        long waves = 0;
        List<Placed> blocks = new ArrayList<>();
        while (h.tick() < 1_500 && leftAt < 0) {
            TickOutputs out = h.step();
            for (var e : out.events()) {
                if (e instanceof WaveAnnounced w) {
                    announced.add(w);
                    announcedAt = h.tick();
                }
                if (e instanceof WorldEffect w) {
                    blocks.add(new Placed(h.tick(), w));
                }
            }
            if (BattleBotTest.bot(h).waves() > waves) {
                waves = BattleBotTest.bot(h).waves();
                leftAt = h.tick();
            }
        }
        assertThat(announced).as("one announcement").hasSize(1);
        WaveAnnounced w = announced.get(0);
        assertThat(w.leaves() - announcedAt).as("ticks of warning").isGreaterThanOrEqualTo(AnnounceWave.LEAST);
        assertThat(leftAt).as("it left when announced, within a think").isBetween(w.leaves() - 1, w.leaves() + 20);
        assertThat(Math.hypot(w.x() - 13.5, w.z() - 13.5))
                .as("blocks from the centre")
                .isGreaterThanOrEqualTo(40);

        // the camp: a campfire between two banners, raised with the announcement
        List<Placed> fire = blocks.stream()
                .filter(p -> p.effect().state().startsWith("minecraft:campfire"))
                .toList();
        assertThat(fire).hasSize(1);
        assertThat(fire.get(0).tick()).isEqualTo(announcedAt);
        assertThat(blocks.stream().filter(p -> p.effect().state().contains("banner")))
                .as("two banners")
                .hasSize(2);
        assertThat(fire.get(0).effect().x()).isEqualTo(w.x());
        assertThat(fire.get(0).effect().z()).isEqualTo(w.z());
        // and taken down once it left
        long down = blocks.stream()
                .filter(p -> p.effect().removes()
                        && p.effect().x() == w.x()
                        && p.effect().z() == w.z())
                .mapToLong(Placed::tick)
                .findFirst()
                .orElse(-1);
        assertThat(down).as("the campfire's removal").isGreaterThanOrEqualTo(leftAt);
        assertThat(down - leftAt).isLessThanOrEqualTo(2);
    }
}
