package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.spawn;

import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** The network test kit in a real server (QA-19): viewers that see what a client would, per player and type. */
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
