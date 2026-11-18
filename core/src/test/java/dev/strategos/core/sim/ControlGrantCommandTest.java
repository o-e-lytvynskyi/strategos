package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetControl;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

class ControlGrantCommandTest {
    private static final PlayerSlot P0 = new PlayerSlot(0),
            P1 = new PlayerSlot(1),
            P2 = new PlayerSlot(2),
            P3 = new PlayerSlot(3);
    private static final FactionSlot F0 = new FactionSlot(0), F1 = new FactionSlot(1), F2 = new FactionSlot(2);

    private static SimHarness scene(boolean allies) {
        SimHarness h =
                SimHarness.builder().size(48, 48).yBand(0, 31, 1).factions(3).build();
        h.publishAll(VoxelFixtures.flat(48, 48, 32, 1));
        DiplomacyState authority =
                DiplomacyState.identity(3).bind(P1, F0).bind(P2, F1).bind(P3, F2);
        if (allies) authority = authority.withTeam(F1, 0).withTeam(F2, 0);
        h.schedule(1, new DiplomacySetup(authority.lockTeams().snapshot()));
        h.step();
        return h;
    }

    private static CommandResult command(SimHarness h, PlayerSlot issuer, Command command) {
        h.submit(issuer, command);
        h.step();
        return h.results().getLast();
    }

    @Test
    void eitherPrimaryControllerCanGrantAndRevokeWithoutChangingOwnershipOrBindings() {
        SimHarness h = scene(true);
        var before = h.queries().diplomacy();
        assertThat(command(h, P1, new SetControl(P2, true)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(h.queries().diplomacy().controls(P2, F0)).isTrue();
        assertThat(before.controls(P2, F0)).isFalse();
        assertThat(h.queries().diplomacy().factionOf(P2)).contains(F1);
        assertThat(command(h, P0, new SetControl(P2, false)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(h.queries().diplomacy().controls(P2, F0)).isFalse();
        assertThat(h.queries().diplomacy().controls(P1, F0)).isTrue();
    }

    @Test
    void inheritedControlCannotBeDelegatedTransitively() {
        SimHarness h = scene(true);
        command(h, P0, new SetControl(P2, true));
        assertThat(command(h, P2, new SetControl(P3, true)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(h.queries().diplomacy().controls(P3, F1)).isTrue();
        assertThat(h.queries().diplomacy().controls(P3, F0)).isFalse();
    }

    @Test
    void enemyAbsentAdminAndSameFactionRecipientsAreRefusedWithoutMutation() {
        SimHarness h = scene(false);
        for (PlayerSlot recipient : new PlayerSlot[] {P2, P3, P1, new PlayerSlot(4), PlayerSlot.ADMIN}) {
            var before = h.queries().diplomacy();
            assertThat(command(h, P0, new SetControl(recipient, true)).outcome())
                    .isEqualTo(CommandOutcome.REJECTED);
            assertThat(h.queries().diplomacy()).isSameAs(before);
        }
        assertThat(command(h, PlayerSlot.ADMIN, new SetControl(P2, true)).firstReason())
                .isEqualTo(RejectReason.NOT_PERMITTED);
        assertThat(command(h, new PlayerSlot(4), new SetControl(P2, true)).firstReason())
                .isEqualTo(RejectReason.NOT_PERMITTED);
    }

    @Test
    void revocationBeforeAnAlreadyQueuedOrderIsEffectiveAtApplication() {
        SimHarness h = scene(true);
        int unit = h.spawn(F0, new Vec2d(8.5, 8.5), 1, 0)[0];
        h.spawn(F1, new Vec2d(12.5, 8.5), 1, 0);
        command(h, P0, new SetControl(P2, true));
        h.submit(P1, new SetControl(P2, false));
        h.submit(P2, Move.replace(UnitSet.of(unit), new Vec2d(16.5, 8.5)));
        h.step();
        assertThat(h.results().getLast().firstReason()).isEqualTo(RejectReason.NOT_OWNER);
        assertThat(h.replay().matches()).isTrue();
    }

    @Test
    void grantsRoundTripBinaryJournalAndSnapshotWithIdenticalContinuation() {
        SimHarness h = scene(true);
        command(h, P0, new SetControl(P2, true));
        command(h, P2, new SetControl(P3, true));
        h.runTicks(24);
        var encoded = JournalFormat.encode(new JournalExport(h.core().journal(), h.tick(), h.stateHash()));
        var decoded = JournalFormat.decode(encoded);
        assertThat(JournalFormat.encode(decoded)).isEqualTo(encoded);
        var replay = ReplayDriver.replay(decoded.journal(), TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
        SimHarness restored = SimHarness.restore(h.core().snapshot(), TemplateSet.builtin());
        assertThat(restored.queries().diplomacy().controls(P2, F0)).isTrue();
        command(h, P1, new SetControl(P2, false));
        command(restored, P1, new SetControl(P2, false));
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
        assertThat(restored.queries().diplomacy().controls(P3, F1)).isTrue();
        assertThat(restored.queries().diplomacy().controls(P2, F0)).isFalse();
    }
}
