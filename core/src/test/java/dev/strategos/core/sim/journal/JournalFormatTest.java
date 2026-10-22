package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.core.testkit.SimHarness;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** CORE-11: the binary journal file round-trips, replays, and refuses what it cannot read. */
class JournalFormatTest {

    @Test
    void roundTripsAndStillReplaysToTheLiveHash() {
        JournalExport export = walkingRun();

        JournalExport copy = JournalFormat.decode(JournalFormat.encode(export));

        assertThat(copy.liveTick()).isEqualTo(export.liveTick());
        assertThat(copy.liveHash()).isEqualTo(export.liveHash());
        Journal original = export.journal();
        Journal read = copy.journal();
        assertThat(read.checkpointEvery()).isEqualTo(original.checkpointEvery());
        assertThat(read.segments()).hasSameSizeAs(original.segments());
        assertThat(read.startTick()).isEqualTo(original.startTick());
        assertThat(read.lastTick()).isEqualTo(original.lastTick());
        assertThat(read.nonEmptyTicks()).isEqualTo(original.nonEmptyTicks());
        assertThat(read.checkpointTicks()).containsExactly(original.checkpointTicks());
        assertThat(read.checkpointHashes()).containsExactly(original.checkpointHashes());
        assertThat(ReplayRunner.check(read, TemplateSet.builtin(), copy.liveHash())
                        .ok())
                .isTrue();
    }

    @Test
    void encodingIsStable() {
        JournalExport export = walkingRun();
        assertThat(JournalFormat.encode(JournalFormat.decode(JournalFormat.encode(export))))
                .isEqualTo(JournalFormat.encode(export));
    }

    @Test
    void refusesAFileWithoutTheHeader() {
        byte[] bytes = JournalFormat.encode(walkingRun());
        bytes[0] = 'X';
        assertThatThrownBy(() -> JournalFormat.decode(bytes))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("no STRJ header");
    }

    @Test
    void refusesAnotherFormatVersion() {
        byte[] bytes = JournalFormat.encode(walkingRun());
        bytes[5] = 9;
        assertThatThrownBy(() -> JournalFormat.decode(bytes))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("format version 9");
    }

    @Test
    void refusesAnotherCommandCatalogue() {
        byte[] header = new WireWriter()
                .writeRaw(new byte[] {'S', 'T', 'R', 'J'})
                .writeShort(JournalFormat.VERSION)
                .writeString("strategos:move=7", 64)
                .toByteArray();
        assertThatThrownBy(() -> JournalFormat.decode(header))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("written with commands [strategos:move=7]");
    }

    @Test
    void refusesATruncatedFileAndTrailingBytes() {
        byte[] bytes = JournalFormat.encode(walkingRun());
        assertThatThrownBy(() -> JournalFormat.decode(Arrays.copyOf(bytes, bytes.length - 3)))
                .isInstanceOf(WireException.class);
        assertThatThrownBy(() -> JournalFormat.decode(Arrays.copyOf(bytes, bytes.length + 1)))
                .isInstanceOf(WireException.class)
                .hasMessageContaining("trailing");
    }

    private static JournalExport walkingRun() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 3, 1.0);
        h.runTicks(3);
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(units), new Vec2d(24, 20)));
        h.runTicks(60);
        return new JournalExport(h.core().journal(), h.tick(), h.stateHash());
    }
}
