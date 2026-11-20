package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchControlPayload;
import dev.strategos.mod.server.net.payload.SkirmishPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** Normal unavailable actions are auditable while the connection's protocol violation budget remains intact. */
public final class IngressAuditTests {
    private IngressAuditTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aNonHostSkirmishRefusalIsAuditedWithoutPunishingLegitimateCommands(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var server = StrategosServer.get();
        var counters = server.net().counters();
        var first = new FactionSlot(0);
        var second = new FactionSlot(1);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, first, new Vec2d(6, 6), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, second, new Vec2d(6, 18), 1, 0));
        var state = new Object() {
            int violations;
            int rejections;
            int controls;
            float tickRate;
        };
        String owner = "refusal-0";
        String key = owner + " skirmish_permission";
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready()
                                && core(rt).queries().unitHandlesOf(first).length == 1
                                && core(rt).queries().unitHandlesOf(second).length == 1,
                        "audit fixture not ready"))
                .thenExecute(() -> kit.admit("refusal", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    var player = kit.viewer(0).player();
                    check(
                            !server.server().isSingleplayerOwner(player.getGameProfile()) && !player.hasPermissions(2),
                            "fixture player unexpectedly owns or operates the server");
                    state.violations = counters.violations(owner);
                    var audit = counters.toJson().getAsJsonObject("rejections");
                    state.rejections =
                            audit != null && audit.has(key) ? audit.get(key).getAsInt() : 0;
                    check(!server.server().isSingleplayer(), "match-control fixture is not a dedicated server");
                    String controlKey = owner + " match_control_unavailable";
                    state.controls = audit != null && audit.has(controlKey)
                            ? audit.get(controlKey).getAsInt()
                            : 0;
                    state.tickRate = server.server().tickRateManager().tickrate();
                    for (int i = 0; i < 3; i++) kit.viewer(0).send(new SkirmishPayload(SkirmishSetup.DEFAULTS));
                    kit.viewer(0).send(new MatchControlPayload(MatchControl.PAUSE_NOW));
                    kit.viewer(0)
                            .send(new MatchControlPayload(new MatchControl(MatchControl.SPEED, MatchControl.FAST)));
                    kit.viewer(0).send(new MatchControlPayload(MatchControl.RESUME_NOW));
                    kit.sendCommands(
                            0,
                            new CommandBatch(
                                    1,
                                    List.of(Move.replace(
                                            UnitSet.of(core(rt).queries().unitHandlesOf(first)[0]),
                                            new Vec2d(40, 6)))));
                    kit.sendCommands(
                            1,
                            new CommandBatch(
                                    1,
                                    List.of(Move.replace(
                                            UnitSet.of(core(rt).queries().unitHandlesOf(second)[0]),
                                            new Vec2d(40, 18)))));
                })
                .thenWaitUntil(() -> check(
                        accepted(kit, 0)
                                && accepted(kit, 1)
                                && !core(rt).queries()
                                        .orders(core(rt).queries().unitHandlesOf(first)[0])
                                        .isEmpty()
                                && !core(rt).queries()
                                        .orders(core(rt).queries().unitHandlesOf(second)[0])
                                        .isEmpty(),
                        "legitimate command or unaffected peer lost its actual application"))
                .thenExecute(() -> {
                    check(
                            counters.violations(owner) == state.violations,
                            "semantic refusal consumed the protocol violation budget");
                    var audit = counters.toJson().getAsJsonObject("rejections");
                    check(
                            audit != null && audit.has(key) && audit.get(key).getAsInt() == state.rejections + 3,
                            "non-host skirmish refusal has no stable audit reason");
                    check(
                            !kit.viewer(0).disconnected() && !kit.viewer(1).disconnected(),
                            "semantic refusal disconnected a valid peer");
                    String controlKey = owner + " match_control_unavailable";
                    check(
                            audit != null
                                    && audit.has(controlKey)
                                    && audit.get(controlKey).getAsInt() == state.controls + 3,
                            "dedicated match controls have no stable semantic refusal audit");
                    check(
                            server.server().tickRateManager().tickrate() == state.tickRate,
                            "dedicated client changed server-wide simulation speed");
                    check(rt.lifecycle() == MatchLifecycle.RUNNING, "refused setup changed match lifecycle");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static boolean accepted(MultiplayerHarness kit, int player) {
        return kit.payloads(player, FeedbackPayload.class).stream()
                .anyMatch(frame -> frame.message() instanceof Feedback reply
                        && reply.clientSeq() == 1
                        && reply.outcome() == CommandOutcome.ACCEPTED);
    }
}
