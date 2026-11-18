package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/** T8.1: an issuer is a player, assets/costs/fog belong to factions, and grants are checked at application. */
class CoopCommandAuthorityTest {
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final PlayerSlot P2 = new PlayerSlot(2);
    private static final PlayerSlot P3 = new PlayerSlot(3);
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final FactionSlot F2 = new FactionSlot(2);
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    private record Scene(SimHarness h, MatchState state, int own, int enemy, int centre) {
        CommandResult command(PlayerSlot issuer, Command command) {
            h.submit(issuer, command);
            h.step();
            return h.results().getLast();
        }
    }

    private static Scene scene(boolean grant, boolean reveal) {
        int size = reveal ? 96 : 256;
        int far = reveal ? 60 : 200;
        SimHarness h = SimHarness.builder()
                .size(size, size)
                .yBand(0, 31, 1)
                .factions(3)
                .rules(MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.NONE, reveal, reveal, false)))
                .build();
        h.publishAll(VoxelFixtures.flat(size, size, 32, 1));
        DiplomacyState authority = DiplomacyState.identity(3)
                .withTeam(F1, 0)
                .bind(P1, F0)
                .bind(P2, F1)
                .bind(P3, F2)
                .lockTeams();
        if (grant) authority = authority.grant(P0, P2);
        h.schedule(1, new DiplomacySetup(authority.snapshot()));
        for (int f = 0; f < 3; f++) {
            FactionSlot faction = new FactionSlot(f);
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, faction, f == 0 ? 4 : far, f == 1 ? far : 4, 0));
            h.submit(PlayerSlot.ADMIN, new Grant(faction, "food", 1_000));
            h.submit(PlayerSlot.ADMIN, new Grant(faction, "wood", 1_000));
        }
        h.runTicks(2);
        int own = h.spawn(F0, new Vec2d(18.5, 8.5), 1, 0)[0];
        h.spawn(F1, new Vec2d(far + 18.5, far + 18.5), 1, 0);
        int enemy = h.spawn(F2, reveal ? new Vec2d(far + 18.5, 18.5) : new Vec2d(24.5, 8.5), 1, 0)[0];
        MatchState state = KernelAccess.state(h.core());
        int centre = -1;
        for (int building : state.buildings().handles())
            if (state.buildings().faction(building) == 0) centre = building;
        assertThat(centre).isNotEqualTo(-1);
        return new Scene(h, state, own, enemy, centre);
    }

    private static Move move(int handle, double x) {
        return Move.replace(UnitSet.of(handle), new Vec2d(x, 18.5));
    }

    @Test
    void bothCoopAndASeatBeyondFactionCountCommandTheirPrimaryUnits() {
        Scene s = scene(false, true);
        assertThat(s.command(P0, move(s.own(), 28.5)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.command(P1, move(s.own(), 30.5)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.command(P3, move(s.enemy(), 72.5)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.state().identity().faction(Handle.index(s.own()))).isZero();
    }

    @Test
    void aGrantedAllyCanCommandButAnotherVisibleFactionCannot() {
        Scene s = scene(true, true);
        assertThat(s.command(P2, move(s.own(), 30.5)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.command(P3, move(s.own(), 32.5)).firstReason()).isEqualTo(RejectReason.NOT_OWNER);
    }

    @Test
    void queuedCommandCannotRetainAuthorityRevokedBeforeApplication() {
        Scene s = scene(true, true);
        assertThat(s.command(P2, move(s.own(), 28.5)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        s.h().submit(P2, move(s.own(), 40.5));
        s.state().restoreDiplomacy(s.state().diplomacy().revoke(P0, P2));
        s.h().step();
        assertThat(s.h().results().getLast().firstReason()).isEqualTo(RejectReason.NOT_OWNER);
    }

    @Test
    void grantingControlDoesNotRevealAnArmyAndHiddenMatchesMissing() {
        Scene s = scene(true, false);
        assertThat(s.state().visionSystem()).isNotNull();
        assertThat(java.util.Objects.requireNonNull(s.state().visionSystem())
                        .visibility(1)
                        .isVisible(s.own()))
                .isFalse();
        assertThat(java.util.Objects.requireNonNull(s.state().visionSystem())
                        .visibility(2)
                        .isVisible(s.own()))
                .isTrue();
        RejectReason hidden = s.command(P2, move(s.own(), 30.5)).firstReason();
        RejectReason missing = s.command(P2, move(Handle.of(1_000, 1), 30.5)).firstReason();
        assertThat(hidden).isEqualTo(RejectReason.UNKNOWN_TARGET).isEqualTo(missing);
    }

    @Test
    void twoControllersPayTheAssetOwnerExactlyOncePerTrainingCommand() {
        Scene s = scene(false, true);
        long food = s.state().treasury().stock(0, 0);
        long other = s.state().treasury().stock(1, 0);
        s.h().submit(P0, new Train(s.centre(), TemplateSet.WORKER));
        s.h().submit(P1, new Train(s.centre(), TemplateSet.WORKER));
        var commands = s.h().step().events().stream()
                .filter(CommandResult.class::isInstance)
                .map(CommandResult.class::cast)
                .toList();
        assertThat(commands).hasSize(2).allSatisfy(c -> assertThat(c.outcome()).isEqualTo(CommandOutcome.ACCEPTED));
        assertThat(s.state().buildings().queueSize(s.centre())).isEqualTo(2);
        assertThat(s.state().treasury().stock(0, 0)).isEqualTo(food - 120_000);
        assertThat(s.state().treasury().stock(1, 0)).isEqualTo(other);
    }

    @Test
    void grantedTrainingAndCoopCancellationChargeAndRefundTheOwnerNotTheIssuer() {
        Scene s = scene(true, true);
        long food = s.state().treasury().stock(0, 0);
        long other = s.state().treasury().stock(1, 0);
        assertThat(s.command(P2, new Train(s.centre(), TemplateSet.WORKER)).outcome())
                .isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.state().treasury().stock(0, 0)).isEqualTo(food - 60_000);
        assertThat(s.state().treasury().stock(1, 0)).isEqualTo(other);
        assertThat(s.command(P1, new CancelTrain(s.centre(), 0)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(s.state().treasury().stock(0, 0)).isEqualTo(food);
        assertThat(s.state().treasury().stock(1, 0)).isEqualTo(other);
    }

    @Test
    void placementCreatesAndChargesThePrimaryFactionOfAHighNumberedSeat() {
        Scene s = scene(false, true);
        long wood0 = s.state().treasury().stock(0, 1);
        long wood1 = s.state().treasury().stock(1, 1);
        long wood2 = s.state().treasury().stock(2, 1);
        assertThat(s.command(P3, new PlaceBuilding(HOUSE, 60, 40, 0, UnitSet.of(), QueueMode.REPLACE))
                        .outcome())
                .isEqualTo(CommandOutcome.ACCEPTED);
        int houses = 0;
        for (int building : s.state().buildings().handles()) {
            if (s.state()
                    .templates()
                    .byIndex(s.state().buildings().template(building))
                    .id()
                    .equals(HOUSE)) {
                assertThat(s.state().buildings().faction(building)).isEqualTo(2);
                houses++;
            }
        }
        assertThat(houses).isEqualTo(1);
        assertThat(s.state().treasury().stock(2, 1)).isLessThan(wood2);
        assertThat(s.state().treasury().stock(0, 1)).isEqualTo(wood0);
        assertThat(s.state().treasury().stock(1, 1)).isEqualTo(wood1);
    }
}
