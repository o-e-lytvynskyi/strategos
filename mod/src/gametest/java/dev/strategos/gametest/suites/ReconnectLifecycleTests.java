package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
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
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import java.util.List;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/** Authenticated replacement control before checking a late predecessor's real lifecycle callback. */
public final class ReconnectLifecycleTests {
    private ReconnectLifecycleTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aLatePredecessorLogoutCannotRevokeTheFreshSeatOrItsSequenceWindow(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var server = StrategosServer.get();
        for (int i = 0; i < 2; i++)
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(i), new Vec2d(6, 6 + 12 * i), 1, 0));
        var state = new Object() {
            @Nullable ServerPlayer old;

            @Nullable MockViewer fresh;

            int violations;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready() && core(rt).queries().unitHandles().length == 2, "lifecycle fixture not ready"))
                .thenExecute(() -> kit.admit("lateout", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    kit.viewer(0).send(move(kit, 0, 1, 40));
                    kit.viewer(1).send(move(kit, 1, 1, 40));
                })
                .thenWaitUntil(() -> check(
                        accepted(kit.viewer(0), 1) == 1 && accepted(kit.viewer(1), 1) == 1,
                        "initial actual command controls missing"))
                .thenExecute(() -> {
                    var old = kit.viewer(0).player();
                    state.old = old;
                    var at = old.position();
                    kit.viewer(0).close();
                    var fresh = MockViewer.join(h.getLevel(), "lateout-0", at.x, at.y, at.z, 2);
                    state.fresh = fresh;
                    fresh.send(move(kit, 0, 1, 36));
                    kit.viewer(1).send(move(kit, 1, 2, 36));
                })
                .thenWaitUntil(() -> {
                    var fresh = Objects.requireNonNull(state.fresh);
                    fresh.poll();
                    check(
                            accepted(fresh, 1) == 1
                                    && accepted(kit.viewer(1), 2) == 1
                                    && NetFixtures.payloads(fresh, MatchJoinPayload.class).stream()
                                            .anyMatch(p -> p.join().matchId()
                                                            == rt.id().value()
                                                    && p.join().slot().equals(new PlayerSlot(0))),
                            "fresh replacement never regained its actual faction/command/handshake control");
                })
                .thenExecute(() -> {
                    var fresh = Objects.requireNonNull(state.fresh);
                    state.violations = server.net().counters().violations("lateout-0");
                    StrategosServer.onLoggedOut(
                            new PlayerEvent.PlayerLoggedOutEvent(Objects.requireNonNull(state.old)));
                    check(
                            rt.roster().isConnected(fresh.player().getUUID()),
                            "late predecessor logout revoked the fresh seat");
                    fresh.send(move(kit, 0, 1, 12)); // must still be a replay after the stale lifecycle callback
                    fresh.send(move(kit, 0, 2, 40));
                    kit.viewer(1).send(move(kit, 1, 3, 40));
                })
                .thenWaitUntil(() -> check(
                        accepted(Objects.requireNonNull(state.fresh), 2) == 1 && accepted(kit.viewer(1), 3) == 1,
                        "fresh commands stopped after the stale lifecycle callback"))
                .thenExecute(() -> {
                    var fresh = Objects.requireNonNull(state.fresh);
                    check(accepted(fresh, 1) == 1, "late logout erased the fresh command sequence window");
                    check(
                            server.net().counters().violations("lateout-0") == state.violations + 1,
                            "replay was not the only new protocol violation");
                    var reasons = server.net().counters().toJson().getAsJsonObject("violations");
                    check(
                            reasons.has("lateout-0 command_sequence")
                                    && reasons.get("lateout-0 command_sequence").getAsInt() == 1,
                            "fresh replay has no sequence refusal");
                    var seat = server.matches().seatOf(fresh.player().getUUID());
                    check(
                            seat != null
                                    && seat.match() == rt
                                    && seat.primaryFaction().orElseThrow().equals(new FactionSlot(0)),
                            "late callback changed fresh faction authority");
                    check(
                            !fresh.disconnected() && !kit.viewer(1).disconnected(),
                            "late callback disconnected a valid peer");
                    fresh.close();
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static CommandsPayload move(MultiplayerHarness kit, int player, int sequence, int x) {
        int handle = core(kit.match()).queries().unitHandlesOf(new FactionSlot(player))[0];
        return CommandsPayload.of(new CommandBatch(
                sequence, List.of(Move.replace(UnitSet.of(handle), new Vec2d(x, 6 + 12 * player)))));
    }

    private static long accepted(MockViewer viewer, int sequence) {
        viewer.poll();
        return NetFixtures.payloads(viewer, FeedbackPayload.class).stream()
                .filter(p -> p.message() instanceof Feedback f
                        && f.clientSeq() == sequence
                        && f.outcome() == CommandOutcome.ACCEPTED)
                .count();
    }
}
