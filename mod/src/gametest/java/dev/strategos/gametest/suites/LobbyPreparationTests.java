package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.match.LobbyPreparation;
import dev.strategos.mod.server.match.LobbyService;
import dev.strategos.mod.server.match.MatchLobby;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.match.Skirmishes;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Actual M6 import/setup/admission and rollback; private lobby intents remain a separate protocol gate. */
public final class LobbyPreparationTests {
    private LobbyPreparationTests() {}

    private static Skirmishes.Placer placer(GameTestHelper h, int lane) {
        return (map, options) -> {
            var manifest = MapPackageLoader.load(h.getLevel().getServer(), map).manifest();
            BlockPos corner = h.absolutePos(BlockPos.ZERO);
            BlockPos origin = new BlockPos(corner.getX() + lane * 2048, corner.getY(), corner.getZ());
            return MatchFixture.create(
                    h,
                    new MatchSpace(
                            h.getLevel().dimension(),
                            origin,
                            manifest.sizeX(),
                            manifest.sizeZ(),
                            origin.getY(),
                            origin.getY() + manifest.height() - 1),
                    MatchFixture.options(SimHarness.DEFAULT_SEED, h)
                            .withMap(
                                    map,
                                    origin.getY() + manifest.startSlots().get(0).y())
                            .withPlay(options.play()));
        };
    }

    private static MatchLobby ready(UUID host, UUID peer, String civilisation) {
        MatchLobby lobby = MatchLobby.create(
                2,
                List.of(
                        new MatchLobby.Seat(
                                new PlayerSlot(0), MatchLobby.Kind.HUMAN, host, new FactionSlot(0), 0, civilisation, 0),
                        new MatchLobby.Seat(
                                new PlayerSlot(1),
                                MatchLobby.Kind.HUMAN,
                                peer,
                                new FactionSlot(1),
                                1,
                                civilisation,
                                1)));
        return lobby.withReady(host, true, 0).withReady(peer, true, 0).prepare();
    }

    @StrategosTest(batch = "map_lobby_start", solo = true, timeoutTicks = 40000)
    public static void realImportInitializesBothHumansBeforeCommanderAdmission(GameTestHelper h) {
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        MockViewer host =
                MockViewer.join(h.getLevel(), "stg-lobby-host", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        MockViewer peer =
                MockViewer.join(h.getLevel(), "stg-lobby-peer", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        MockViewer outsider =
                MockViewer.join(h.getLevel(), "stg-lobby-out", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        StrategosServer server = StrategosServer.get();
        LobbyPreparation preparation = LobbyPreparation.start(
                server,
                ready(host.player().getUUID(), peer.player().getUUID(), "strategos:placeholder"),
                SkirmishSetup.DEFAULTS,
                placer(h, 21));
        var rt = preparation.match();
        check(rt.lifecycle() == MatchLifecycle.PREPARING, "map did not enter preparation");
        boolean refused = false;
        try {
            rt.submit(new PlayerSlot(0), new dev.strategos.core.protocol.RuleChange("populationMax", "200"));
        } catch (dev.strategos.mod.server.match.MatchRefusedException expected) {
            refused = true;
        }
        check(refused, "a player submitted commands during import");
        check(server.matches().seatOf(host.player().getUUID()) == null, "host admitted before import/setup");
        h.onEachTick(() -> {
            var status = preparation.poll();
            check(status != LobbyService.Preparation.Status.FAILED, "lobby preparation failed");
            host.poll();
            peer.poll();
            outsider.poll();
            if (status != LobbyService.Preparation.Status.COMPLETE) return;
            var hostSeat = server.matches().seatOf(host.player().getUUID());
            var peerSeat = server.matches().seatOf(peer.player().getUUID());
            check(hostSeat != null && hostSeat.slot().value() == 0, "wrong host seat");
            check(peerSeat != null && peerSeat.slot().value() == 1, "wrong peer seat");
            check(
                    server.commanders().commands(host.player())
                            && server.commanders().commands(peer.player()),
                    "not commanders");
            check(core(rt).queries().diplomacy().teamsLocked(), "authority not installed");
            if (host.capture().counts().getOrDefault("payload/strategos:match_join", 0) == 0
                    || peer.capture().counts().getOrDefault("payload/strategos:match_join", 0) == 0) return;
            for (String type : List.of("unit_summary", "faction_state", "building_state")) {
                check(
                        host.capture().counts().getOrDefault("payload/strategos:" + type, 0) > 0,
                        "host missing initial " + type);
                check(
                        peer.capture().counts().getOrDefault("payload/strategos:" + type, 0) > 0,
                        "peer missing initial " + type);
                check(
                        outsider.capture().counts().getOrDefault("payload/strategos:" + type, 0) == 0,
                        "outsider received " + type);
            }
            check(
                    outsider.capture().counts().getOrDefault("payload/strategos:match_join", 0) == 0,
                    "outsider received match_join");
            h.succeed();
        });
    }

    @StrategosTest(solo = true, timeoutTicks = 100)
    public static void invalidPreflightDoesNotInvokeTheWorldAllocator(GameTestHelper h) {
        int[] allocations = {0};
        boolean refused = false;
        try {
            LobbyPreparation.start(
                    StrategosServer.get(),
                    ready(new UUID(7, 201), new UUID(7, 202), "strategos:missing"),
                    SkirmishSetup.DEFAULTS,
                    (map, options) -> {
                        allocations[0]++;
                        throw new AssertionError("must not allocate");
                    });
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused && allocations[0] == 0, "invalid civ reached world allocation");
        h.succeed();
    }

    @StrategosTest(batch = "map_lobby_cancel", solo = true, timeoutTicks = 100)
    public static void cancellationDropsThePartialRuntimeAndItsQueuedImport(GameTestHelper h) {
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        MockViewer host =
                MockViewer.join(h.getLevel(), "stg-lobby-ca", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        MockViewer peer =
                MockViewer.join(h.getLevel(), "stg-lobby-cb", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        LobbyPreparation preparation = LobbyPreparation.start(
                StrategosServer.get(),
                ready(host.player().getUUID(), peer.player().getUUID(), "strategos:placeholder"),
                SkirmishSetup.DEFAULTS,
                placer(h, 22));
        var rt = preparation.match();
        preparation.cancel();
        check(rt.lifecycle() == MatchLifecycle.DISPOSED, "partial runtime not disposed");
        check(
                StrategosServer.get().matches().all().stream().noneMatch(m -> m.id().equals(rt.id())),
                "partial runtime retained");
        check(rt.roster().isEmpty(), "partial roster retained");
        h.succeed();
    }
}
