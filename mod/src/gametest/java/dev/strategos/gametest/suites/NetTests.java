package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Limits;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.TemplateAck;
import dev.strategos.core.protocol.TemplateSetView;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.content.ServerTemplates;
import dev.strategos.mod.server.content.TemplateSync;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.NetCounters;
import dev.strategos.mod.server.net.RateLimiter;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.net.payload.TemplateAckPayload;
import dev.strategos.mod.server.net.payload.TemplateSetPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jspecify.annotations.Nullable;

/**
 * The network test kit and the payload layer in a real server (QA-19, NET-01): viewers that see what a client would,
 * per player and type; batches a client sends; bodies' poses on the wire.
 */
public final class NetTests {
    private static final String TEMPLATE_SET = "payload/" + TemplateSetPayload.TYPE.id();

    private NetTests() {}

    /**
     * QA-19.1: a viewer that answers its chunk batches gets every chunk of its view; one beside it that never answers
     * gets its first batch only, as R8 feared a mock player would.
     */
    @StrategosTest(timeoutTicks = 600)
    public static void acknowledgingViewerTracksView(GameTestHelper h) {
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-viewer", at.x, at.y, at.z, 4);
        MockViewer silent = MockViewer.join(h.getLevel(), "strategos-silent", at.x, at.y, at.z, 4, false);
        h.onEachTick(() -> {
            viewer.poll();
            silent.poll();
            Set<ChunkPos> view = new HashSet<>();
            viewer.player().getChunkTrackingView().forEach(view::add);
            if (view.size() > 9 && viewer.chunks().containsAll(view)) {
                check(silent.batches() == 1, "the silent viewer got " + silent.batches() + " batches");
                check(
                        silent.chunks().size() < view.size(),
                        "the silent viewer got " + silent.chunks().size() + " of " + view.size() + " chunks");
                viewer.close();
                silent.close();
                h.succeed();
            }
        });
    }

