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
import dev.strategos.mod.server.match.LobbyBackend;
import dev.strategos.mod.server.match.LobbyPreparation;
import dev.strategos.mod.server.match.LobbyService;
import dev.strategos.mod.server.match.MatchLobby;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.match.ServerLobby;
import dev.strategos.mod.server.match.Skirmishes;
import dev.strategos.mod.server.net.LobbyState;
import dev.strategos.mod.server.net.payload.LobbyStatePayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Actual M6 import/setup/admission and rollback; private lobby intents remain a separate protocol gate. */
public final class LobbyPreparationTests {
    private LobbyPreparationTests() {}

    private static Skirmishes.Placer placer(GameTestHelper h, int lane) {
        return (map, options) -> {
            var manifest = MapPackageLoader.manifest(h.getLevel().getServer(), map);
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
                    new MatchOptions(
                            SimHarness.DEFAULT_SEED,
                            options.factions(),
                            false,
                            MatchSource.GAMETEST,
                            null,
                            options.metrics(),
                            origin.getY() + manifest.startSlots().get(0).y(),
                            map,
                            options.play()));
        };
    }

    private static MatchLobby configured(UUID host, @org.jspecify.annotations.Nullable UUID peer, String civilisation) {
        return MatchLobby.create(
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
    }

    private static MatchLobby ready(UUID host, UUID peer, String civilisation) {
        return configured(host, peer, civilisation)
                .withReady(host, true, 0)
                .withReady(peer, true, 0)
                .prepare();
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
        host.operator(2);
        StrategosServer server = StrategosServer.get();
        MatchRuntime[] allocated = new MatchRuntime[1];
        Skirmishes.Placer actual = placer(h, 21);
        ServerLobby facade = new ServerLobby(server, (map, options) -> {
            MatchRuntime rt = actual.place(map, options);
            allocated[0] = rt;
            return rt;
        });
        LobbyService service = facade.service();
        UUID hostId = host.player().getUUID();
        UUID peerId = peer.player().getUUID();
        service.open(
                hostId, 2, configured(hostId, null, "strategos:placeholder").seats(), SkirmishSetup.DEFAULTS);
        service.join(peerId, new PlayerSlot(1));
        long revision = service.snapshot().lobby().configurationRevision();
        service.ready(hostId, true, revision);
        service.ready(peerId, true, revision);
        service.start(hostId);
        var rt = java.util.Objects.requireNonNull(allocated[0]);
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
            service.tick();
            var status = service.snapshot().state();
            check(
                    status == LobbyService.State.PREPARING || status == LobbyService.State.STARTED,
                    "lobby preparation failed: " + service.snapshot().message());
            host.poll();
            peer.poll();
            outsider.poll();
            if (status != LobbyService.State.STARTED) return;
            var hostViews = NetFixtures.payloads(host, LobbyStatePayload.class);
            var peerViews = NetFixtures.payloads(peer, LobbyStatePayload.class);
            if (hostViews.stream().noneMatch(p -> p.state().phase() == LobbyState.Phase.STARTED)
                    || peerViews.stream().noneMatch(p -> p.state().phase() == LobbyState.Phase.STARTED)) return;
            check(
                    hostViews.stream().allMatch(p -> p.state().self() == 0)
                            && peerViews.stream().allMatch(p -> p.state().self() == 1),
                    "wrong private lobby recipient");
            check(NetFixtures.payloads(outsider, LobbyStatePayload.class).isEmpty(), "outsider received lobby state");
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
            for (String type : List.of("unit_summary", "faction_state", "building_state", "deposit_state")) {
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

    /** Four distinct player/faction identities, actual terrain import, and all private initial frames within NET-02's deadline. */
    @StrategosTest(batch = "map_lobby_four", solo = true, timeoutTicks = 40000)
    public static void fourHumansReceiveTheirInitialStateWithinTenSeconds(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        LobbyFourMap.install(server);
        BlockPos at = h.absolutePos(BlockPos.ZERO);
        var viewers = new java.util.ArrayList<MockViewer>();
        for (int i = 0; i < 4; i++)
            viewers.add(MockViewer.join(h.getLevel(), "stg-lobby-f" + i, at.getX(), at.getY() + 16, at.getZ(), 2));
        viewers.getFirst().operator(2);
        MockViewer outsider = MockViewer.join(h.getLevel(), "stg-lobby-fout", at.getX(), at.getY() + 16, at.getZ(), 2);
        var seats = new java.util.ArrayList<MatchLobby.Seat>();
        for (int i = 0; i < 4; i++)
            seats.add(new MatchLobby.Seat(
                    new PlayerSlot(i),
                    MatchLobby.Kind.HUMAN,
                    i == 0 ? viewers.getFirst().player().getUUID() : null,
                    new FactionSlot(i),
                    i,
                    "strategos:placeholder",
                    i));
        MatchRuntime[] allocated = new MatchRuntime[1];
        Skirmishes.Placer actual = placer(h, 23);
        ServerLobby facade = new ServerLobby(server, (map, options) -> {
            MatchRuntime rt = actual.place(map, options);
            allocated[0] = rt;
            return rt;
        });
        LobbyService service = facade.service();
        var setup = new SkirmishSetup(
                LobbyFourMap.ID.toString(),
                SkirmishSetup.Level.NORMAL,
                SkirmishSetup.StartResources.MAP,
                150,
                SkirmishSetup.Visibility.FOG);
        UUID host = viewers.getFirst().player().getUUID();
        service.open(host, 4, seats, setup);
        for (int i = 1; i < 4; i++) service.join(viewers.get(i).player().getUUID(), new PlayerSlot(i));
        long revision = service.snapshot().lobby().configurationRevision();
        for (MockViewer viewer : viewers) service.ready(viewer.player().getUUID(), true, revision);
        long started = System.nanoTime();
        service.start(host);
        MatchRuntime rt = java.util.Objects.requireNonNull(allocated[0]);
        check(rt.lifecycle() == MatchLifecycle.PREPARING, "four-player map did not enter import");
        for (MockViewer viewer : viewers)
            check(
                    server.matches().seatOf(viewer.player().getUUID()) == null,
                    "player admitted before authority/import");
        h.onEachTick(() -> {
            facade.tick();
            for (MockViewer viewer : viewers) viewer.poll();
            outsider.poll();
            long elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (elapsed > 10_000) {
                var counts = viewers.stream().map(v -> v.capture().counts()).toList();
                check(
                        false,
                        "four-player initial state exceeded ten seconds: " + elapsed + " ms; runtime=" + rt.lifecycle()
                                + "; lobby=" + service.snapshot().state() + "; frames=" + counts);
            }
            check(
                    service.snapshot().state() == LobbyService.State.PREPARING
                            || service.snapshot().state() == LobbyService.State.STARTED,
                    "four-player preparation failed");
            if (service.snapshot().state() != LobbyService.State.STARTED) return;
            for (int i = 0; i < 4; i++) {
                MockViewer viewer = viewers.get(i);
                int seat = i;
                var joins = NetFixtures.payloads(viewer, MatchJoinPayload.class);
                var states = NetFixtures.payloads(viewer, LobbyStatePayload.class);
                if (joins.isEmpty() || states.stream().noneMatch(p -> p.state().phase() == LobbyState.Phase.STARTED))
                    return;
                check(
                        joins.stream()
                                .allMatch(p -> p.join().slot().value() == seat
                                        && p.join().factions() == 4
                                        && p.join().matchId() == rt.id().value()),
                        "wrong four-player match_join");
                check(states.stream().allMatch(p -> p.state().self() == seat), "wrong four-player lobby recipient");
                var binding = server.matches().seatOf(viewer.player().getUUID());
                check(
                        binding != null && binding.slot().value() == seat && binding.match() == rt,
                        "wrong four-player admission");
                check(server.commanders().commands(viewer.player()), "member did not enter commander mode");
                for (String type : List.of("unit_summary", "faction_state", "building_state", "deposit_state")) {
                    if (viewer.capture().counts().getOrDefault("payload/strategos:" + type, 0) == 0) return;
                }
            }
            for (String type : List.of("lobby_state", "match_join", "unit_summary", "faction_state", "building_state"))
                check(
                        outsider.capture().counts().getOrDefault("payload/strategos:" + type, 0) == 0,
                        "four-player state leaked: " + type);
            check(core(rt).queries().diplomacy().teamsLocked(), "four-player authority not frozen");
            check(rt.roster().members().size() == 4, "four-player roster is incomplete");
            h.succeed();
        });
    }

    @StrategosTest(batch = "lobby_preflight", solo = true, timeoutTicks = 100)
    public static void invalidPreflightDoesNotInvokeTheWorldAllocator(GameTestHelper h) {
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        MockViewer host =
                MockViewer.join(h.getLevel(), "stg-pref-a", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        MockViewer peer =
                MockViewer.join(h.getLevel(), "stg-pref-b", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        int[] allocations = {0};
        LobbyBackend backend = new LobbyBackend(
                StrategosServer.get(),
                (map, options) -> {
                    allocations[0]++;
                    throw new AssertionError("must not allocate");
                },
                (member, snapshot) -> {
                    throw new AssertionError("must not publish");
                });
        UUID hostId = host.player().getUUID();
        check(backend.online(hostId) && !backend.seated(hostId), "connected member unavailable");
        check(!backend.online(UUID.randomUUID()), "absent member is online");
        check(!backend.mayHost(hostId), "ordinary member has host authority");
        host.operator(2);
        check(backend.mayHost(hostId), "operator lacks host authority");
        boolean invalid = false;
        try {
            backend.validate(ready(hostId, peer.player().getUUID(), "strategos:missing"), SkirmishSetup.DEFAULTS);
        } catch (IllegalArgumentException expected) {
            invalid = true;
        }
        check(invalid && allocations[0] == 0, "validation allocated or accepted invalid content");
        boolean refused = false;
        try {
            LobbyPreparation.start(
                    StrategosServer.get(),
                    ready(host.player().getUUID(), peer.player().getUUID(), "strategos:missing"),
                    SkirmishSetup.DEFAULTS,
                    (map, options) -> {
                        allocations[0]++;
                        throw new AssertionError("must not allocate");
                    });
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused && allocations[0] == 0, "invalid civ reached world allocation");
        LobbyMapHeaders.verify(StrategosServer.get());
        h.succeed();
    }

    @StrategosTest(batch = "map_lobby_cancel", solo = true, timeoutTicks = 100)
    public static void cancellationDropsThePartialRuntimeAndItsQueuedImport(GameTestHelper h) {
        BlockPos corner = h.absolutePos(BlockPos.ZERO);
        MockViewer host =
                MockViewer.join(h.getLevel(), "stg-lobby-ca", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        MockViewer peer =
                MockViewer.join(h.getLevel(), "stg-lobby-cb", corner.getX(), corner.getY() + 16, corner.getZ(), 2);
        host.operator(2);
        MatchRuntime[] allocated = new MatchRuntime[1];
        Skirmishes.Placer actual = placer(h, 22);
        ServerLobby facade = new ServerLobby(StrategosServer.get(), (map, options) -> {
            MatchRuntime rt = actual.place(map, options);
            allocated[0] = rt;
            return rt;
        });
        LobbyService service = facade.service();
        UUID hostId = host.player().getUUID();
        UUID peerId = peer.player().getUUID();
        service.open(
                hostId, 2, configured(hostId, null, "strategos:placeholder").seats(), SkirmishSetup.DEFAULTS);
        service.join(peerId, new PlayerSlot(1));
        long revision = service.snapshot().lobby().configurationRevision();
        service.ready(hostId, true, revision);
        service.ready(peerId, true, revision);
        service.start(hostId);
        var rt = java.util.Objects.requireNonNull(allocated[0]);
        facade.shutdown();
        check(service.snapshot().state() == LobbyService.State.CLOSED, "shutdown did not close lobby");
        check(rt.lifecycle() == MatchLifecycle.DISPOSED, "partial runtime not disposed");
        check(
                StrategosServer.get().matches().all().stream().noneMatch(m -> m.id().equals(rt.id())),
                "partial runtime retained");
        check(rt.roster().isEmpty(), "partial roster retained");
        h.succeed();
    }
}
