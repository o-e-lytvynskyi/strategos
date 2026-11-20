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
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.persist.MatchData;
import java.util.List;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/** Valid co-op control after actual blob restore is established before checking an expired UUID's denied login. */
public final class ReconnectRestoreTests {
    private ReconnectRestoreTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aRestoredZeroDurationCannotRejoinWhileTheUnexpiredCoopPeerStillCommands(GameTestHelper h) {
        var server = StrategosServer.get();
        var registry = server.matches();
        var kit = MultiplayerHarness.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        var rt = kit.match();
        var authority = new DiplomacyState(2)
                .bind(new PlayerSlot(0), new FactionSlot(0))
                .bind(new PlayerSlot(1), new FactionSlot(0));
        rt.initializeDiplomacy(new DiplomacySetup(authority.lockTeams().snapshot()));
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(6, 6), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(6, 18), 1, 0));
        var state = new Object() {
            @Nullable MatchRuntime back;

            @Nullable MockViewer expired;

            @Nullable MockViewer peer;

            boolean revived;
            long hash;
            int commands;
            int violations;
        };
        h.onEachTick(() -> {
            poll(state.expired);
            poll(state.peer);
        });
        h.startSequence()
                .thenWaitUntil(() ->
                        check(kit.ready() && core(rt).queries().unitHandles().length == 2, "restore fixture not ready"))
                .thenExecute(() -> kit.admit("expiry", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> kit.viewer(0).send(move(rt, 1, 40)))
                .thenWaitUntil(() -> check(accepted(kit.viewer(0), 1), "pre-disconnect actual command control missing"))
                .thenExecute(() -> {
                    var data = Objects.requireNonNull(rt.data());
                    var record = Objects.requireNonNull(registry.record(rt.id()));
                    var at = kit.viewer(0).player().position();
                    kit.viewer(0).close();
                    kit.viewer(1).close();
                    var saved = data.save(new CompoundTag(), h.getLevel().registryAccess());
                    var roster = saved.getList("roster", Tag.TAG_COMPOUND);
                    int changed = 0;
                    for (int i = 0; i < roster.size(); i++) {
                        var seat = roster.getCompound(i);
                        if (seat.getInt("slot") == 0) {
                            seat.putLong("remainingNanos", 0);
                            changed++;
                        } else check(seat.getLong("remainingNanos") > 0, "positive peer reservation already expired");
                    }
                    check(changed == 1, "zero-duration fixture did not identify exactly one reserved seat");
                    registry.unload(rt.id());
                    kit.close(); // retire the old harness before its match is replaced by the restored runtime
                    var back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), saved));
                    state.back = back;
                    check(back.lifecycle() == MatchLifecycle.PAUSED, "valid session blob did not restore paused");
                    var expired = MockViewer.join(h.getLevel(), "expiry-0", at.x, at.y, at.z, 2);
                    state.expired = expired;
                    state.revived = back.roster().isConnected(expired.player().getUUID());
                    var peer = MockViewer.join(h.getLevel(), "expiry-1", at.x + 1, at.y, at.z, 2);
                    state.peer = peer;
                    registry.resume(back.id());
                    peer.send(move(back, 1, 36));
                })
                .thenWaitUntil(() -> {
                    var back = Objects.requireNonNull(state.back);
                    var peer = Objects.requireNonNull(state.peer);
                    check(
                            accepted(peer, 1)
                                    && NetFixtures.payloads(peer, MatchJoinPayload.class).stream()
                                            .anyMatch(p -> p.join().matchId()
                                                            == back.id().value()
                                                    && p.join().slot().equals(new PlayerSlot(1)))
                                    && !core(back)
                                            .queries()
                                            .orders(core(back).queries().unitHandlesOf(new FactionSlot(0))[0])
                                            .isEmpty(),
                            "unexpired co-op peer never regained actual same-faction control after restore");
                })
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    var expired = Objects.requireNonNull(state.expired);
                    check(!state.revived, "restored zero-duration reservation was revived by login");
                    check(!back.roster().isConnected(expired.player().getUUID()), "expired member became connected");
                    check(
                            !server.commanders().commands(expired.player())
                                    && !server.commanders().observes(expired.player()),
                            "refused login still entered a commander or observer body mode");
                    registry.pause(back.id());
                    state.hash = core(back).stateHash();
                    state.commands =
                            NetFixtures.envelopes(back, Integer.MIN_VALUE).size();
                    state.violations = server.net().counters().violations("expiry-0");
                    expired.send(move(back, 1, 12));
                })
                .thenWaitUntil(() -> check(
                        server.net().counters().violations("expiry-0") == state.violations + 1,
                        "expired peer command never reached its actual refusal handler"))
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    var expired = Objects.requireNonNull(state.expired);
                    check(core(back).stateHash() == state.hash, "expired peer changed paused core state");
                    check(
                            NetFixtures.envelopes(back, Integer.MIN_VALUE).size() == state.commands,
                            "expired peer command entered the journal");
                    check(
                            NetFixtures.payloads(expired, MatchJoinPayload.class)
                                    .isEmpty(),
                            "expired peer received a private match handshake");
                    check(
                            server.commanders()
                                    .commands(Objects.requireNonNull(state.peer).player()),
                            "valid peer never entered its authorized commander mode");
                    registry.resume(back.id());
                    Objects.requireNonNull(state.peer).send(move(back, 2, 40));
                })
                .thenWaitUntil(() -> check(
                        accepted(Objects.requireNonNull(state.peer), 2),
                        "valid peer stopped after expired login/command"))
                .thenExecute(() -> {
                    var peer = Objects.requireNonNull(state.peer);
                    var expired = Objects.requireNonNull(state.expired);
                    check(
                            !peer.disconnected() && !expired.disconnected(),
                            "ordinary expired login disconnected a peer");
                    expired.close();
                    peer.close();
                    registry.dispose(Objects.requireNonNull(state.back).id());
                })
                .thenSucceed();
    }

    private static CommandsPayload move(MatchRuntime rt, int sequence, int x) {
        int handle = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
        return CommandsPayload.of(
                new CommandBatch(sequence, List.of(Move.replace(UnitSet.of(handle), new Vec2d(x, 6)))));
    }

    private static boolean accepted(MockViewer viewer, int sequence) {
        viewer.poll();
        return NetFixtures.payloads(viewer, FeedbackPayload.class).stream()
                .anyMatch(p -> p.message() instanceof Feedback f
                        && f.clientSeq() == sequence
                        && f.outcome() == CommandOutcome.ACCEPTED);
    }

    private static void poll(@Nullable MockViewer viewer) {
        if (viewer != null) viewer.poll();
    }
}
