package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandKinds;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-01: an administrator's grant through the command path, refused to a player and for a resource the match lacks;
 * a refusal for want of resources names what is missing (ECO-01.2's result, which every paying rule returns); the
 * treasury survives a snapshot, and a match that never had one saves none.
 */
class PaymentTest {
    @Test
    void aGrantIsTheAdministratorsAndNamesAResourceTheMatchHas() {
        SimHarness h = SimHarness.builder().build();
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(1), "wood", 250));
        h.submit(new PlayerSlot(0), new Grant(new FactionSlot(0), "wood", 250));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "gold", 250));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 0));
        List<CommandResult> results = h.step().events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .toList();

        assertThat(results)
                .extracting(CommandResult::outcome)
                .containsExactly(
                        CommandOutcome.ACCEPTED,
                        CommandOutcome.REJECTED,
                        CommandOutcome.REJECTED,
                        CommandOutcome.REJECTED);
        assertThat(results) // the administrator's three apply first, then the player's
                .extracting(CommandResult::firstReason)
                .containsExactly(
                        null, RejectReason.INVALID_ARGUMENT, RejectReason.INVALID_ARGUMENT, RejectReason.NOT_PERMITTED);
        assertThat(h.queries().treasury(new FactionSlot(1)).stock()).containsExactly(0L, 250_000L, 0L, 0L);
        assertThat(h.queries().treasury(new FactionSlot(1)).granted()).containsExactly(0L, 250_000L, 0L, 0L);
        assertThat(h.queries().treasury(new FactionSlot(0)).stock()).containsExactly(0L, 0L, 0L, 0L);
    }

    @Test
    void anUnpayableCommandNamesTheMissingAmounts() {
        CommandEnvelope envelope = new CommandEnvelope(new PlayerSlot(0), 12, 3, new Stop(UnitSet.of(5)));
        CommandResult r = Tally.unaffordable(envelope, CommandKinds.STOP, 1, new long[] {0, 35 * Amount.UNIT, 0, 0});

        assertThat(r.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(r.firstReason()).isEqualTo(RejectReason.NOT_ENOUGH_RESOURCES);
        assertThat(r.rejected()).isEqualTo(1);
        assertThat(r.missing()).containsExactly(0L, 35_000L, 0L, 0L);
        assertThat(Tally.acceptAll(envelope, CommandKinds.STOP, 1).missing()).isEmpty();
    }

    @Test
    void theTreasurySurvivesASnapshotAndAMatchWithoutOneSavesNone() {
        MatchRules rules = MatchRules.DEFAULTS.withEconomy(EconomyRules.DEFAULTS.withStart(300, 200, 0, 0));
        SimHarness h = SimHarness.builder().rules(rules).build();
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "stone", 40));
        h.step();
        MatchSnapshot snapshot = h.core().snapshot();
        MatchSnapshot decoded = MatchSnapshotCodec.decode(
                        new Dynamic<>(JsonOps.INSTANCE, MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot)),
                        Migrations.production())
                .getOrThrow();
        assertThat(decoded).isEqualTo(snapshot);
        MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(restored.queries().treasury(new FactionSlot(0)))
                .isEqualTo(h.queries().treasury(new FactionSlot(0)));
        assertThat(restored.queries().treasury(new FactionSlot(0)).stock())
                .containsExactly(300_000L, 200_000L, 40_000L, 0L);
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());

        SimHarness bare = SimHarness.builder().build();
        bare.step();
        assertThat(MatchSnapshotCodec.encode(JsonOps.INSTANCE, bare.core().snapshot())
                        .getAsJsonObject()
                        .has("treasury"))
                .isFalse();
    }
}
