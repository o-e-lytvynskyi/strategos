package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;

import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.payload.PingPayload;
import dev.strategos.mod.server.net.payload.TeamPingPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;

public final class TeamPingTests {
    private TeamPingTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void teamPingsReachOnlyCurrentPrimaryAlliesAtTheExactCooldown(GameTestHelper h) {
        var options = new MatchOptions(
                SimHarness.DEFAULT_SEED,
                4,
                false,
                MatchSource.GAMETEST,
                null,
                MetricsSink.none(),
                Math.floor(h.getBounds().minY) + 1,
                null,
                PlayRules.DEFAULTS);
        var kit = MultiplayerHarness.create(h, options);
        var match = kit.match();
        var server = StrategosServer.get();
        var f0 = new FactionSlot(0);
        var p0 = new PlayerSlot(0);
        var p1 = new PlayerSlot(1);
        var p2 = new PlayerSlot(2);
        var p3 = new PlayerSlot(3);
        match.initializeDiplomacy(new DiplomacySetup(DiplomacyState.identity(4)
                .withTeam(new FactionSlot(1), 0)
                .bind(p1, f0)
                .bind(p2, new FactionSlot(1))
                .lockTeams()
                .snapshot()));
        var at = h.absolutePos(new BlockPos(12, 20, 12));
        var observer = MockViewer.join(h.getLevel(), "ping-observer", at.getX(), at.getY(), at.getZ(), 2);
        h.onEachTick(observer::poll);
        h.startSequence()
                .thenWaitUntil(() -> check(kit.ready(), "ping fixture not ready"))
                .thenExecute(() -> {
                    kit.admit("teamping", List.of(p0, p1, p2, p3));
                    server.matches().join(kit.match(), observer.player().getUUID(), PlayerSlot.ADMIN);
                    server.net().joined(kit.match(), observer.player(), PlayerSlot.ADMIN);
                    server.net().flush();
                    observer.poll();
                    observer.send(new PingPayload(0, 5, 7));
                    var sender = kit.viewer(0);
                    sender.send(new PingPayload(0, -1, 7));
                    sender.send(new PingPayload(Long.MAX_VALUE, 2, 7));
                    sender.send(new PingPayload(0, 6, 7));
                    sender.send(new PingPayload(0, 9, 7));
                    server.net().flush();
                    assertDelivery(kit, observer, 1, 6);
                })
                .thenExecuteAfter(39, () -> {
                    kit.viewer(0).send(new PingPayload(0, 10, 7));
                    server.net().flush();
                    assertDelivery(kit, observer, 1, 6);
                })
                .thenExecuteAfter(1, () -> {
                    kit.viewer(0).send(new PingPayload(0, 11, 7));
                    server.net().flush();
                    assertDelivery(kit, observer, 2, 11);
                })
                .thenExecute(observer::close)
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static void assertDelivery(MultiplayerHarness kit, MockViewer observer, int count, int finalX) {
        for (int i = 0; i < 3; i++) {
            var pings = kit.payloads(i, TeamPingPayload.class);
            check(pings.size() == count, "sender, co-op or allied delivery count wrong");
            var last = pings.getLast();
            check(last.epoch() == kit.viewer(i).epoch(), "recipient epoch was not used");
            check(last.ping().matchId() == kit.match().id().value(), "wrong ping match");
            check(
                    last.ping().player().value() == 0
                            && last.ping().x() == finalX
                            && last.ping().z() == 7,
                    "ping sender or coordinates changed");
        }
        check(kit.payloads(3, TeamPingPayload.class).isEmpty(), "ping reached an opponent");
        observer.poll();
        check(
                observer.capture().of(ClientboundCustomPayloadPacket.class).stream()
                        .noneMatch(packet -> packet.payload() instanceof TeamPingPayload),
                "ping reached an observer");
    }
}
