package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.protocol.Limits;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.sim.UnitState;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.NetCounters;
import dev.strategos.mod.server.net.RateLimiter;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
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
     * that type; a viewer 400 blocks away gets none of them; neither gets a payload of ours before NET-01.
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
                check(close.bytes().ours() == 0 && far.bytes().ours() == 0, "a payload of ours before NET-01");
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
