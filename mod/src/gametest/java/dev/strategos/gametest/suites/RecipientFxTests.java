package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.payload.FxPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** A page is a batching unit, never permission to reveal its hidden events. */
public final class RecipientFxTests {
    private RecipientFxTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void worldAndShotBatchesContainOnlyEventsVisibleToTheirRecipient(GameTestHelper h) {
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
        var rt = kit.match();
        var server = StrategosServer.get();
        var net = server.net();
        var at = h.absolutePos(new BlockPos(12, 20, 12));
        var observer = MockViewer.join(h.getLevel(), "fx-observer", at.getX(), at.getY(), at.getZ(), 2);
        var unbound = MockViewer.join(h.getLevel(), "fx-unbound", at.getX() + 1, at.getY(), at.getZ(), 2);
        h.onEachTick(observer::poll);
        h.onEachTick(unbound::poll);
        var p0 = new PlayerSlot(0);
        var p1 = new PlayerSlot(1);
        var p2 = new PlayerSlot(2);
        var p3 = new PlayerSlot(3);
        var f0 = new FactionSlot(0);
        var f1 = new FactionSlot(1);
        var f2 = new FactionSlot(2);
        rt.initializeDiplomacy(new DiplomacySetup(DiplomacyState.identity(4)
                .withTeam(f1, 0)
                .bind(p1, f0)
                .bind(p2, f1)
                .bind(p3, f2)
                .grant(p0, p2)
                .lockTeams()
                .snapshot()));
        var own = Fx.Event.at(FxKind.FLAME, 2, 7, 2);
        var hidden = Fx.Event.at(FxKind.FLAME, 14, 7, 14);
        var other = Fx.Event.at(FxKind.FLAME, 36, 7, 36);
        var enemy = Fx.Event.at(FxKind.FLAME, 36, 7, 2);
        var launch = Fx.Event.shot(FxKind.SHOT_LAUNCH, 2, 7, 2, 14, 7, 14, 8);
        var landing = Fx.Event.shot(FxKind.SHOT_IMPACT, 14, 7, 14, 2, 7, 2, 8);
        var unseen = Fx.Event.shot(FxKind.SHOT_LAUNCH, 14, 7, 14, 15, 7, 15, 8);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(2, 2), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(36, 36), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, f2, new Vec2d(36, 2), 1, 0));
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(kit.ready() && core(rt).vision() != null, "fog fixture not ready");
                    var vision = java.util.Objects.requireNonNull(core(rt).vision());
                    VisionGrid first = vision.grid(0);
                    VisionGrid second = vision.grid(1);
                    VisionGrid third = vision.grid(2);
                    check(
                            first.count(0, 0) > 0 && second.count(9, 9) > 0 && third.count(9, 0) > 0,
                            "vision not stamped");
                    check(
                            first.count(3, 3) == 0
                                    && first.count(9, 9) == 0
                                    && second.count(0, 0) == 0
                                    && second.count(3, 3) == 0
                                    && third.count(0, 0) == 0
                                    && third.count(3, 3) == 0
                                    && third.count(9, 9) == 0,
                            "hidden-event precondition failed");
                })
                .thenExecute(() -> {
                    kit.admit("fx-priv", List.of(p0, p1, p2, p3));
                    server.matches().join(rt, observer.player().getUUID(), PlayerSlot.ADMIN);
                    net.joined(rt, observer.player(), PlayerSlot.ADMIN);
                })
                .thenExecute(() -> {
                    for (var viewer : kit.viewers()) viewer.capture().clear();
                    observer.capture().clear();
                    unbound.capture().clear();
                    net.send(
                            new Audience.SeesCell(rt.id(), 0, 0),
                            new FxPayload(new Fx(rt.id().value(), List.of(own, hidden))));
                    net.send(
                            new Audience.SeesCell(rt.id(), 32, 32),
                            new FxPayload(new Fx(rt.id().value(), List.of(other))));
                    net.send(
                            new Audience.SeesCell(rt.id(), 32, 0),
                            new FxPayload(new Fx(rt.id().value(), List.of(enemy))));
                    net.send(
                            new Audience.SeesEither(rt.id(), 0, 0, 0, 0),
                            new FxPayload(new Fx(rt.id().value(), List.of(launch, landing, unseen))));
                })
                .thenWaitUntil(() -> {
                    for (var viewer : kit.viewers()) viewer.poll();
                    observer.poll();
                    unbound.poll();
                    check(
                            NetFixtures.fxOf(kit.viewer(0), rt).containsAll(List.of(own, launch, landing))
                                    && NetFixtures.fxOf(kit.viewer(1), rt).containsAll(List.of(own, launch, landing))
                                    && NetFixtures.fxOf(kit.viewer(2), rt).contains(other)
                                    && NetFixtures.fxOf(kit.viewer(3), rt).contains(enemy)
                                    && NetFixtures.fxOf(observer, rt)
                                            .containsAll(List.of(own, hidden, other, enemy, launch, landing, unseen)),
                            "visible FX positive controls missing");
                })
                .thenExecute(() -> {
                    check(NetFixtures.fxOf(unbound, rt).isEmpty(), "non-member received match FX");
                    check(
                            !NetFixtures.fxOf(kit.viewer(1), rt).contains(hidden)
                                    && !NetFixtures.fxOf(kit.viewer(1), rt).contains(other)
                                    && !NetFixtures.fxOf(kit.viewer(1), rt).contains(enemy)
                                    && !NetFixtures.fxOf(kit.viewer(1), rt).contains(unseen),
                            "co-op primary received hidden FX");
                    check(
                            NetFixtures.fxOf(kit.viewer(3), rt).equals(List.of(enemy)),
                            "opponent received another faction's FX");
                    var first = NetFixtures.fxOf(kit.viewer(0), rt);
                    var second = NetFixtures.fxOf(kit.viewer(2), rt);
                    check(
                            !first.contains(hidden)
                                    && !first.contains(other)
                                    && !first.contains(enemy)
                                    && !first.contains(unseen),
                            "hidden page/shot event leaked to faction zero");
                    check(
                            !second.contains(enemy)
                                    && !second.contains(own)
                                    && !second.contains(hidden)
                                    && !second.contains(launch)
                                    && !second.contains(landing)
                                    && !second.contains(unseen),
                            "hidden page/shot event leaked to faction one");
                })
                .thenExecute(kit::close)
                .thenExecute(observer::close)
                .thenExecute(unbound::close)
                .thenSucceed();
    }
}
