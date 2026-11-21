package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CameraJump;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.DebugSubscribe;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.CameraJumpPayload;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchControlPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/** The same open transport and account are insufficient after a new full recipient view. */
public final class MatchEpochTests {
    private MatchEpochTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void oldEpochsCannotReachAnyMatchScopedHandlerOrPoisonTheFreshCommandWindow(GameTestHelper h) {
        var server = StrategosServer.get();
        var registry = server.matches();
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        for (int f = 0; f < 2; f++)
            rt.submit(
                    PlayerSlot.ADMIN,
                    new dev.strategos.core.protocol.SpawnUnits(
                            TemplateSet.DEBUG_WALKER, new FactionSlot(f), new Vec2d(6, 6 + 12 * f), 1, 0));
        var state = new Object() {
            long oldEpoch;
            long freshEpoch;
            long hash;
            int commands;
            int violations;
            int rejected;
            int accepted;
            Vec3 position = Vec3.ZERO;
        };
        h.startSequence()
                .thenWaitUntil(() ->
                        check(kit.ready() && core(rt).queries().unitHandles().length == 2, "epoch fixture not ready"))
                .thenExecute(() -> kit.admit("epoch", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenWaitUntil(() -> check(
                        epoch(kit.viewer(0)) > 0 && epoch(kit.viewer(1)) > 0,
                        "actual initial epoch handshakes missing"))
                .thenExecute(() -> {
                    state.oldEpoch = epoch(kit.viewer(0));
                    kit.viewer(0).send(move(rt, 0, 1, 40, state.oldEpoch));
                    kit.viewer(1).send(move(rt, 1, 1, 40, epoch(kit.viewer(1))));
                })
                .thenWaitUntil(() -> check(
                        accepted(kit.viewer(0), 1) == 1 && accepted(kit.viewer(1), 1) == 1,
                        "original epoch owner/peer command controls missing"))
                .thenExecute(() -> {
                    registry.pause(rt.id());
                    server.commanders().enter(kit.viewer(0).player(), rt);
                    server.net().joined(rt, kit.viewer(0).player(), new PlayerSlot(0));
                })
                .thenWaitUntil(() -> check(
                        epoch(kit.viewer(0)) > 0 && epoch(kit.viewer(0)) != state.oldEpoch,
                        "same-account/transport full join did not deliver a distinct epoch"))
                .thenExecute(() -> {
                    var viewer = kit.viewer(0);
                    state.freshEpoch = epoch(viewer);
                    state.hash = core(rt).stateHash();
                    state.commands =
                            NetFixtures.envelopes(rt, Integer.MIN_VALUE).size();
                    state.violations = server.net().counters().violations("epoch-0");
                    state.rejected = rejected();
                    state.accepted = accepted(viewer, 1);
                    state.position = viewer.player().position();
                    var at = state.position;
                    int handle = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    viewer.send(move(rt, 0, Integer.MAX_VALUE, 12, state.oldEpoch));
                    viewer.send(new CameraJumpPayload(state.oldEpoch, new CameraJump(at.x + 4, at.y, at.z, 0, 0)));
                    viewer.send(new ModeSwitchPayload(
                            state.oldEpoch,
                            new ModeSwitch(
                                    true,
                                    rt.space().origin().getX() + 8,
                                    rt.space().origin().getZ() + 8)));
                    viewer.send(new UiQueryPayload(state.oldEpoch, new UiQuery(List.of(handle))));
                    viewer.send(new DebugSubscribePayload(state.oldEpoch, DebugSubscribe.NONE));
                    viewer.send(new MatchControlPayload(state.oldEpoch, MatchControl.RESUME_NOW));
                })
                .thenWaitUntil(() -> check(
                        rejected() == state.rejected + 6, "not all six actual handlers refused their old view epoch"))
                .thenExecuteAfter(3, () -> {
                    var viewer = kit.viewer(0);
                    check(core(rt).stateHash() == state.hash, "old epoch changed paused core state");
                    check(
                            NetFixtures.envelopes(rt, Integer.MIN_VALUE).size() == state.commands,
                            "old epoch entered the command journal");
                    check(viewer.player().position().equals(state.position), "old epoch moved the actual camera body");
                    check(
                            !server.commanders().observes(viewer.player()),
                            "old epoch changed the actual commander mode");
                    check(
                            server.net().counters().violations("epoch-0") == state.violations,
                            "old view charged the fresh violation budget");
                    check(
                            NetFixtures.payloads(viewer, UnitDetailPayload.class).stream()
                                    .noneMatch(frame -> frame.detail().units().stream()
                                            .anyMatch(u -> u.handle()
                                                    == core(rt).queries().unitHandlesOf(new FactionSlot(0))[0])),
                            "old epoch installed a private query subscription");
                    int handle = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    viewer.send(new UiQueryPayload(state.freshEpoch, new UiQuery(List.of(handle))));
                })
                .thenWaitUntil(() -> check(
                        NetFixtures.payloads(kit.viewer(0), UnitDetailPayload.class).stream()
                                .anyMatch(p -> p.detail().units().stream()
                                        .anyMatch(u ->
                                                u.handle() == core(rt).queries().unitHandlesOf(new FactionSlot(0))[0])),
                        "fresh epoch never reached its real query backend/private response"))
                .thenExecute(() -> {
                    registry.resume(rt.id());
                    kit.viewer(0).send(move(rt, 0, 1, 30, state.freshEpoch));
                    kit.viewer(1).send(move(rt, 1, 2, 30, epoch(kit.viewer(1))));
                })
                .thenWaitUntil(() -> check(
                        accepted(kit.viewer(0), 1) == state.accepted + 1 && accepted(kit.viewer(1), 2) == 1,
                        "old max sequence poisoned the fresh window or stopped the unaffected peer"))
                .thenExecute(() -> {
                    check(
                            NetFixtures.envelopes(rt, Integer.MIN_VALUE).size() == state.commands + 2,
                            "old epoch was applied alongside the two fresh commands");
                    check(
                            NetFixtures.envelopes(rt, Integer.MIN_VALUE).stream()
                                    .noneMatch(e -> e.clientSeq() == Integer.MAX_VALUE),
                            "old max sequence reached the canonical journal");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aPreviousMatchCannotAddressEqualHandlesInANewRuntimeOnTheSameConnection(GameTestHelper h) {
        var server = StrategosServer.get();
        var registry = server.matches();
        var options = MatchFixture.options(SimHarness.DEFAULT_SEED, h);
        var first = MatchFixture.create(h, 0, 0, 20, 48, options);
        var second = MatchFixture.create(h, 24, 0, 20, 48, options);
        for (var rt : List.of(first, second))
            rt.submit(
                    PlayerSlot.ADMIN,
                    new dev.strategos.core.protocol.SpawnUnits(
                            TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(6, 6), 1, 0));
        var at = h.absoluteVec(new Vec3(8, 20, 8));
        var viewer = MockViewer.join(h.getLevel(), "epcross", at.x, at.y, at.z, 2);
        h.onEachTick(viewer::poll);
        var state = new Object() {
            long oldEpoch;
            long oldGeneration;
            long freshEpoch;
            long hash;
            int commands;
            int rejected;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(first).queries().unitHandles().length == 1
                                && core(second).queries().unitHandles().length == 1,
                        "cross-runtime fixture not ready"))
                .thenExecute(() -> {
                    NetFixtures.seat(first, viewer, 0);
                    server.commanders().enter(viewer.player(), first);
                })
                .thenWaitUntil(() -> check(epoch(viewer) > 0, "first runtime handshake missing"))
                .thenExecute(() -> {
                    state.oldEpoch = epoch(viewer);
                    state.oldGeneration =
                            first.roster().generationOf(viewer.player().getUUID());
                    viewer.send(move(first, 0, 1, 14, state.oldEpoch));
                })
                .thenWaitUntil(() -> check(accepted(viewer, 1) == 1, "first runtime actual command control missing"))
                .thenExecute(() -> {
                    registry.pause(second.id());
                    NetFixtures.seat(second, viewer, 0);
                    server.commanders().enter(viewer.player(), second);
                })
                .thenWaitUntil(() -> check(
                        epoch(viewer) > 0 && epoch(viewer) != state.oldEpoch, "new runtime reused the old wire epoch"))
                .thenExecute(() -> {
                    check(
                            second.roster().generationOf(viewer.player().getUUID()) == state.oldGeneration,
                            "counterexample no longer has equal local roster generations");
                    check(
                            core(first).queries().unitHandles()[0]
                                    == core(second).queries().unitHandles()[0],
                            "counterexample no longer has colliding match-local handles");
                    state.freshEpoch = epoch(viewer);
                    state.hash = core(second).stateHash();
                    state.commands =
                            NetFixtures.envelopes(second, Integer.MIN_VALUE).size();
                    state.rejected = rejected("epcross");
                    viewer.send(move(first, 0, Integer.MAX_VALUE, 12, state.oldEpoch));
                })
                .thenWaitUntil(() -> check(
                        rejected("epcross") == state.rejected + 1,
                        "previous runtime packet did not reach the actual old-epoch refusal"))
                .thenExecuteAfter(3, () -> {
                    check(
                            core(second).stateHash() == state.hash,
                            "previous runtime packet changed the paused new match");
                    registry.resume(second.id());
                    viewer.send(move(second, 0, 1, 16, state.freshEpoch));
                })
                .thenWaitUntil(
                        () -> check(accepted(viewer, 1) == 2, "old runtime max sequence poisoned the new runtime"))
                .thenExecute(() -> {
                    check(
                            NetFixtures.envelopes(second, Integer.MIN_VALUE).size() == state.commands + 1,
                            "old runtime command was applied to the colliding new handle");
                    check(
                            NetFixtures.envelopes(second, Integer.MIN_VALUE).stream()
                                    .noneMatch(e -> e.clientSeq() == Integer.MAX_VALUE),
                            "old runtime max sequence entered the new journal");
                    viewer.close();
                    registry.dispose(first.id());
                    registry.dispose(second.id());
                })
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aFreshViewReusingASequenceCannotReceiveTheOlderPendingCommandsResult(GameTestHelper h) {
        var server = StrategosServer.get();
        var registry = server.matches();
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        for (int f = 0; f < 2; f++)
            rt.submit(
                    PlayerSlot.ADMIN,
                    new dev.strategos.core.protocol.SpawnUnits(
                            TemplateSet.DEBUG_WALKER, new FactionSlot(f), new Vec2d(6, 6 + 12 * f), 1, 0));
        var state = new Object() {
            long oldEpoch;
            long freshEpoch;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready() && core(rt).queries().unitHandles().length == 2,
                        "pending-result fixture not ready"))
                .thenExecute(() -> kit.admit("epreply", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenWaitUntil(() -> check(epoch(kit.viewer(0)) > 0, "old pending-result handshake missing"))
                .thenExecute(() -> {
                    registry.pause(rt.id());
                    state.oldEpoch = epoch(kit.viewer(0));
                    kit.viewer(0).send(move(rt, 0, 1, 40, state.oldEpoch));
                })
                .thenExecuteAfter(1, () -> server.net().joined(rt, kit.viewer(0).player(), new PlayerSlot(0)))
                .thenWaitUntil(
                        () -> check(epoch(kit.viewer(0)) != state.oldEpoch, "pending-result fresh handshake missing"))
                .thenExecute(() -> {
                    state.freshEpoch = epoch(kit.viewer(0));
                    // Same sequence in the fresh view, but a foreign unit: its own result must be REJECTED.
                    kit.viewer(0).send(move(rt, 1, 1, 30, state.freshEpoch));
                })
                .thenExecuteAfter(1, () -> registry.resume(rt.id()))
                .thenWaitUntil(() -> check(
                        !NetFixtures.payloads(kit.viewer(0), FeedbackPayload.class)
                                .isEmpty(),
                        "fresh pending command never received its result"))
                .thenExecuteAfter(3, () -> {
                    var replies = NetFixtures.payloads(kit.viewer(0), FeedbackPayload.class).stream()
                            .filter(p -> p.message() instanceof Feedback f && f.clientSeq() == 1)
                            .toList();
                    check(
                            replies.size() == 1
                                    && replies.getFirst().message() instanceof Feedback f
                                    && f.outcome() == CommandOutcome.REJECTED,
                            "old pending command result was retargeted to the fresh view's reused sequence");
                    long commands = NetFixtures.envelopes(rt, Integer.MIN_VALUE).stream()
                            .filter(e -> e.issuer().equals(new PlayerSlot(0)) && e.clientSeq() == 1)
                            .count();
                    check(commands == 2, "the old admitted order or the fresh independently refused order was lost");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static long epoch(MockViewer viewer) {
        viewer.poll();
        var joins = NetFixtures.payloads(viewer, MatchJoinPayload.class);
        return joins.isEmpty() ? 0 : joins.getLast().epoch();
    }

    private static int accepted(MockViewer viewer, int sequence) {
        viewer.poll();
        return (int) NetFixtures.payloads(viewer, FeedbackPayload.class).stream()
                .filter(p -> p.message() instanceof Feedback f
                        && f.clientSeq() == sequence
                        && f.outcome() == CommandOutcome.ACCEPTED)
                .count();
    }

    private static int rejected() {
        return rejected("epoch-0");
    }

    private static int rejected(String player) {
        var values = StrategosServer.get().net().counters().toJson().getAsJsonObject("rejections");
        var value = values.get(player + " stale_match_epoch");
        return value == null ? 0 : value.getAsInt();
    }

    private static CommandsPayload move(MatchRuntime rt, int faction, int sequence, int x, long epoch) {
        int handle = core(rt).queries().unitHandlesOf(new FactionSlot(faction))[0];
        return CommandsPayload.of(
                new CommandBatch(sequence, List.of(Move.replace(UnitSet.of(handle), new Vec2d(x, 6 + 12 * faction)))),
                epoch);
    }
}
