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
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.gametest.net.NetFuzzCorpus;
import dev.strategos.gametest.net.ServerboundCases;
import dev.strategos.gametest.net.ServerboundCases.Input;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Real decoded refusal objects traverse the registered connection pipeline; framing remains the engine's boundary. */
public final class IngressCorpusTests {
    private IngressCorpusTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void permanentMalformedCorpusCannotChangePausedStateOrJournalAndValidTrafficStillApplies(
            GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var server = StrategosServer.get();
        var corpus = NetFuzzCorpus.read("fixtures/net-fuzz/ingress.json");
        var types = ServerboundCases.inputs().toList();
        for (int i = 0; i < 2; i++)
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(i), new Vec2d(6, 6 + 12 * i), 1, 0));
        var state = new Object() {
            long pausedHash;
            int commands;
            int violations;
        };
        String owner = "fuzzing-0";
        h.startSequence()
                .thenWaitUntil(() ->
                        check(kit.ready() && core(rt).queries().unitHandles().length == 2, "corpus fixture not ready"))
                .thenExecute(() -> kit.admit("fuzzing", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    move(kit, 0, 1);
                    move(kit, 1, 1);
                })
                .thenWaitUntil(() -> check(applied(kit, 1), "initial accepted command controls missing"))
                .thenExecute(() -> {
                    check(playerCommands(rt) == 2, "initial applied commands are absent from the actual journal");
                    server.matches().pause(rt.id());
                    check(rt.lifecycle() == MatchLifecycle.PAUSED, "pause control failed");
                    state.pausedHash = core(rt).stateHash();
                    state.commands = allCommands(rt);
                    state.violations = server.net().counters().violations(owner);
                    for (var entry : corpus) {
                        check(entry.refused(), "engine subset contains an undeclared accepted action");
                        var input = types.stream()
                                .filter(t -> t.spec().id().toString().equals(entry.payload()))
                                .findFirst()
                                .orElseThrow();
                        kit.viewer(0).send(decode(h, input, entry));
                    }
                })
                .thenWaitUntil(() -> check(
                        server.net().counters().violations(owner) == state.violations + corpus.size(),
                        "not every permanent malformed input reached the registered refusal handler"))
                .thenExecute(() -> {
                    check(core(rt).stateHash() == state.pausedHash, "malformed corpus changed paused core state");
                    check(allCommands(rt) == state.commands, "malformed input entered the command journal");
                    check(rt.lifecycle() == MatchLifecycle.PAUSED, "malformed control changed match lifecycle");
                    var seat = server.matches().seatOf(kit.viewer(0).player().getUUID());
                    check(
                            seat != null
                                    && seat.match() == rt
                                    && seat.slot().equals(new PlayerSlot(0))
                                    && seat.primaryFaction().orElseThrow().equals(new FactionSlot(0)),
                            "malformed input changed membership or authority");
                    var reasons = server.net().counters().toJson().getAsJsonObject("violations");
                    for (String reason : List.of(
                            "unreadable",
                            "invalid_lobby_action",
                            "invalid_camera_jump",
                            "invalid_ping",
                            "invalid_mode_switch",
                            "invalid_match_control",
                            "invalid_skirmish",
                            "invalid_template_ack",
                            "invalid_ui_query",
                            "invalid_debug_subscribe")) {
                        check(
                                reasons.has(owner + " " + reason)
                                        && reasons.get(owner + " " + reason).getAsInt() == 3,
                                "permanent corpus has no exact stable refusal count for " + reason);
                    }
                    check(
                            !kit.viewer(0).disconnected() && !kit.viewer(1).disconnected(),
                            "bounded corpus disconnected a legitimate peer");
                    server.matches().resume(rt.id());
                    move(kit, 0, 2);
                    move(kit, 1, 2);
                })
                .thenWaitUntil(() -> check(
                        applied(kit, 2) && allCommands(rt) == state.commands + 2 && playerCommands(rt) == 4,
                        "post-corpus accepted commands did not apply and enter the journal"))
                .thenExecute(() -> {
                    check(rt.lifecycle() == MatchLifecycle.RUNNING, "corpus faulted the match");
                    check(core(rt).queries().unitHandles().length == 2, "corpus created or removed a unit");
                    check(server.net().counters().violations("fuzzing-1") == 0, "corpus charged the unrelated peer");
                    check(
                            !kit.viewer(0).disconnected() && !kit.viewer(1).disconnected(),
                            "valid peer disconnected after commands");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static <P extends CustomPacketPayload> P decode(
            GameTestHelper h, Input<P> input, NetFuzzCorpus.Entry entry) {
        var buffer = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(entry.bytes()), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            P decoded = input.codec().decode(buffer);
            check(
                    input.refused().test(decoded),
                    "permanent malformed input escaped as an accepted payload: " + entry.id());
            check(buffer.readableBytes() == 0, "permanent decoder left unread bytes: " + entry.id());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static int allCommands(MatchRuntime rt) {
        return NetFixtures.envelopes(rt, Integer.MIN_VALUE).size();
    }

    private static int playerCommands(MatchRuntime rt) {
        return (int) NetFixtures.envelopes(rt, 0).stream()
                .filter(e -> e.issuer().equals(new PlayerSlot(0)) || e.issuer().equals(new PlayerSlot(1)))
                .count();
    }

    private static boolean applied(MultiplayerHarness kit, int sequence) {
        for (int i = 0; i < 2; i++) {
            int player = i;
            int handle = core(kit.match()).queries().unitHandlesOf(new FactionSlot(i))[0];
            boolean accepted = kit.payloads(player, FeedbackPayload.class).stream()
                    .anyMatch(p -> p.message() instanceof Feedback f
                            && f.clientSeq() == sequence
                            && f.outcome() == CommandOutcome.ACCEPTED);
            if (!accepted || core(kit.match()).queries().orders(handle).isEmpty()) return false;
        }
        return true;
    }

    private static void move(MultiplayerHarness kit, int player, int sequence) {
        int handle = core(kit.match()).queries().unitHandlesOf(new FactionSlot(player))[0];
        kit.sendCommands(
                player,
                new CommandBatch(
                        sequence,
                        List.of(Move.replace(
                                UnitSet.of(handle), new Vec2d(sequence == 1 ? 40 : 36, 6 + 12 * player)))));
    }
}
