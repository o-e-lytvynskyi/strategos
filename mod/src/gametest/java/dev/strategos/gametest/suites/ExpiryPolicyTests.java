package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Resign;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.FactionExpiry;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.ReconnectSettings;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.persist.MatchData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/** Real paused/save/restore boundaries before the default canonical defeat policy, with a live opponent control. */
public final class ExpiryPolicyTests {
    private ExpiryPolicyTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void theLastExpiredOwnerResignsOnceAfterThePausedMatchResumes(GameTestHelper h) {
        defeat(h, false, "timeout");
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void expirySurvivesAnotherPausedSaveAndRestoreAndAppliesOnce(GameTestHelper h) {
        defeat(h, true, "timeback");
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aResolvedDefeatIsNotReissuedAfterAnOngoingMatchIsSavedAndRestored(GameTestHelper h) {
        resolved(h, false);
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aConfiguredComputerTakeoverSurvivesRestoreWithoutResetOrDefeat(GameTestHelper h) {
        resolved(h, true);
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void sandboxExpiryIsPersistentWithoutEndingOtherPlayersOrRepeatingTheInput(GameTestHelper h) {
        resolved(h, false, PlayRules.Victory.NONE);
    }

    private static void resolved(GameTestHelper h, boolean computer) {
        resolved(h, computer, PlayRules.Victory.CONQUEST);
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void disablingTakeoverDoesNotRearmExpiryAfterSaveAndRestore(GameTestHelper h) {
        resolved(h, true, PlayRules.Victory.CONQUEST, true);
    }

    private static void resolved(GameTestHelper h, boolean computer, PlayRules.Victory victory) {
        resolved(h, computer, victory, false);
    }

    private static void resolved(GameTestHelper h, boolean computer, PlayRules.Victory victory, boolean disable) {
        var registry = StrategosServer.get().matches();
        String prefix = disable
                ? "takeoff"
                : computer ? "takeover" : victory == PlayRules.Victory.NONE ? "sandbox" : "resolved";
        var base = MatchFixture.options(SimHarness.DEFAULT_SEED, h);
        var options = new MatchOptions(
                base.seed(),
                3,
                true,
                base.source(),
                base.faultPolicy(),
                base.metrics(),
                base.flatFloorY(),
                base.map(),
                new PlayRules(victory, false, false, false));
        var kit = MultiplayerHarness.create(h, options);
        var rt = kit.match();
        for (int i = 0; i < 3; i++)
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(i), new Vec2d(6, 6 + 12 * i), 1, 0));
        var state = new Object() {
            @Nullable MatchRuntime back;

            @Nullable MockViewer peer;

            boolean policyChecked;
            boolean restored;
        };
        h.onEachTick(() -> {
            var peer = state.peer;
            if (peer != null) peer.poll();
        });
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready() && core(rt).queries().unitHandles().length == 3,
                        "three-faction expiry fixture not ready"))
                .thenExecute(() -> kit.admit(prefix, List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> kit.viewer(1).send(move(rt, 1, 1, 40)))
                .thenWaitUntil(() -> check(accepted(kit.viewer(1), 1), "original live opponent command missing"))
                .thenExecute(() -> {
                    var at = kit.viewer(1).player().position();
                    kit.viewer(0).close();
                    kit.viewer(1).close();
                    var back = restore(h, rt, true);
                    state.back = back;
                    kit.close();
                    state.peer = MockViewer.join(h.getLevel(), prefix + "-1", at.x, at.y, at.z, 2);
                    subscribe(Objects.requireNonNull(state.peer), back);
                })
                .thenWaitUntil(() -> check(
                        privateControl(Objects.requireNonNull(state.peer), Objects.requireNonNull(state.back)),
                        "first restored opponent control missing"))
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    if (computer) {
                        long hash = core(back).stateHash();
                        long tick = back.tick();
                        back.expireDisconnected(new ReconnectSettings(
                                ReconnectSettings.DEFAULT_SECONDS, ReconnectSettings.Policy.AI_TAKEOVER));
                        check(
                                core(back).stateHash() == hash && back.tick() == tick && policyActions(back, true) == 0,
                                "explicit takeover changed the paused core or journal");
                    }
                    registry.resume(back.id());
                    if (computer)
                        back.expireDisconnected(new ReconnectSettings(
                                ReconnectSettings.DEFAULT_SECONDS, ReconnectSettings.Policy.AI_TAKEOVER));
                    Objects.requireNonNull(state.peer).send(move(back, 1, 1, 35));
                })
                .thenWaitUntil(() -> check(
                        accepted(Objects.requireNonNull(state.peer), 1),
                        "opponent command missing while initial expiry resolves"))
                .thenWaitUntil(() -> check(
                        resolved(core(Objects.requireNonNull(state.back)), computer), "first expiry never resolved"))
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    check(
                            back.lifecycle() == MatchLifecycle.RUNNING,
                            "third surviving faction did not keep match running");
                    check(policyActions(back, computer) == 1, "first runtime did not issue exactly one expiry command");
                    replays(back);
                    if (disable) back.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "battle", ""));
                    state.policyChecked = true;
                })
                .thenWaitUntil(() -> check(
                        state.policyChecked
                                && (!disable
                                        || !core(Objects.requireNonNull(state.back))
                                                .queries()
                                                .computerControlled(new FactionSlot(0))),
                        "policy validation or deliberate controller disable did not complete"))
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    var peer = Objects.requireNonNull(state.peer);
                    var at = peer.player().position();
                    peer.close();
                    back = restore(h, back, false);
                    state.back = back;
                    check(
                            disable
                                    ? !core(back).queries().computerControlled(new FactionSlot(0))
                                            && !core(back).queries().factionDefeated(new FactionSlot(0))
                                    : resolved(core(back), computer),
                            "save lost resolved ownership");
                    check(!core(back).queries().factionDefeated(new FactionSlot(1)), "save defeated the live opponent");
                    state.peer = MockViewer.join(h.getLevel(), prefix + "-1", at.x, at.y, at.z, 2);
                    subscribe(Objects.requireNonNull(state.peer), back);
                    state.restored = true;
                })
                .thenWaitUntil(() -> check(
                        state.restored
                                && privateControl(
                                        Objects.requireNonNull(state.peer), Objects.requireNonNull(state.back)),
                        "resolved-restore private opponent control missing"))
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    registry.resume(back.id());
                    Objects.requireNonNull(state.peer).send(move(back, 1, 1, 30));
                })
                .thenWaitUntil(() -> check(
                        accepted(Objects.requireNonNull(state.peer), 1),
                        "post-resolution restore lost the live opponent command"))
                .thenExecuteAfter(10, () -> {
                    var back = Objects.requireNonNull(state.back);
                    check(
                            policyActions(back, computer) == 0,
                            "restored resolved faction received a duplicate expiry command");
                    check(resignations(back) == 0, "restored computer faction was defeated instead of retained");
                    check(back.lifecycle() == MatchLifecycle.RUNNING, "expiry ended unrelated surviving factions");
                    Objects.requireNonNull(state.peer).close();
                    registry.dispose(back.id());
                })
                .thenSucceed();
    }

    private static void replays(MatchRuntime rt) {
        var c = core(rt);
        var result = ReplayDriver.replay(c.journal(), c.queries().templates());
        boolean matches =
                result.matches() && result.finalTick() == c.queries().tick() && result.finalHash() == c.stateHash();
        String difference = matches ? "" : replayDifference(c);
        check(
                matches,
                "canonical expiry replay mismatch: " + result + "; live tick="
                        + c.queries().tick() + " hash=" + c.stateHash() + "; fields=" + difference);
    }

    private static String replayDifference(MatchCore live) {
        var journal = live.journal();
        var back = MatchCore.restore(
                journal.start(),
                live.queries().templates(),
                CorePorts.of(() -> 0L),
                journal.segments().getFirst()::slice);
        var ticks = journal.nonEmptyTicks();
        int index = 0;
        for (long tick = journal.startTick() + 1; tick <= journal.lastTick(); tick++) {
            TickInputs input = TickInputs.empty(tick);
            if (index < ticks.size() && ticks.get(index).tick() == tick) input = ticks.get(index++);
            back.step(input);
        }
        var differences = new ArrayList<String>();
        differences(
                "",
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, live.snapshot()),
                MatchSnapshotCodec.encode(JsonOps.INSTANCE, back.snapshot()),
                differences);
        return differences.toString();
    }

    private static void differences(String path, JsonElement live, JsonElement replay, List<String> out) {
        if (live.equals(replay) || out.size() >= 16) return;
        if (live.isJsonObject() && replay.isJsonObject()) {
            var keys = new TreeSet<String>();
            for (var e : live.getAsJsonObject().entrySet()) keys.add(e.getKey());
            for (var e : replay.getAsJsonObject().entrySet()) keys.add(e.getKey());
            for (String key : keys) {
                var a = live.getAsJsonObject().get(key);
                var b = replay.getAsJsonObject().get(key);
                if (a == null || b == null) out.add(path + "/" + key + " missing");
                else differences(path + "/" + key, a, b, out);
                if (out.size() >= 16) return;
            }
        } else if (live.isJsonArray()
                && replay.isJsonArray()
                && live.getAsJsonArray().size() == replay.getAsJsonArray().size()) {
            for (int i = 0; i < live.getAsJsonArray().size() && out.size() < 16; i++)
                differences(
                        path + "/" + i,
                        live.getAsJsonArray().get(i),
                        replay.getAsJsonArray().get(i),
                        out);
        } else {
            String a = live.toString(), b = replay.toString();
            out.add(path + "=" + a.substring(0, Math.min(80, a.length())) + " vs "
                    + b.substring(0, Math.min(80, b.length())));
        }
    }

    private static boolean resolved(dev.strategos.core.sim.MatchCore c, boolean computer) {
        var faction = new FactionSlot(0);
        return computer
                ? c.queries().computerControlled(faction) && !c.queries().factionDefeated(faction)
                : c.queries().factionDefeated(faction);
    }

    private static long policyActions(MatchRuntime rt, boolean computer) {
        if (!computer)
            return resignations(rt)
                    + core(rt).journal().nonEmptyTicks().stream()
                            .flatMap(t -> t.events().stream())
                            .filter(e -> e instanceof FactionExpiry expiry
                                    && expiry.faction().equals(new FactionSlot(0)))
                            .count();
        return NetFixtures.envelopes(rt, Integer.MIN_VALUE).stream()
                .filter(e -> e.issuer().isAdmin()
                        && e.command() instanceof SetAi a
                        && a.faction().equals(new FactionSlot(0))
                        && a.profile().equals("battle")
                        && !a.script().isEmpty())
                .count();
    }

    private static long resignations(MatchRuntime rt) {
        return NetFixtures.envelopes(rt, Integer.MIN_VALUE).stream()
                .filter(e -> e.issuer().isAdmin()
                        && e.command() instanceof Resign r
                        && r.faction().equals(new FactionSlot(0)))
                .count();
    }

    private static void defeat(GameTestHelper h, boolean restart, String prefix) {
        var registry = StrategosServer.get().matches();
        var options = MatchFixture.options(SimHarness.DEFAULT_SEED, h)
                .withPersistent(true)
                .withPlay(new PlayRules(PlayRules.Victory.CONQUEST, false, false, false));
        var kit = MultiplayerHarness.create(h, options);
        var rt = kit.match();
        for (int i = 0; i < 2; i++)
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(i), new Vec2d(6, 6 + 12 * i), 1, 0));
        var state = new Object() {
            @Nullable MatchRuntime back;

            @Nullable MockViewer peer;

            long hash;
            long tick;
        };
        h.onEachTick(() -> {
            var peer = state.peer;
            if (peer != null) peer.poll();
        });
        h.startSequence()
                .thenWaitUntil(() ->
                        check(kit.ready() && core(rt).queries().unitHandles().length == 2, "expiry fixture not ready"))
                .thenExecute(() -> kit.admit(prefix, List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> kit.viewer(0).send(move(rt, 0, 1, 40)))
                .thenWaitUntil(() -> check(accepted(kit.viewer(0), 1), "actual pre-disconnect owner command missing"))
                .thenExecute(() -> {
                    var at = kit.viewer(1).player().position();
                    kit.viewer(0).close();
                    kit.viewer(1).close();
                    var back = restore(h, rt, true);
                    state.back = back;
                    kit.close();
                    check(back.lifecycle() == MatchLifecycle.PAUSED, "expiry blob did not restore paused");
                    state.hash = core(back).stateHash();
                    state.tick = core(back).queries().tick();
                    var peer = MockViewer.join(h.getLevel(), prefix + "-1", at.x, at.y, at.z, 2);
                    state.peer = peer;
                    subscribe(peer, back);
                })
                .thenWaitUntil(() -> check(
                        privateControl(Objects.requireNonNull(state.peer), Objects.requireNonNull(state.back)),
                        "unexpired peer has no actual paused handshake/detail control"))
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    check(
                            core(back).queries().tick() == state.tick
                                    && core(back).stateHash() == state.hash,
                            "expiry advanced paused simulation state");
                    if (restart) {
                        var peer = Objects.requireNonNull(state.peer);
                        var at = peer.player().position();
                        peer.close();
                        back = restore(h, back, false);
                        state.back = back;
                        state.hash = core(back).stateHash();
                        state.tick = core(back).queries().tick();
                        peer = MockViewer.join(h.getLevel(), prefix + "-1", at.x, at.y, at.z, 2);
                        state.peer = peer;
                        subscribe(peer, back);
                    }
                })
                .thenWaitUntil(() -> check(
                        privateControl(Objects.requireNonNull(state.peer), Objects.requireNonNull(state.back)),
                        "post-restore unexpired peer control missing"))
                .thenExecute(() -> {
                    var back = Objects.requireNonNull(state.back);
                    check(
                            back.roster().remainingNanos(MockViewer.uuidOf(prefix + "-0")) == 0,
                            "paused save/restore renewed the expired owner's grace");
                    check(
                            core(back).queries().tick() == state.tick
                                    && core(back).stateHash() == state.hash,
                            "paused expiry policy stepped simulation before resume");
                    registry.resume(back.id());
                    Objects.requireNonNull(state.peer).send(move(back, 1, 1, 40));
                })
                .thenWaitUntil(() -> check(
                        accepted(Objects.requireNonNull(state.peer), 1),
                        "valid opponent command lost its actual application at expiry"))
                .thenWaitUntil(() -> {
                    var back = Objects.requireNonNull(state.back);
                    var results = core(back).queries().matchResults();
                    check(
                            back.lifecycle() == MatchLifecycle.ENDED
                                    && results != null
                                    && results.winners().equals(List.of(1)),
                            "expired faction was not defeated by the configured policy");
                })
                .thenExecuteAfter(5, () -> {
                    var back = Objects.requireNonNull(state.back);
                    long resignations = NetFixtures.envelopes(back, Integer.MIN_VALUE).stream()
                            .filter(e -> e.issuer().isAdmin()
                                    && e.command() instanceof Resign r
                                    && r.faction().equals(new FactionSlot(0)))
                            .count();
                    check(resignations == 1, "expiry did not record exactly one canonical faction resignation");
                    replays(back);
                    check(
                            !Objects.requireNonNull(state.peer).disconnected(),
                            "expiry disconnected the unaffected opponent");
                    Objects.requireNonNull(state.peer).close();
                    registry.dispose(back.id());
                })
                .thenSucceed();
    }

    private static MatchRuntime restore(GameTestHelper h, MatchRuntime rt, boolean expire) {
        var registry = StrategosServer.get().matches();
        var record = Objects.requireNonNull(registry.record(rt.id()));
        var tag = Objects.requireNonNull(rt.data())
                .save(new CompoundTag(), h.getLevel().registryAccess());
        if (expire) {
            var roster = tag.getList("roster", Tag.TAG_COMPOUND);
            int changed = 0;
            for (int i = 0; i < roster.size(); i++) {
                var member = roster.getCompound(i);
                if (member.getInt("slot") == 0) {
                    member.putLong("remainingNanos", 0);
                    changed++;
                }
            }
            check(changed == 1, "expiry fixture did not identify exactly one owner");
        }
        long window = rt.roster().reclaimWindowNanos();
        registry.unload(rt.id());
        return registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag, window));
    }

    private static void subscribe(MockViewer peer, MatchRuntime rt) {
        int handle = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
        peer.send(new UiQueryPayload(new UiQuery(List.of(handle))));
    }

    private static boolean privateControl(MockViewer peer, MatchRuntime rt) {
        peer.poll();
        int handle = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
        return NetFixtures.payloads(peer, MatchJoinPayload.class).stream()
                        .anyMatch(p -> p.join().matchId() == rt.id().value()
                                && p.join().slot().equals(new PlayerSlot(1)))
                && NetFixtures.payloads(peer, UnitDetailPayload.class).stream()
                        .anyMatch(p -> p.detail().units().stream().anyMatch(u -> u.handle() == handle));
    }

    private static CommandsPayload move(MatchRuntime rt, int faction, int sequence, int x) {
        int handle = core(rt).queries().unitHandlesOf(new FactionSlot(faction))[0];
        return CommandsPayload.of(new CommandBatch(
                sequence, List.of(Move.replace(UnitSet.of(handle), new Vec2d(x, 6 + 12 * faction)))));
    }

    private static boolean accepted(MockViewer viewer, int sequence) {
        viewer.poll();
        return NetFixtures.payloads(viewer, FeedbackPayload.class).stream()
                .anyMatch(p -> p.message() instanceof Feedback f
                        && f.clientSeq() == sequence
                        && f.outcome() == CommandOutcome.ACCEPTED);
    }
}
