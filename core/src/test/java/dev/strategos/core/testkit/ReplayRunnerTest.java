package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.journal.JournalWriter;
import dev.strategos.core.testkit.ReplayRunner.ReplayReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CORE-05.3: re-recorded with a checkpoint every tick, a journal names the very tick a dropped command changed. */
class ReplayRunnerTest {
    private static final int TICKS = 200;
    private static final long DROPPED_AT = 137;

    /**
     * A seeded live run of 200 ticks: two spawns at tick 1, random commands every 10 ticks, and the only command
     * recorded at tick 137, an ADMIN Move. {@code hashes[t]} is the live hash after tick t.
     */
    private static final class LiveRun {
        final SimHarness h = SimHarness.builder().seed(Seeds.first("m0-200")).build();
        final long[] hashes = new long[TICKS + 1];

        LiveRun() {
            long seed = h.queries().config().seed();
            FactionSlot west = new FactionSlot(0);
            h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, west, new Vec2d(8, 8), 10, 4.0));
            h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 24), 10, 4.0));
            while (h.tick() < TICKS) {
                if (h.tick() == DROPPED_AT - 1) {
                    int unit = h.queries().unitHandlesOf(west)[0];
                    h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(30.5, 2.5)));
                } else if (h.tick() > 0 && h.tick() % 10 == 0) {
                    for (int f = 0; f < 2; f++) {
                        for (Command c : RandomCommands.next(seed, h.tick(), h.queries(), new FactionSlot(f))) {
                            h.submit(new PlayerSlot(f), c);
                        }
                    }
                }
                h.step();
                hashes[(int) h.tick()] = h.stateHash();
            }
        }

        /** The live journal's inputs with a checkpoint every tick, holding the live hash; tick {@code drop} loses its
         * first input (none dropped for -1). */
        Journal everyTick(long drop) {
            Journal live = h.core().journal();
            JournalWriter writer = new JournalWriter(live.start(), 1, Integer.MAX_VALUE);
            List<TickInputs> recorded = live.nonEmptyTicks();
            int next = 0;
            for (long tick = live.startTick() + 1; tick <= live.lastTick(); tick++) {
                TickInputs inputs = TickInputs.empty(tick);
                if (next < recorded.size() && recorded.get(next).tick() == tick) {
                    inputs = recorded.get(next++);
                }
                if (tick == drop) {
                    List<InputEvent> kept = new ArrayList<>(inputs.events());
                    kept.remove(0);
                    inputs = new TickInputs(tick, kept);
                }
                writer.record(inputs);
                writer.checkpoint(tick, hashes[(int) tick]);
            }
            return writer.view();
        }
    }

    @Test
    void aFaithfulJournalReplaysToEveryLiveHash() {
        LiveRun run = new LiveRun();
        Journal journal = run.everyTick(-1);

        ReplayReport report = ReplayRunner.check(journal, TemplateSet.builtin(), run.h.stateHash());

        assertThat(report.ok()).as(report.message()).isTrue();
        assertThat(journal.checkpointTicks()).hasSize(TICKS);
        assertThat(report.message()).isEqualTo("the replay matches 200 checkpoints and the final hash at tick 200");
    }

    @Test
    void droppingTheCommandRecordedAtTick137NamesTick137() {
        LiveRun run = new LiveRun();
        assertThat(run.h.core().journal().nonEmptyTicks())
                .filteredOn(t -> t.tick() == DROPPED_AT)
                .singleElement()
                .satisfies(
                        t -> assertThat(t.events()).as("the inputs of tick 137").hasSize(1));

        ReplayReport report = ReplayRunner.check(run.everyTick(DROPPED_AT), TemplateSet.builtin(), run.h.stateHash());

        assertThat(report.ok()).isFalse();
        assertThat(report.result().firstDivergentCheckpoint()).isEqualTo(DROPPED_AT);
        assertThat(report.message()).startsWith("the replay diverges at checkpoint tick 137: recorded ");
    }

    @Test
    void aWrongFinalHashFailsEvenWhenEveryCheckpointMatches() {
        LiveRun run = new LiveRun();

        ReplayReport report = ReplayRunner.check(run.everyTick(-1), TemplateSet.builtin(), run.h.stateHash() + 1);

        assertThat(report.ok()).isFalse();
        assertThat(report.result().matches()).isTrue();
        assertThat(report.message()).startsWith("the replay reaches tick 200 with hash ");
    }
}
