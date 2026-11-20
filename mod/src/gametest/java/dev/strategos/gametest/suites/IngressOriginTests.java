package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.C2sHandlers;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyState;
import dev.strategos.mod.server.net.payload.LobbyActionPayload;
import dev.strategos.mod.server.net.payload.LobbyStatePayload;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A valid pending intent from a disconnected player object cannot rebind to a replacement host's UUID. */
public final class IngressOriginTests {
    private IngressOriginTests() {}

    @StrategosTest(batch = "ingress_lobby_origin", solo = true, timeoutTicks = 400)
    public static void aRetainedLobbyContextCannotCancelTheReplacementConnectionsLobby(GameTestHelper h) {
        var server = StrategosServer.get();
        var service = server.lobby().service();
        check(!service.reservesStart(), "lobby service already reserved before the solo fixture");
        MockViewer original = viewer(h);
        IPayloadContext retained = context(original.player());
        var state = new Object() {
            MockViewer active = original;
        };
        h.onEachTick(() -> state.active.poll());
        h.startSequence()
                .thenExecute(() -> {
                    original.close();
                    state.active = viewer(h).operator(2);
                    check(
                            original.player()
                                    .getUUID()
                                    .equals(state.active.player().getUUID()),
                            "replacement changed UUID");
                    state.active.send(LobbyActionPayload.of(new LobbyAction.Open(configuration())));
                })
                .thenWaitUntil(() -> check(
                        service.reservesStart()
                                && service.isHost(state.active.player().getUUID())
                                && NetFixtures.payloads(state.active, LobbyStatePayload.class).stream()
                                        .anyMatch(frame -> frame.state().phase() == LobbyState.Phase.EDITING
                                                && frame.state().mayConfigure()),
                        "fresh connection's host/backend/private-view positive control missing"))
                .thenExecute(() -> C2sHandlers.<LobbyActionPayload>timed(C2sHandlers::lobby)
                        .handle(LobbyActionPayload.of(new LobbyAction.Cancel()), retained))
                .thenExecute(() -> {
                    check(
                            service.reservesStart()
                                    && service.isHost(state.active.player().getUUID()),
                            "predecessor context cancelled the replacement connection's lobby");
                    check(service.snapshot().lobby().readyPlayers().isEmpty(), "new lobby unexpectedly ready");
                    long revision = service.snapshot().lobby().configurationRevision();
                    state.active.send(LobbyActionPayload.of(new LobbyAction.Ready(true, revision)));
                })
                .thenWaitUntil(() -> check(
                        !service.snapshot().lobby().readyPlayers().isEmpty(),
                        "fresh connection lost its legitimate ready intent"))
                .thenExecute(() -> state.active.send(LobbyActionPayload.of(new LobbyAction.Cancel())))
                .thenWaitUntil(() -> check(!service.reservesStart(), "fixture lobby did not close"))
                .thenExecute(() -> {
                    state.active.close();
                    original.close();
                })
                .thenSucceed();
    }

    private static LobbyConfiguration configuration() {
        return new LobbyConfiguration(
                2,
                List.of(
                        new LobbyConfiguration.Seat(0, LobbyConfiguration.Kind.HUMAN, 0, 0, "strategos:placeholder", 0),
                        new LobbyConfiguration.Seat(
                                1, LobbyConfiguration.Kind.HUMAN, 1, 1, "strategos:placeholder", 1)),
                SkirmishSetup.DEFAULTS);
    }

    private static MockViewer viewer(GameTestHelper h) {
        var at = h.absolutePos(new BlockPos(8, 16, 8));
        return MockViewer.join(h.getLevel(), "origin-lobby", at.getX(), at.getY(), at.getZ(), 2);
    }

    private static IPayloadContext context(ServerPlayer player) {
        var listener = player.connection;
        var connection = listener.getConnection();
        return (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) return player;
                    if (method.getName().equals("listener")) return listener;
                    if (method.getName().equals("connection")) return connection;
                    throw new UnsupportedOperationException("unused test context method " + method.getName());
                });
    }
}
