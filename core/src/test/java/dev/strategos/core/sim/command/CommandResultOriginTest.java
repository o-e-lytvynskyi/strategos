package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandResultOriginTest {
    @Test
    void equalEnvelopesRetainTheirDistinctSubmissionIdentities() {
        var h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        var first = new CommandEnvelope(new PlayerSlot(0), 1, h.tick(), new Stop(UnitSet.of(units)));
        var second = new CommandEnvelope(first.issuer(), first.clientSeq(), first.receivedTick(), first.command());
        assertThat(second).isEqualTo(first).isNotSameAs(first);

        var results = h.core().step(new TickInputs(h.tick() + 1, List.of(first, second))).events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .toList();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).source()).isSameAs(first);
        assertThat(results.get(1).source()).isSameAs(second);
        var detached = new CommandResult(
                first.issuer(),
                first.clientSeq(),
                results.get(0).kind(),
                results.get(0).outcome(),
                results.get(0).accepted(),
                results.get(0).rejected(),
                results.get(0).firstReason(),
                results.get(0).missing());
        assertThat(results.get(0)).isEqualTo(detached).hasSameHashCodeAs(detached);
        assertThat(results.get(0).toString()).isEqualTo(detached.toString());
    }

    @Test
    void refusedCommandsAlsoRetainTheOriginalSubmission() {
        var h = SimHarness.builder().build();
        int[] units = h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        var envelope = new CommandEnvelope(new PlayerSlot(1), 1, h.tick(), new Stop(UnitSet.of(units)));
        var result = h.core().step(new TickInputs(h.tick() + 1, List.of(envelope))).events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(result.accepted()).isZero();
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.source()).isSameAs(envelope);
    }
}
