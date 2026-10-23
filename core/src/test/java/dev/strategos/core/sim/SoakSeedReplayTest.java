package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.SoakOrders;
import org.junit.jupiter.api.Test;

/**
 * PERF-05.4 (M1 spec §8.5): a crowd soak seed reproduces exactly from its journal. The soak's first seed drives {@link
 * SoakOrders} on the proving ground's terrain, two factions of {@value #PER_FACTION} at its start slots, for {@value
 * #TICKS} ticks; the journal, through its disk format, replays to the run's hash at every checkpoint and at the end.
 * The stuck ladder's state is in the hash, so a unit stuck in the run is stuck again in the replay, tick for tick, as
 * a failing soak's exported journal is stepped again.
 */
class SoakSeedReplayTest {
    private static final int PER_FACTION = 100;
    private static final int TICKS = 1_200;

    @Test
    void theSoakSeedReplaysFromItsJournal() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        for (int f = 0; f < 2; f++) {
            StartSlot slot = ground.startSlots().get(f);
            h.spawn(new FactionSlot(f), new Vec2d(slot.x() + 0.5, slot.z() + 0.5), PER_FACTION, 10);
        }
        SoakOrders orders = new SoakOrders(Seeds.first("soak"), ground);
        for (int t = 0; t < TICKS; t++) {
            Move move = orders.next(h.tick() + 1, h.queries());
            if (move != null) {
                h.submit(PlayerSlot.ADMIN, move);
            }
            h.step();
        }

        assertThat(orders.groups()).isEqualTo(TICKS / SoakOrders.EVERY);
        assertThat(h.queries().ladderRungs()[1])
                .as("units the ladder had to help")
                .isPositive();
        JournalExport export = new JournalExport(h.core().journal(), h.tick(), h.stateHash());
        JournalExport read = JournalFormat.decode(JournalFormat.encode(export));
        ReplayResult replay = ReplayDriver.replay(read.journal(), TemplateSet.builtin());
        assertThat(replay.matches()).as("every checkpoint: %s", replay).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }
}