    /**
     * NET-01.6: an oversized batch and 99 malformed ones are dropped and counted, and the player stays; the 101st
     * violation within the minute disconnects it with the gateway's reason.
     */
    @StrategosTest(timeoutTicks = 100)
    public static void badBatchesDroppedAndDisconnect(GameTestHelper h) {
        Vec3 at = h.absoluteVec(new Vec3(8.5, 2, 8.5));
        String name = "strategos-bad";
        MockViewer viewer = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 2);
        NetCounters counters = StrategosServer.get().net().counters();
        int before = counters.violations(name);
        byte[] malformed = new WireWriter()
                .writeVarInt(0)
                .writeVarInt(Limits.BATCH_COMMANDS + 1)
                .toByteArray();
        viewer.send(read(h, new byte[Limits.BATCH_BYTES + 1]));
        for (int k = 0; k < RateLimiter.VIOLATIONS_PER_MINUTE - 1; k++) {
            viewer.send(read(h, malformed));
        }
        h.startSequence()
                .thenExecuteAfter(1, () -> {
                    check(
                            counters.violations(name) - before == RateLimiter.VIOLATIONS_PER_MINUTE,
                            "violations " + (counters.violations(name) - before));
                    check(!viewer.disconnected(), "disconnected at the 100th violation");
                    viewer.send(read(h, malformed));
                })
                .thenExecuteAfter(1, () -> {
                    viewer.poll();
                    check(viewer.disconnected(), "still connected after the 101st violation");
                    check(
                            !viewer.capture()
                                    .of(ClientboundDisconnectPacket.class)
                                    .isEmpty(),
                            "no disconnect packet: " + viewer.capture().counts());
                    viewer.close();
                })
                .thenSucceed();
    }

    /**
     * UNIT-01.5, NET-01.8: a walking unit's body reaches a viewer with the pose the core set in this tick's {@code
     * LevelTickEvent.Pre}: each move packet, decoded as a client decodes it, puts the body where it stands now, not a
     * tick behind. And the walk sends no entity data and no teleport (no {@code onGround} flip).
     */
    @StrategosTest(timeoutTicks = 300)
    public static void poseSameTickNoEntityData(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 16.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-pose", at.x, at.y, at.z, 4);
        var state = new Object() {
            int id = -1;
            boolean ordered;
            final VecDeltaCodec codec = new VecDeltaCodec();
            int moves;
            int seen;
        };
        h.onEachTick(() -> {
            viewer.poll();
            if (handles(rt).length != 1) {
                return;
            }
            int handle = handles(rt)[0];
            UnitEntity body = rt.bodies().get(handle);
            for (Packet<?> packet : viewer.capture()
                    .all()
                    .subList(state.seen, viewer.capture().all().size())) {
                if (packet instanceof ClientboundAddEntityPacket add && body != null && add.getId() == body.getId()) {
                    state.id = add.getId();
                    state.codec.setBase(new Vec3(add.getX(), add.getY(), add.getZ()));
                } else if (packet instanceof ClientboundMoveEntityPacket move
                        && state.id >= 0
                        && body != null
                        && isBody(move.getEntity(h.getLevel()), body)
                        && move.hasPosition()) {
                    Vec3 sent = state.codec.decode(move.getXa(), move.getYa(), move.getZa());
                    state.codec.setBase(sent);
                    state.moves++;
                    check(
                            sent.distanceTo(body.position()) <= 1.0 / 4096,
                            "the body was sent at " + sent + " while it stands at " + body.position());
                } else if (packet instanceof ClientboundTeleportEntityPacket teleport && teleport.getId() == state.id) {
                    check(false, "a teleport packet for the walking body");
                } else if (packet instanceof ClientboundSetEntityDataPacket data
                        && data.id() == state.id
                        && state.moves > 0) {
                    check(false, "entity data for the walking body: " + data.packedItems());
                }
            }
            state.seen = viewer.capture().all().size();
            if (state.id >= 0 && !state.ordered) {
                state.ordered = true;
                move(rt, handle, 28.5, 16.5);
            }
            if (state.moves >= 20 && unit(rt, handle).state() == UnitState.IDLE) {
                viewer.close();
                h.succeed();
            }
        });
    }

    /**
     * NET-01.5: a player seated in the match beside its units is paired with their bodies (its interest set holds
     * them) and gets {@code match_join} and the match's {@code match_state}.
     */
    @StrategosTest(timeoutTicks = 300)
    public static void sameMatchPairedAndInformed(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16.5, 16.5, 3, 1);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-same", at.x, at.y, at.z, 4);
        seat(rt, viewer, 0);
        h.onEachTick(() -> {
            viewer.poll();
            Set<Integer> bodies = bodyIds(rt);
            if (bodies.size() == 3 && added(viewer).containsAll(bodies)) {
                List<Integer> paired = StrategosServer.get()
                        .interest()
                        .handles(viewer.player().getUUID(), rt.id().value());
                check(paired.size() == 3, "paired with " + paired);
                check(
                        payloads(viewer, MatchJoinPayload.class).stream()
                                .anyMatch(p -> p.join().matchId() == rt.id().value()
                                        && p.join().slot().value() == 0),
                        "no match_join");
                check(
                        payloads(viewer, MatchStatePayload.class).stream()
                                .anyMatch(p -> p.state().matchId() == rt.id().value()
                                        && p.state().lifecycle() == MatchStateMsg.Lifecycle.RUNNING),
                        "no match_state");
                viewer.close();
                h.succeed();
            }
        });
    }

    /** NET-01.5: a player seated while it was away hears, as it logs in, which match it sits in, and its state. */
    @StrategosTest(timeoutTicks = 100)
    public static void seatedPlayerRejoinsInformed(GameTestHelper h) {
        MatchRuntime rt = match(h);
        String name = "strategos-back";
        StrategosServer.get().matches().join(rt, MockViewer.uuidOf(name), new PlayerSlot(1));
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 2);
        h.onEachTick(() -> {
            viewer.poll();
            boolean joined = payloads(viewer, MatchJoinPayload.class).stream()
                    .anyMatch(p -> p.join().matchId() == rt.id().value()
                            && p.join().slot().value() == 1);
            boolean state = payloads(viewer, MatchStatePayload.class).stream()
                    .anyMatch(p -> p.state().matchId() == rt.id().value());
            if (joined && state) {
                viewer.close();
                h.succeed();
            }
        });
    }

    /**
     * NET-01.5: a member of another match standing beside this match's units is never paired with their bodies and
     * gets none of this match's payloads, while a player in no match beside it is paired with them.
     */
    @StrategosTest(timeoutTicks = 300)
    public static void otherMatchGetsNothing(GameTestHelper h) {
        MatchRuntime a = MatchFixture.create(h, 0, 0, 14, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        MatchRuntime b = MatchFixture.create(h, 18, 0, 14, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        spawn(a, 8.5, 16.5, 3, 1);
        Vec3 at = h.absoluteVec(new Vec3(9.5, 2, 12.5));
        MockViewer member = MockViewer.join(h.getLevel(), "strategos-other", at.x, at.y, at.z, 4);
        MockViewer outsider = MockViewer.join(h.getLevel(), "strategos-none", at.x, at.y, at.z, 4);
        seat(b, member, 0);
        var state = new Object() {
            int ticks;
        };
        h.onEachTick(() -> {
            member.poll();
            outsider.poll();
            Set<Integer> bodies = bodyIds(a);
            if (bodies.size() != 3 || !added(outsider).containsAll(bodies)) {
                return; // the outsider sees them first: they are there to be seen
            }
            for (int id : added(member)) {
                check(!bodies.contains(id), "the other match's member was sent body " + id);
            }
            check(
                    StrategosServer.get()
                            .interest()
                            .handles(member.player().getUUID(), a.id().value())
                            .isEmpty(),
                    "the other match's member is paired with A's bodies");
            for (MatchStatePayload p : payloads(member, MatchStatePayload.class)) {
                check(p.state().matchId() != a.id().value(), "the other match's member got A's state");
            }
            if (++state.ticks >= 20) {
                check(
                        payloads(member, MatchStatePayload.class).stream()
                                .anyMatch(p -> p.state().matchId() == b.id().value()),
                        "B's member never got B's state");
                member.close();
                outsider.close();
                h.succeed();
            }
        });
    }

    /**
     * NET-01.9: a seated player's commands carry the slot bound to its UUID as issuer: its batch's first command, for
     * its faction's units, is accepted, the second, for another faction's unit, refused as not its own; both arrive in
     * the journal under its slot and its numbers, and each result comes back to it as feedback.
     */
    @StrategosTest(timeoutTicks = 300)
    public static void issuerFromUuidSlot(GameTestHelper h) {
        MatchRuntime rt = match(h);
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(16.5, 16.5), 2, 1));
        spawn(rt, 8.5, 8.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-slot", at.x, at.y, at.z, 2);
        seat(rt, viewer, 1);
        var state = new Object() {
            boolean sent;
        };
        h.onEachTick(() -> {
            viewer.poll();
            int[] own = core(rt).queries().unitHandlesOf(new FactionSlot(1));
            int[] other = core(rt).queries().unitHandlesOf(new FactionSlot(0));
            if (own.length != 2 || other.length != 1) {
                return;
            }
            if (!state.sent) {
                state.sent = true;
                viewer.send(CommandsPayload.of(new CommandBatch(
                        7,
                        List.of(
                                Move.replace(UnitSet.of(own), new Vec2d(24.5, 24.5)),
                                Move.replace(UnitSet.of(other), new Vec2d(24.5, 24.5))))));
                return;
            }
            List<Feedback> results = new ArrayList<>();
            for (FeedbackPayload p : payloads(viewer, FeedbackPayload.class)) {
                if (p.message() instanceof Feedback f) {
                    results.add(f);
                }
            }
            if (results.size() < 2) {
                return;
            }
            check(
                    results.contains(new Feedback(7, CommandOutcome.ACCEPTED, null, 2, 2)),
                    "the own move's feedback: " + results);
            check(
                    results.contains(new Feedback(8, CommandOutcome.REJECTED, RejectReason.NOT_OWNER, 0, 1)),
                    "the other faction's move's feedback: " + results);
            List<CommandEnvelope> sent = envelopes(rt, 1);
            check(
                    sent.size() == 2
                            && sent.get(0).clientSeq() == 7
                            && sent.get(1).clientSeq() == 8,
                    "journaled under slot 1: " + sent);
            viewer.close();
            h.succeed();
        });
    }

    /**
     * NET-01.9: a player with no seat in the match sends a move for its units: nothing reaches the core, the units
     * stay, no feedback comes back, and the gateway counts the violation.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void unseatedPlayerCommandsIgnored(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 8.5, 8.5, 2, 1);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 8.5));
        String name = "strategos-lone";
        MockViewer viewer = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 2);
        NetCounters counters = StrategosServer.get().net().counters();
        var state = new Object() {
            long sentAt = -1;
            int before;
        };
        h.onEachTick(() -> {
            viewer.poll();
            int[] units = handles(rt);
            if (units.length != 2) {
                return;
            }
            if (state.sentAt < 0) {
                state.sentAt = core(rt).queries().tick();
                state.before = counters.violations(name);
                viewer.send(CommandsPayload.of(
                        new CommandBatch(1, List.of(Move.replace(UnitSet.of(units), new Vec2d(24.5, 24.5))))));
                return;
            }
            if (core(rt).queries().tick() < state.sentAt + 20) {
                return;
            }
            for (int unit : units) {
                check(unit(rt, unit).state() == UnitState.IDLE, "a unit moved for an unseated player");
            }
            check(envelopes(rt, 0).isEmpty(), "a command of a player slot reached the core");
            check(payloads(viewer, FeedbackPayload.class).isEmpty(), "feedback for an unseated player");
            check(counters.violations(name) == state.before + 1, "violations " + counters.violations(name));
            viewer.close();
            h.succeed();
        });
    }

    /**
     * DATA-03.6, §1.6 D row 9: a player joining beside a match's bodies gets the server's template set, its table
     * with it, before the packet that adds any body: its client knows each body's template when it sees one.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void templateSetBeforePairing(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16.5, 16.5, 4, 2);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer[] viewer = new MockViewer[1];
        h.startSequence()
                .thenWaitUntil(() ->
                        check(bodyIds(rt).size() == 4, "bodies " + bodyIds(rt).size()))
                .thenExecute(() -> {
                    viewer[0] = MockViewer.join(h.getLevel(), "strategos-tset", at.x, at.y, at.z, 4);
                    seat(rt, viewer[0], 1);
                })
                .thenWaitUntil(() -> {
                    viewer[0].moved();
                    viewer[0].poll();
                    check(
                            added(viewer[0]).containsAll(bodyIds(rt)),
                            "the viewer sees " + added(viewer[0]) + " of " + bodyIds(rt) + "; chunks "
                                    + viewer[0].chunks().size() + "; "
                                    + viewer[0].capture().counts());
                })
                .thenExecute(() -> {
                    List<Packet<?>> all = viewer[0].capture().all();
                    Set<Integer> bodies = bodyIds(rt);
                    int set = -1;
                    int body = -1;
                    for (int k = 0; k < all.size(); k++) {
                        Packet<?> p = all.get(k);
                        if (set < 0
                                && p instanceof ClientboundCustomPayloadPacket c
                                && c.payload() instanceof TemplateSetPayload) {
                            set = k;
                        }
                        if (body < 0 && p instanceof ClientboundAddEntityPacket a && bodies.contains(a.getId())) {
                            body = k;
                        }
                    }
                    check(set >= 0, "no template set: " + viewer[0].capture().counts());
                    check(set < body, "the template set came at packet " + set + ", the first body at " + body);
                    ServerTemplates server = StrategosServer.get().templates();
                    TemplateSetView view =
                            payloads(viewer[0], TemplateSetPayload.class).get(0).view();
                    check(
                            view.toSet().version().equals(server.set().version()),
                            "the viewer got " + view.version() + ", the server runs "
                                    + server.set().version());
                    check(view.table().equals(server.table().ids()), "the viewer got the table " + view.table());
                    viewer[0].close();
                })
                .thenSucceed();
    }

    /**
     * DATA-03.6: a client that acknowledges another set gets the server's again, at most once a second; one that
     * acknowledges the server's gets nothing more.
     */
    @StrategosTest(timeoutTicks = 100)
    public static void templateHashMismatchResends(GameTestHelper h) {
        Vec3 at = h.absoluteVec(new Vec3(8.5, 2, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), "strategos-tack", at.x, at.y, at.z, 2);
        TemplateSync sync = StrategosServer.get().templateSync();
        long resent = sync.resent();
        int[] sets = new int[1];
        h.startSequence()
                .thenExecute(() -> {
                    viewer.poll();
                    sets[0] = payloads(viewer, TemplateSetPayload.class).size();
                    check(sets[0] == 1, "the join sent " + sets[0] + " template sets");
                    viewer.send(new TemplateAckPayload(new TemplateAck(
                            StrategosServer.get().templates().set().sha())));
                })
                .thenExecuteAfter(1, () -> {
                    viewer.poll();
                    check(payloads(viewer, TemplateSetPayload.class).size() == 1, "a matching ack got a resend");
                    viewer.send(new TemplateAckPayload(new TemplateAck(new byte[TemplateAck.SHA_BYTES])));
                    viewer.send(new TemplateAckPayload(new TemplateAck(new byte[TemplateAck.SHA_BYTES])));
                })
                .thenExecuteAfter(1, () -> {
                    viewer.poll();
                    int now = payloads(viewer, TemplateSetPayload.class).size();
                    check(now == 2, "two mismatched acks within a second got " + (now - 1) + " resends");
                    check(sync.resent() - resent == 1, "resends counted " + (sync.resent() - resent));
                    viewer.close();
                })
                .thenSucceed();
    }

    private static void seat(MatchRuntime rt, MockViewer viewer, int slot) {
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(slot));
        StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(slot));
    }

    private static Set<Integer> bodyIds(MatchRuntime rt) {
        Set<Integer> ids = new HashSet<>();
        for (int handle : handles(rt)) {
            UnitEntity body = rt.bodies().get(handle);
            if (body != null) {
                ids.add(body.getId());
            }
        }
        return ids;
    }

    private static Set<Integer> added(MockViewer viewer) {
        Set<Integer> ids = new HashSet<>();
        for (ClientboundAddEntityPacket p : viewer.capture().of(ClientboundAddEntityPacket.class)) {
            ids.add(p.getId());
        }
        return ids;
    }

    private static <P extends CustomPacketPayload> List<P> payloads(MockViewer viewer, Class<P> type) {
        List<P> out = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (type.isInstance(p.payload())) {
                out.add(type.cast(p.payload()));
            }
        }
        return out;
    }

    /** The commands the match's journal holds from player slots at or above {@code minSlot}, in order. */
    private static List<CommandEnvelope> envelopes(MatchRuntime rt, int minSlot) {
        List<CommandEnvelope> out = new ArrayList<>();
        for (Journal.Segment segment : core(rt).journal().segments()) {
            for (TickInputs tick : segment.nonEmptyTicks()) {
                for (InputEvent event : tick.events()) {
                    if (event instanceof CommandEnvelope e && e.issuer().value() >= minSlot) {
                        out.add(e);
                    }
                }
            }
        }
        return out;
    }

    private static boolean isBody(@Nullable Entity entity, UnitEntity body) {
        return entity != null && entity.getId() == body.getId();
    }

    private static CommandsPayload read(GameTestHelper h, byte[] bytes) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(bytes), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            return CommandsPayload.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    /**
     * QA-19.2: three units' bodies reach the viewer beside them as add-entity packets, with their bytes counted under
     * that type; a viewer 400 blocks away gets none of them; neither, seated in no match, gets a payload of ours but
     * the template set every player gets at login (DATA-03).
     */
    @StrategosTest(timeoutTicks = 400)
    public static void capturePerPlayerAndType(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16.5, 16.5, 3, 1);
        Vec3 near = h.absoluteVec(new Vec3(16.5, 2, 4.5));
        MockViewer close = MockViewer.join(h.getLevel(), "strategos-near", near.x, near.y, near.z, 4);
        MockViewer far = MockViewer.join(h.getLevel(), "strategos-far", near.x + 400, near.y, near.z, 2);
        h.onEachTick(() -> {
            close.poll();
            far.poll();
            Set<Integer> bodies = new HashSet<>();
            for (int handle : handles(rt)) {
                UnitEntity body = rt.bodies().get(handle);
                if (body != null) {
                    bodies.add(body.getId());
                }
            }
            Set<Integer> added = new HashSet<>();
            for (ClientboundAddEntityPacket p : close.capture().of(ClientboundAddEntityPacket.class)) {
                added.add(p.getId());
            }
            if (bodies.size() == 3 && added.containsAll(bodies)) {
                for (ClientboundAddEntityPacket p : far.capture().of(ClientboundAddEntityPacket.class)) {
                    check(!bodies.contains(p.getId()), "the far viewer was sent body " + p.getId());
                }
                check(
                        close.bytes().bytes("minecraft:add_entity") > 0,
                        "no add-entity bytes: " + close.bytes().toJson());
                check(
                        close.capture().counts().get("minecraft:add_entity") >= 3,
                        "counts " + close.capture().counts());
                for (MockViewer v : List.of(close, far)) {
                    check(
                            v.bytes().ours() == v.bytes().bytes(TEMPLATE_SET)
                                    && v.bytes().bytes(TEMPLATE_SET) > 0,
                            "payloads of ours beyond the template set: "
                                    + v.bytes().toJson());
                }
                check(
                        close.bytes().failures().isEmpty(),
                        "unencodable packets: " + close.bytes().failures());
                close.close();
                far.close();
                h.succeed();
            }
        });
    }
}
