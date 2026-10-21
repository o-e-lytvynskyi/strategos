package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JournalTest {

    @Test
    void storesOnlyTicksWithInputs() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 2, 1.0); // tick 1
        h.runTicks(5);
        h.submit(PlayerSlot.ADMIN, new Stop(UnitSet.of(units))); // applies at tick 7
        h.runTicks(10);

        Journal journal = h.core().journal();
        assertThat(journal.startTick()).isZero();
        assertThat(journal.lastTick()).isEqualTo(16);
        assertThat(journal.nonEmptyTicks()).extracting(TickInputs::tick).containsExactly(1L, 7L);
    }

    @Test
    void checkpointsEveryTwentyTicksWithTheLiveHash() {
        SimHarness h = SimHarness.builder().build();
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 5, 3.0);
        List<Long> hashesAtTwenties = new ArrayList<>();
        while (h.tick() < 100) {
            h.step();
            if (h.tick() % 20 == 0) {
                hashesAtTwenties.add(h.stateHash());
            }
        }
        Journal journal = h.core().journal();
        assertThat(journal.checkpointEvery()).isEqualTo(Journal.DEFAULT_CHECKPOINT_EVERY);
        assertThat(journal.checkpointTicks()).containsExactly(20, 40, 60, 80, 100);
        assertThat(journal.checkpointHashes())
                .containsExactly(
                        hashesAtTwenties.stream().mapToLong(Long::longValue).toArray());
    }

    @Test
    void reBasesPastItsLimitAndStillReplays() {
        MatchConfig config =
                new MatchConfig(new MatchId(1), 11, new MatchBounds(32, 32, 0, 16, 1.0), 2, MatchRules.DEFAULTS);
        MatchCore core = KernelAccess.create(config, TemplateSet.builtin(), CorePorts.of(() -> 0L), 10, 50);
        long seq = 0;
        for (long tick = 1; tick <= 130; tick++) {
            List<InputEvent> inputs = tick % 7 == 1
                    ? List.of(new CommandEnvelope(
                            PlayerSlot.ADMIN,
                            (int) ++seq,
                            tick - 1,
                            new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(16, 16), 2, 4.0)))
                    : List.of();
            core.step(new TickInputs(tick, inputs));
        }
        Journal journal = core.journal();
        assertThat(journal.startTick()).as("re-based at tick 50 and 100").isEqualTo(100);
        assertThat(journal.lastTick()).isEqualTo(130);
        assertThat(journal.checkpointTicks()).containsExactly(110, 120, 130);

        ReplayResult replay = ReplayDriver.replay(journal, TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalTick()).isEqualTo(130);
        assertThat(replay.finalHash()).isEqualTo(core.stateHash());
    }

    @Test
    void recordsEveryEnvelopeOnceInCanonicalOrderWithinItsTick() {
        SimHarness h = SimHarness.builder().seed(3).build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 4, 2.0);
        Set<String> sent = new HashSet<>();
        int seq = 1000;
        for (int t = 0; t < 200; t++) {
            long received = h.tick();
            List<InputEvent> reversed = new ArrayList<>();
            for (int k = 3; k >= 0; k--) { // highest issuer and sequence first
                if ((t + k) % 3 == 0) {
                    PlayerSlot issuer = k == 0 ? PlayerSlot.ADMIN : new PlayerSlot(k % 2);
                    CommandEnvelope e = new CommandEnvelope(issuer, seq + k, received, new Stop(UnitSet.of(units[k])));
                    reversed.add(e);
                    sent.add(issuer.value() + "/" + e.clientSeq() + "@" + received);
                }
            }
            seq += 10;
            h.core().step(new TickInputs(received + 1, reversed));
        }

        List<String> recorded = new ArrayList<>();
        for (TickInputs tick : h.core().journal().nonEmptyTicks()) {
            List<CommandEnvelope> envelopes =
                    tick.events().stream().map(CommandEnvelope.class::cast).toList();
            assertThat(envelopes).as("tick %d", tick.tick()).isSortedAccordingTo(CommandEnvelope.CANONICAL);
            envelopes.forEach(e -> recorded.add(e.issuer().value() + "/" + e.clientSeq() + "@" + e.receivedTick()));
        }
        recorded.removeIf(r -> r.startsWith("-1/1@")); // the harness's spawn
        assertThat(recorded).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(sent);
    }
}
