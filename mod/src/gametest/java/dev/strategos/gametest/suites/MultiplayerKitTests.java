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
