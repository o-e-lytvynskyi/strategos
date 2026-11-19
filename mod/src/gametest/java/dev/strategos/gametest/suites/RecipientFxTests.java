package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.payload.FxPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** A page is a batching unit, never permission to reveal its hidden events. */
public final class RecipientFxTests {
    private RecipientFxTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void worldAndShotBatchesContainOnlyEventsVisibleToTheirRecipient(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.fogOptions(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var net = StrategosServer.get().net();
        var own = Fx.Event.at(FxKind.FLAME, 2, 7, 2);
        var hidden = Fx.Event.at(FxKind.FLAME, 14, 7, 14);
        var other = Fx.Event.at(FxKind.FLAME, 36, 7, 36);
        var launch = Fx.Event.shot(FxKind.SHOT_LAUNCH, 2, 7, 2, 14, 7, 14, 8);
        var landing = Fx.Event.shot(FxKind.SHOT_IMPACT, 14, 7, 14, 2, 7, 2, 8);
        var unseen = Fx.Event.shot(FxKind.SHOT_LAUNCH, 14, 7, 14, 15, 7, 15, 8);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(2, 2), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(36, 36), 1, 0));
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(kit.ready() && core(rt).vision() != null, "fog fixture not ready");
                    var vision = java.util.Objects.requireNonNull(core(rt).vision());
                    VisionGrid first = vision.grid(0);
                    VisionGrid second = vision.grid(1);
                    check(first.count(0, 0) > 0 && second.count(9, 9) > 0, "vision not stamped");
                    check(
                            first.count(3, 3) == 0
                                    && first.count(9, 9) == 0
                                    && second.count(0, 0) == 0
                                    && second.count(3, 3) == 0,
                            "hidden-event precondition failed");
                })
                .thenExecute(() -> kit.admit("fx-private", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    kit.viewer(0).capture().clear();
                    kit.viewer(1).capture().clear();
                    net.send(
                            new Audience.SeesCell(rt.id(), 0, 0),
                            new FxPayload(new Fx(rt.id().value(), List.of(own, hidden))));
                    net.send(
                            new Audience.SeesCell(rt.id(), 32, 32),
                            new FxPayload(new Fx(rt.id().value(), List.of(other))));
                    net.send(
                            new Audience.SeesEither(rt.id(), 0, 0, 0, 0),
                            new FxPayload(new Fx(rt.id().value(), List.of(launch, landing, unseen))));
                })
                .thenWaitUntil(() -> {
                    kit.viewer(0).poll();
                    kit.viewer(1).poll();
                    check(
                            NetFixtures.fxOf(kit.viewer(0), rt).containsAll(List.of(own, launch, landing))
                                    && NetFixtures.fxOf(kit.viewer(1), rt).contains(other),
                            "visible FX positive controls missing");
                })
                .thenExecute(() -> {
                    var first = NetFixtures.fxOf(kit.viewer(0), rt);
                    var second = NetFixtures.fxOf(kit.viewer(1), rt);
                    check(
                            !first.contains(hidden) && !first.contains(other) && !first.contains(unseen),
                            "hidden page/shot event leaked to faction zero");
                    check(
                            !second.contains(own)
                                    && !second.contains(hidden)
                                    && !second.contains(launch)
                                    && !second.contains(landing)
                                    && !second.contains(unseen),
                            "hidden page/shot event leaked to faction one");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }
}
