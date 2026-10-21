package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Snapshot and restore of pending commands join with T6a (M0 spec 1.6 row 45). */
class CommandOrderingTest {

    private static List<CommandResult> results(TickOutputs out) {
        return out.events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .toList();
    }

    @Test
    void commandsOfATickApplyInCanonicalOrder() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 2, 1.0);
        long received = h.tick();
        Stop stop = new Stop(UnitSet.of(units));
        List<InputEvent> reversed = List.of(
                new CommandEnvelope(new PlayerSlot(1), 2, received, stop),
                new CommandEnvelope(new PlayerSlot(1), 1, received, stop),
                new CommandEnvelope(new PlayerSlot(0), 9, received, stop),
                new CommandEnvelope(PlayerSlot.ADMIN, 4, received, stop));

        TickOutputs out = h.core().step(new TickInputs(received + 1, reversed));

        assertThat(results(out))
                .extracting(r -> r.issuer().value() + "/" + r.clientSeq())
                .containsExactly("-1/4", "0/9", "1/1", "1/2");
    }

    @Test
    void threeHundredCommandsApplyTwoHundredFiftySixNowAndTheRestNextTickInOrder() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0.0);
        List<Integer> seqs = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            seqs.add(h.submit(PlayerSlot.ADMIN, new Stop(UnitSet.of(units))));
        }

        List<CommandResult> now = results(h.step());
        List<CommandResult> next = results(h.step());

        assertThat(now).extracting(CommandResult::clientSeq).containsExactlyElementsOf(seqs.subList(0, 256));
        assertThat(next).extracting(CommandResult::clientSeq).containsExactlyElementsOf(seqs.subList(256, 300));
        assertThat(results(h.step())).isEmpty();
    }

    @Test
    void everyEnvelopeGetsExactlyOneResult() {
        SimHarness h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0.0);
        List<String> sent = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            PlayerSlot issuer = i % 3 == 0 ? PlayerSlot.ADMIN : new PlayerSlot(i % 2);
            int seq = h.submit(issuer, new Stop(UnitSet.of(units)));
            sent.add(issuer.value() + "/" + seq);
        }
        h.runTicks(3);

        List<String> answered = h.results().stream()
                .filter(r -> r.kind().equals("strategos:stop"))
                .map(r -> r.issuer().value() + "/" + r.clientSeq())
                .toList();
        assertThat(answered).hasSize(300).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(sent);
    }
}
