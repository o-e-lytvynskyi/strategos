package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.JournalSink;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * MATCH-04.4 in L1: a match that kept two minutes of journal, a ring, holds its last two segments only, and the ring,
 * saved to the binary format and read back, replays from its first snapshot to the match's hash at its last tick.
 */
class JournalRingTest {
    @Test
    void aSavedRingReplaysToItsHash() {
        SimHarness h = SimHarness.builder()
                .size(64, 64)
                .journal(JournalSink.ring(2 * Journal.SEGMENT_TICKS))
                .build();
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(10, 10), 6, 2));
        for (int t = 0; t < 5 * Journal.SEGMENT_TICKS + 300; t++) {
            if (t % 400 == 0) {
                h.submit(
                        new PlayerSlot(0),
                        Move.replace(UnitSet.of(h.queries().unitHandles()), new Vec2d(10 + (t / 400 % 4) * 10, 30)));
            }
            h.step();
        }
        Journal ring = h.core().journal();
        assertThat(ring.lastTick() - ring.startTick())
                .as("the last two minutes and the minute begun")
                .isLessThanOrEqualTo(3L * Journal.SEGMENT_TICKS);
        assertThat(ring.startTick()).as("the first minutes dropped").isGreaterThan(0);
        byte[] saved = JournalFormat.encode(new JournalExport(ring, h.tick(), h.stateHash()));
        JournalExport back = JournalFormat.decode(saved);
        ReplayResult replay = ReplayDriver.replay(back.journal(), TemplateSet.builtin());
        assertThat(replay.matches())
                .as("every checkpoint, first divergent at %d", replay.firstDivergentCheckpoint())
                .isTrue();
        assertThat(replay.finalTick()).isEqualTo(h.tick());
        assertThat(replay.finalHash()).as("the match's hash at its last tick").isEqualTo(h.stateHash());
    }
}
