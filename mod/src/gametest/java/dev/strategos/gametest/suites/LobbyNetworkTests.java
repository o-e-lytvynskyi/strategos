package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.LobbyService;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyReply;
import dev.strategos.mod.server.net.LobbyState;
import dev.strategos.mod.server.net.payload.LobbyActionPayload;
import dev.strategos.mod.server.net.payload.LobbyReplyPayload;
import dev.strategos.mod.server.net.payload.LobbyStatePayload;
import dev.strategos.mod.server.net.payload.SkirmishPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Actual registered ingress and private gateway projection, without world allocation. */
public final class LobbyNetworkTests {
    private LobbyNetworkTests() {}

    private static LobbyConfiguration config(int cap) {
        return new LobbyConfiguration(
                2,
                List.of(
                        new LobbyConfiguration.Seat(0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0),
                        new LobbyConfiguration.Seat(
                                1, LobbyConfiguration.Kind.HUMAN, 1, 1, "strategos:placeholder", 1)),
                new SkirmishSetup(
                        SkirmishSetup.FIRST_SKIRMISH,
                        SkirmishSetup.Level.NORMAL,
                        SkirmishSetup.StartResources.MAP,
                        cap,
                        SkirmishSetup.Visibility.FOG));
    }

    @StrategosTest(batch = "lobby_network", solo = true, timeoutTicks = 100)
    public static void intentsUseConnectionIdentityAndStateGoesOnlyToMembers(GameTestHelper h) {
        BlockPos at = h.absolutePos(BlockPos.ZERO);
        MockViewer host = MockViewer.join(h.getLevel(), "stg-lnet-host", at.getX(), at.getY() + 16, at.getZ(), 2)
                .operator(2);
        MockViewer peer = MockViewer.join(h.getLevel(), "stg-lnet-peer", at.getX(), at.getY() + 16, at.getZ(), 2);
        MockViewer outsider = MockViewer.join(h.getLevel(), "stg-lnet-out", at.getX(), at.getY() + 16, at.getZ(), 2);
        LobbyService service = StrategosServer.get().lobby().service();
        host.send(LobbyActionPayload.of(new LobbyAction.Open(config(150))));
        int[] stage = {0};
        h.onEachTick(() -> {
            host.poll();
            peer.poll();
            outsider.poll();
            var hostStates = NetFixtures.payloads(host, LobbyStatePayload.class);
            var peerStates = NetFixtures.payloads(peer, LobbyStatePayload.class);
            check(NetFixtures.payloads(outsider, LobbyStatePayload.class).isEmpty(), "lobby state leaked to outsider");
            switch (stage[0]) {
                case 0 -> {
                    if (hostStates.isEmpty()) return;
                    check(
                            host.player()
                                    .getUUID()
                                    .equals(service.snapshot()
                                            .lobby()
                                            .seats()
                                            .get(0)
                                            .occupant()),
                            "forged host identity");
                    host.send(new SkirmishPayload(SkirmishSetup.DEFAULTS));
                    peer.send(LobbyActionPayload.of(new LobbyAction.JoinAvailable()));
                    stage[0] = 1;
                }
                case 1 -> {
                    if (peerStates.isEmpty()) return;
                    check(StrategosServer.get().matches().all().isEmpty(), "solo start bypassed the active lobby");
                    long revision = service.snapshot().lobby().configurationRevision();
                    host.send(LobbyActionPayload.of(new LobbyAction.Ready(true, revision)));
                    peer.send(LobbyActionPayload.of(new LobbyAction.Ready(true, revision)));
                    outsider.send(LobbyActionPayload.of(new LobbyAction.Ready(true, revision)));
                    host.send(LobbyActionPayload.of(new LobbyAction.Edit(config(200))));
                    peer.send(LobbyActionPayload.of(new LobbyAction.Ready(true, revision)));
                    stage[0] = 2;
                }
                case 2 -> {
                    if (hostStates.stream()
                                    .noneMatch(p ->
                                            p.state().configuration().setup().populationCap() == 200)
                            || peerStates.stream()
                                    .noneMatch(p ->
                                            p.state().configuration().setup().populationCap() == 200)) return;
                    check(service.snapshot().lobby().readyPlayers().isEmpty(), "stale ready acknowledged new settings");
                    check(
                            hostStates.stream()
                                    .allMatch(p ->
                                            p.state().self() == 0 && p.state().mayConfigure()),
                            "wrong host view");
                    check(
                            peerStates.stream()
                                    .allMatch(p ->
                                            p.state().self() == 1 && !p.state().mayConfigure()),
                            "wrong peer view");
                    check(
                            NetFixtures.payloads(outsider, LobbyReplyPayload.class).stream()
                                    .anyMatch(p -> p.reply() == LobbyReply.REFUSED),
                            "outsider did not receive its own bounded refusal");
                    check(!outsider.disconnected() && !peer.disconnected(), "bounded refusal disconnected client");
                    host.send(LobbyActionPayload.of(new LobbyAction.Leave()));
                    stage[0] = 3;
                }
                case 3 -> {
                    if (hostStates.stream().noneMatch(p -> p.state().phase() == LobbyState.Phase.CLOSED)
                            || peerStates.stream().noneMatch(p -> p.state().phase() == LobbyState.Phase.CLOSED)) return;
                    check(service.snapshot().state() == LobbyService.State.CLOSED, "host leave did not close lobby");
                    h.succeed();
                }
                default -> throw new AssertionError("unknown stage");
            }
        });
    }
}
