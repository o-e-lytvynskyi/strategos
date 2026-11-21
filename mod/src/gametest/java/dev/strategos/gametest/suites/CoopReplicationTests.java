package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.protocol.SetControl;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.VisionDelta;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.payload.BuildingStatePayload;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.PlayerAuthorityPayload;
import dev.strategos.mod.server.net.payload.VisionDeltaPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** T8.3a: real four-player private transport with non-identity primaries and a control-only ally. */
public final class CoopReplicationTests {
    private CoopReplicationTests() {}

    private static final long MARKER_TICK = 1_000_000_000L;

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void coopPrimariesSharePrivateStateButGrantedControlNeverReceivesIt(GameTestHelper h) {
        var options = new MatchOptions(
                SimHarness.DEFAULT_SEED,
                4,
                false,
                MatchSource.GAMETEST,
                null,
                MetricsSink.none(),
                Math.floor(h.getBounds().minY) + 1,
                null,
                MatchOptions.GAMETEST_PLAY);
        MultiplayerHarness kit = MultiplayerHarness.create(h, options);
        var rt = kit.match();
        PlayerSlot p0 = new PlayerSlot(0), p1 = new PlayerSlot(1), p2 = new PlayerSlot(2), p3 = new PlayerSlot(3);
        FactionSlot f0 = new FactionSlot(0), f1 = new FactionSlot(1), f2 = new FactionSlot(2);
        var authority = DiplomacyState.identity(4)
                .withTeam(f1, 0)
                .bind(p1, f0)
                .bind(p2, f1)
                .bind(p3, f2)
                .grant(p0, p2)
                .lockTeams();
        rt.initializeDiplomacy(new DiplomacySetup(authority.snapshot()));
        for (int f = 0; f < 4; f++) rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), "food", 111L * (f + 1)));
        var centre = new TemplateId("strategos:building/civic_centre");
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, f0, 4, 4, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, f1, 26, 26, 0));
        long[] revokedAt = {-1};
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready() && core(rt).queries().buildings().size() == 2,
                        "fixture not ready: " + core(rt).queries().buildings().size() + " buildings at tick "
                                + core(rt).queries().tick()))
                .thenExecute(() -> {
                    int building = core(rt).queries().buildings().stream()
                            .filter(b -> b.faction() == 0)
                            .findFirst()
                            .orElseThrow()
                            .handle();
                    rt.submit(PlayerSlot.ADMIN, new Train(building, TemplateSet.WORKER));
                    kit.admit("coop", List.of(p0, p1, p2, p3));
                })
                .thenWaitUntil(() -> {
                    for (int p = 0; p < 4; p++) {
                        check(
                                !kit.payloads(p, FactionStatePayload.class).isEmpty(),
                                "no positive faction frame for player " + p);
                        check(
                                !kit.payloads(p, BuildingStatePayload.class).isEmpty(),
                                "no positive building frame for player " + p);
                    }
                })
                .thenWaitUntil(() -> {
                    for (int p = 0; p < 2; p++)
                        check(
                                kit.payloads(p, BuildingStatePayload.class).stream()
                                        .flatMap(v -> v.state().buildings().stream())
                                        .anyMatch(b -> b.faction() == 0
                                                && b.own()
                                                && !b.queue().isEmpty()),
                                "no positive co-op queue for player " + p);
                })
                .thenExecute(() -> {
                    int[] primary = {0, 0, 1, 2};
                    for (int p = 0; p < 4; p++) {
                        for (var payload : kit.payloads(p, FactionStatePayload.class)) {
                            check(
                                    payload.state().faction() == primary[p],
                                    "private treasury reached wrong primary player " + p);
                        }
                        for (var payload : kit.payloads(p, BuildingStatePayload.class))
                            for (var building : payload.state().buildings()) {
                                check(
                                        building.own() == (building.faction() == primary[p]),
                                        "wrong private building ownership for player " + p);
                                if (!building.own())
                                    check(
                                            building.queue().isEmpty() && !building.rally(),
                                            "foreign queue/rally leaked");
                            }
                    }
                    for (int p = 0; p < 2; p++) {
                        check(
                                kit.payloads(p, BuildingStatePayload.class).stream()
                                        .flatMap(v -> v.state().buildings().stream())
                                        .anyMatch(b -> b.faction() == 0
                                                && b.own()
                                                && !b.queue().isEmpty()),
                                "co-op positive queue missing");
                    }
                    StrategosServer.get()
                            .net()
                            .send(
                                    new Audience.Faction(rt.id(), f0),
                                    new VisionDeltaPayload(new VisionDelta(
                                            rt.id().value(), MARKER_TICK, 1, 1, true, new int[] {0}, new int[] {0})));
                })
                .thenWaitUntil(() -> check(marker(kit, 0) && marker(kit, 1), "faction audience missed a co-op primary"))
                .thenExecuteAfter(2, () -> {
                    check(
                            !marker(kit, 2) && !marker(kit, 3),
                            "faction-private audience used control rights or numeric slots");
                    for (int player = 0; player < 4; player++) {
                        var joined = kit.payloads(player, MatchJoinPayload.class)
                                .getFirst()
                                .join();
                        int expected = player < 2 ? 0 : player - 1;
                        check(
                                joined.primaryFaction() == expected
                                        && joined.slot().value() == player,
                                "join confused primary faction with recipient identity");
                    }
                    check(authority(kit, 2).controlsFaction(0), "initial control grant absent");
                    kit.sendCommands(0, new CommandBatch(1, List.of(new SetControl(p2, false))));
                })
                .thenWaitUntil(() -> check(!authority(kit, 2).controlsFaction(0), "revoke did not reach its recipient"))
                .thenExecute(() -> {
                    var changed = authority(kit, 2);
                    check(changed.primaryFaction() == 1 && changed.controlsFaction(1), "revoke lost primary authority");
                    revokedAt[0] = changed.tick();
                    kit.sendCommands(1, new CommandBatch(1, List.of(new SetControl(p2, true))));
                })
                .thenWaitUntil(() -> check(
                        authority(kit, 2).controlsFaction(0)
                                && authority(kit, 2).tick() > revokedAt[0],
                        "co-op primary regrant not published"))
                .thenExecute(() -> {
                    for (int player = 0; player < 4; player++) {
                        for (var frame : kit.payloads(player, PlayerAuthorityPayload.class)) {
                            check(
                                    frame.authority().player().value() == player
                                            && frame.authority().matchId()
                                                    == rt.id().value(),
                                    "authority reached another recipient");
                        }
                        int expected = player < 2 ? 0 : player - 1;
                        for (var frame : kit.payloads(player, FactionStatePayload.class)) {
                            check(frame.state().faction() == expected, "grant/revoke widened private identity");
                        }
                    }
                    kit.close();
                })
                .thenSucceed();
    }

    private static PlayerAuthority authority(MultiplayerHarness kit, int player) {
        var frames = kit.payloads(player, PlayerAuthorityPayload.class);
        check(!frames.isEmpty(), "no positive authority frame for player " + player);
        return frames.getLast().authority();
    }

    private static boolean marker(MultiplayerHarness kit, int player) {
        return kit.payloads(player, VisionDeltaPayload.class).stream()
                .anyMatch(v -> v.delta().tick() == MARKER_TICK);
    }
}
