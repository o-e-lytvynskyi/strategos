package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** QA-15 engine kit: real acknowledging connections, private captures, actual counters and bounded owned cleanup. */
public final class MultiplayerKitTests {
    private MultiplayerKitTests() {}

    private static MatchOptions options(GameTestHelper h) {
        return new MatchOptions(
                SimHarness.DEFAULT_SEED,
                4,
                false,
                MatchSource.GAMETEST,
                null,
                MetricsSink.none(),
                Math.floor(h.getBounds().minY) + 1,
                null,
                MatchOptions.GAMETEST_PLAY);
    }

    /** T6.2: valid commands before/after malformed frames prove the actual connection and handler remain usable. */
    @StrategosTest
    public static void boundedHostileFramesUseRealIngressAndOnlyValidCommandsReachTheJournal(GameTestHelper h) {
        MultiplayerHarness kit = MultiplayerHarness.create(h, options(h));
        kit.match().submit(PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8.5, 8.5), 1, 0));
        var counters = StrategosServer.get().net().counters();
        int[] violations = {0};
        h.startSequence()
                .thenWaitUntil(() -> check(kit.ready() && core(kit.match()).queries().unitHandles().length == 1,
                        "command fixture is not ready"))
                .thenExecute(() -> {
                    kit.admit("m7-wire", List.of(new PlayerSlot(0)));
                    kit.sendCommands(0, move(kit, 1));
                })
                .thenWaitUntil(() -> check(accepted(kit, 1), "valid command before hostile traffic was not accepted"))
                .thenExecute(() -> {
                    String name = kit.viewer(0).player().getGameProfile().getName();
                    violations[0] = counters.violations(name);
                    kit.sendCommandBytes(0, new byte[dev.strategos.core.protocol.Limits.BATCH_BYTES + 1]);
                    kit.sendCommandBytes(0, new byte[] {0, 1, (byte) 255});
                    kit.sendCommands(0, move(kit, 2));
                })
                .thenWaitUntil(() -> check(accepted(kit, 2), "valid command after hostile frames was not accepted"))
                .thenExecute(() -> kit.sendPacket(0,
                        new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                                dev.strategos.mod.server.net.payload.CommandsPayload.of(move(kit, 3)))))
                .thenWaitUntil(() -> check(accepted(kit, 3), "generic packet route bypassed or missed real ingress"))
                .thenExecute(() -> {
                    var commands = NetFixtures.envelopes(kit.match(), 0);
                    check(commands.size() == 3 && commands.get(0).clientSeq() == 1
                            && commands.get(1).clientSeq() == 2 && commands.get(2).clientSeq() == 3,
                            "malformed traffic changed the journal: " + commands);
                    String name = kit.viewer(0).player().getGameProfile().getName();
                    check(counters.violations(name) == violations[0] + 2, "hostile frames did not traverse the violation policy");
                    check(!kit.viewer(0).disconnected(), "bounded hostile probes disconnected a valid player");
                    var retained = move(kit, 4);
                    kit.close();
                    boolean refused = false;
                    try {
                        kit.sendCommands(0, retained);
                    } catch (IllegalStateException expected) {
                        refused = true;
                    }
                    check(refused, "closed harness accepted another command");
                })
                .thenSucceed();
    }

    private static dev.strategos.core.protocol.CommandBatch move(MultiplayerHarness kit, int sequence) {
        int handle = core(kit.match()).queries().unitHandles()[0];
        return new dev.strategos.core.protocol.CommandBatch(sequence, List.of(
                dev.strategos.core.protocol.Move.replace(dev.strategos.core.protocol.UnitSet.of(handle), new Vec2d(20.5, 20.5))));
    }

    private static boolean accepted(MultiplayerHarness kit, int sequence) {
        return kit.payloads(0, dev.strategos.mod.server.net.payload.FeedbackPayload.class).stream()
                .anyMatch(p -> p.message() instanceof dev.strategos.core.protocol.Feedback f
                        && f.clientSeq() == sequence && f.outcome() == dev.strategos.core.protocol.CommandOutcome.ACCEPTED);
    }

    @StrategosTest(timeoutTicks = 400)
    public static void fourPlayersAcknowledgeCapturePrivatelyAndCleanUpWithinThirtySeconds(GameTestHelper h) {
        long started = System.nanoTime();
        MultiplayerHarness kit = MultiplayerHarness.create(h, options(h));
        for (int f = 0; f < 4; f++)
            kit.match()
                    .submit(
                            PlayerSlot.ADMIN,
                            new SpawnUnits(
                                    TemplateSet.DEBUG_WALKER, new FactionSlot(f), new Vec2d(5 + f * 6, 8), 1, 0));
        var at = h.absolutePos(new BlockPos(8, 20, 8));
        MockViewer unrelated = MockViewer.join(h.getLevel(), "m7-kit-unrelated", at.getX(), at.getY(), at.getZ(), 2);
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready() && core(kit.match()).queries().unitHandles().length == 4,
                        "kit's match is not ready"))
                .thenExecute(() -> kit.admit(
                        "m7-kit", List.of(new PlayerSlot(0), new PlayerSlot(1), new PlayerSlot(2), new PlayerSlot(3))))
                .thenWaitUntil(() -> {
                    check(kit.viewers().size() == 4, "not four players");
                    for (int i = 0; i < 4; i++) {
                        check(kit.viewer(i).batches() > 0, "player did not acknowledge chunks: " + i);
                        check(!kit.payloads(i, FactionStatePayload.class).isEmpty(), "no private state: " + i);
                    }
                })
                .thenExecute(() -> {
                    for (int i = 0; i < 4; i++) {
                        for (var state : kit.payloads(i, FactionStatePayload.class))
                            check(
                                    state.state().matchId() == kit.match().id().value()
                                            && state.state().faction() == i,
                                    "another player's private faction state reached " + i);
                        final int seat = i;
                        check(
                                kit.payloads(i, MatchJoinPayload.class).stream()
                                        .anyMatch(p -> p.join().slot().value() == seat),
                                "wrong admission slot");
                        check(kit.viewer(i).bytes().ours() > 0, "no counted custom payloads");
                        check(
                                kit.viewer(i).bytes().bytes()
                                        > kit.viewer(i).bytes().ours(),
                                "no counted vanilla bytes");
                        check(kit.viewer(i).bytes().failures().isEmpty(), "packet counter could not encode a packet");
                    }
                    var ids = kit.viewers().stream()
                            .map(v -> v.player().getUUID())
                            .toList();
                    kit.close();
                    kit.close();
                    check(
                            kit.match().lifecycle() == dev.strategos.core.sim.MatchLifecycle.DISPOSED,
                            "owned match survived close");
                    for (var id : ids) {
                        check(
                                h.getLevel().getServer().getPlayerList().getPlayer(id) == null,
                                "owned mock player leaked");
                        check(StrategosServer.get().matches().matchOf(id) == 0, "owned membership index leaked");
                    }
                    check(
                            h.getLevel()
                                            .getServer()
                                            .getPlayerList()
                                            .getPlayer(unrelated.player().getUUID())
                                    == unrelated.player(),
                            "closing the group removed an unrelated player");
                    unrelated.close();
                    check(System.nanoTime() - started < 30_000_000_000L, "four-player smoke exceeded 30 seconds");
                })
                .thenSucceed();
    }

    @StrategosTest
    public static void invalidAdmissionIsAtomicAndAValidAdmissionStillWorks(GameTestHelper h) {
        MultiplayerHarness kit = MultiplayerHarness.create(h, options(h));
        h.startSequence()
                .thenWaitUntil(() -> check(kit.ready(), "kit is not ready"))
                .thenExecute(() -> {
                    boolean refused = false;
                    try {
                        kit.admit("m7-dupe", List.of(new PlayerSlot(0), new PlayerSlot(0)));
                    } catch (IllegalArgumentException expected) {
                        refused = true;
                    }
                    check(
                            refused
                                    && kit.viewers().isEmpty()
                                    && kit.match().roster().members().isEmpty(),
                            "invalid group partially admitted players");
                    kit.admit("m7-valid", List.of(new PlayerSlot(0), new PlayerSlot(1)));
                    check(kit.viewers().size() == 2, "valid group could not follow refused admission");
                    kit.close();
                })
                .thenSucceed();
    }
}
