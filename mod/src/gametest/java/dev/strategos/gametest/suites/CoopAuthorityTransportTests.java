package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** Actual co-op commands from a camera and first-person peer; player identity never becomes faction identity. */
public final class CoopAuthorityTransportTests {
    private CoopAuthorityTransportTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void bothPrimariesCommandAndRefundTheSameFactionWhileFirstPersonKeepsItsPlayerLeader(
            GameTestHelper h) {
        var kit = MultiplayerHarness.create(
                h,
                new MatchOptions(
                        SimHarness.DEFAULT_SEED,
                        4,
                        false,
                        MatchSource.GAMETEST,
                        null,
                        MetricsSink.none(),
                        Math.floor(h.getBounds().minY) + 1,
                        null,
                        MatchOptions.GAMETEST_PLAY));
        var match = kit.match();
        var own = new FactionSlot(0);
        var other = new FactionSlot(1);
        var diplomacy = DiplomacyState.identity(4)
                .withTeam(other, 0)
                .bind(new PlayerSlot(1), own)
                .bind(new PlayerSlot(2), other)
                .bind(new PlayerSlot(3), new FactionSlot(2))
                .lockTeams();
        match.initializeDiplomacy(new DiplomacySetup(diplomacy.snapshot()));
        match.submit(PlayerSlot.ADMIN, new Grant(own, "food", 200_000));
        match.submit(PlayerSlot.ADMIN, new Grant(other, "food", 777_000));
        var centre = new TemplateId("strategos:building/civic_centre");
        match.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, own, 4, 4, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, other, 26, 26, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, own, new Vec2d(12, 12), 2, 1));
        var state = new Object() {
            int first;
            int second;
            int building;
            int food;
            long before;
            long debited;
            long foreign;
        };
        var commanders = StrategosServer.get().commanders();
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready()
                                && core(match).queries().unitHandlesOf(own).length == 2
                                && core(match).queries().buildings().size() == 2,
                        "co-op command fixture not ready"))
                .thenExecute(() -> {
                    int[] handles = core(match).queries().unitHandlesOf(own);
                    state.first = handles[0];
                    state.second = handles[1];
                    state.building = core(match).queries().buildings().stream()
                            .filter(b -> b.faction() == 0)
                            .findFirst()
                            .orElseThrow()
                            .handle();
                    state.food = core(match).queries().config().rules().economy().resources().stream()
                            .map(resource -> resource.id())
                            .toList()
                            .indexOf("food");
                    check(state.food >= 0, "food is absent from the fixture rules");
                    state.before = core(match).queries().treasury(own).stock().get(state.food);
                    state.foreign =
                            core(match).queries().treasury(other).stock().get(state.food);
                    kit.admit(
                            "coauth",
                            List.of(new PlayerSlot(0), new PlayerSlot(1), new PlayerSlot(2), new PlayerSlot(3)));
                    for (int i = 0; i < 4; i++) commanders.enter(kit.viewer(i).player(), match);
                    kit.viewer(1)
                            .send(new ModeSwitchPayload(new ModeSwitch(
                                    true,
                                    match.space().worldX(14.5),
                                    match.space().worldZ(14.5))));
                })
                .thenWaitUntil(() ->
                        check(commanders.observes(kit.viewer(1).player()), "co-op peer could not enter first person"))
                .thenExecute(() -> {
                    kit.sendCommands(0, new CommandBatch(1, List.of(new Train(state.building, TemplateSet.WORKER))));
                    kit.sendCommands(1, new CommandBatch(1, List.of(Follow.replace(UnitSet.of(state.first)))));
                })
                .thenWaitUntil(() ->
                        check(accepted(kit, 0, 1) && accepted(kit, 1, 1), "both primary commands were not accepted"))
                .thenExecute(() -> {
                    state.debited = core(match).queries().treasury(own).stock().get(state.food);
                    check(state.debited < state.before, "camera training did not charge the primary owner");
                    check(
                            core(match).queries().treasury(other).stock().get(state.food) == state.foreign,
                            "co-op training charged a foreign faction");
                    var orders = core(match).queries().orders(state.first);
                    check(
                            !orders.isEmpty() && orders.getFirst().target() == 1,
                            "first-person follow used the faction number instead of player slot 1");
                    kit.sendCommands(1, new CommandBatch(2, List.of(new CancelTrain(state.building, 0))));
                    kit.sendCommands(
                            0, new CommandBatch(2, List.of(Move.replace(UnitSet.of(state.second), new Vec2d(22, 16)))));
                })
                .thenWaitUntil(() -> check(
                        accepted(kit, 0, 2) && accepted(kit, 1, 2), "co-op cancellation or camera move was refused"))
                .thenExecute(() -> {
                    var paid = core(match).queries().treasury(own);
                    check(
                            paid.stock().get(state.food) > state.debited
                                    && paid.stock().get(state.food) <= state.before,
                            "first-person cancellation did not refund the original owner");
                    check(paid.refunded().get(state.food) > 0, "owner refund was not recorded");
                    check(
                            core(match).queries().treasury(other).stock().get(state.food) == state.foreign
                                    && core(match)
                                                    .queries()
                                                    .treasury(other)
                                                    .refunded()
                                                    .get(state.food)
                                            == 0,
                            "a foreign faction received the co-op refund");
                    check(
                            core(match).queries().buildings().stream()
                                    .filter(b -> b.handle() == state.building)
                                    .findFirst()
                                    .orElseThrow()
                                    .queue()
                                    .isEmpty(),
                            "cancelled primary queue is not empty");
                    kit.close();
                })
                .thenSucceed();
    }

    private static boolean accepted(MultiplayerHarness kit, int player, int sequence) {
        for (var payload : kit.payloads(player, FeedbackPayload.class)) {
            if (payload.message() instanceof Feedback feedback && feedback.clientSeq() == sequence) {
                check(
                        feedback.outcome() == CommandOutcome.ACCEPTED,
                        "player " + player + " sequence " + sequence + " refused: " + feedback);
                return true;
            }
        }
        return false;
    }
}
