package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;

import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.VisionDelta;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.VisionDeltaPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** T9.1: private markers use the real queued gateway and separate acknowledging connections. */
public final class RecipientQueueTests {
    private RecipientQueueTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void queuedPrivateFramesCannotSurviveReseatReconnectOrObserverRoleChange(GameTestHelper h) {
        var options = new MatchOptions(
                SimHarness.DEFAULT_SEED,
                2,
                false,
                MatchSource.GAMETEST,
                null,
                MetricsSink.none(),
                Math.floor(h.getBounds().minY) + 1,
                null,
                MatchOptions.GAMETEST_PLAY);
        MultiplayerHarness kit = MultiplayerHarness.create(h, options);
        var rt = kit.match();
        var server = StrategosServer.get();
        h.startSequence()
                .thenWaitUntil(() -> check(kit.ready(), "fixture not ready"))
                .thenExecute(() -> kit.admit("epoch", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenWaitUntil(() -> check(
                        !kit.payloads(0, MatchJoinPayload.class).isEmpty()
                                && !kit.payloads(1, MatchJoinPayload.class).isEmpty(),
                        "sequential admissions lost an initial frame"))
                .thenExecute(() -> {
                    marker(kit, 1, 101);
                    marker(kit, 0, 102);
                    server.matches().join(rt, kit.viewer(0).player().getUUID(), new PlayerSlot(0));
                    marker(kit, 0, 103);
                })
                .thenWaitUntil(() -> check(
                        received(kit, 0, 103) && received(kit, 1, 101),
                        "current or unrelated recipient lost a positive marker"))
                .thenExecute(() -> {
                    check(!received(kit, 0, 102), "private frame survived a new seat generation");
                    marker(kit, 0, 104);
                    server.matches().connection(kit.viewer(0).player().getUUID(), false);
                    server.matches().connection(kit.viewer(0).player().getUUID(), true);
                    marker(kit, 0, 105);
                })
                .thenWaitUntil(() -> check(received(kit, 0, 105), "reconnected recipient missed a current marker"))
                .thenExecute(() -> {
                    check(!received(kit, 0, 104), "private frame survived disconnect/reconnect");
                    marker(kit, 0, 106);
                    server.matches().join(rt, kit.viewer(0).player().getUUID(), PlayerSlot.ADMIN);
                    marker(kit, 0, 107);
                })
                .thenWaitUntil(() -> check(received(kit, 0, 107), "explicit observer missed a current marker"))
                .thenExecute(() -> {
                    check(!received(kit, 0, 106), "former primary frame survived an observer role change");
                    check(
                            !received(kit, 1, 102) && !received(kit, 1, 104) && !received(kit, 1, 106),
                            "issuer-only frame escaped to another participant");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static void marker(MultiplayerHarness kit, int player, long marker) {
        StrategosServer.get()
                .net()
                .send(
                        new Audience.Player(kit.viewer(player).player()),
                        new VisionDeltaPayload(new VisionDelta(
                                kit.match().id().value(),
                                1_000_000_000L + marker,
                                1,
                                1,
                                true,
                                new int[] {0},
                                new int[] {0})));
    }

    private static boolean received(MultiplayerHarness kit, int player, long marker) {
        return kit.payloads(player, VisionDeltaPayload.class).stream()
                .anyMatch(frame -> frame.delta().tick() == 1_000_000_000L + marker);
    }
}
