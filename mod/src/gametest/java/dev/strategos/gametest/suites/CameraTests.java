package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.protocol.CameraJump;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.GameTestTickets;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.commander.CommanderService;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.CameraJumpPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * The commander's server side (CAM-03, ADR-0017) with mock commanders: QA-19's viewers made commanders, which answer
 * every teleport as a client does and pan through {@code handleMovePlayer}.
 */
public final class CameraTests {
    private CameraTests() {}

    /**
     * CAM-03.1: a {@code camera_jump} moves the commander's body within a tick of its receipt; one beyond the space and
     * its 32 blocks and a fifth within a second are refused, and one from a player in no seat goes nowhere.
     */
    @StrategosTest(timeoutTicks = 100)
    public static void jumpMovesBodyAndLimits(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer viewer = commander(h, rt, "strategos-jump");
        MockViewer plain = MockViewer.join(h.getLevel(), "strategos-plain2", 0, 100, 0, 2);
        CommanderService commanders = StrategosServer.get().commanders();
        MatchSpace s = rt.space();
        double x = s.origin().getX() + 20.5;
        double z = s.origin().getZ() + 12.5;
        long refused = commanders.refused();
        h.startSequence()
                .thenExecute(() -> {
                    accept(viewer);
                    viewer.send(jump(x, s.origin().getY() + 20, z));
                })
                .thenExecuteAfter(1, () -> {
                    ServerPlayer p = viewer.player();
                    check(
                            Math.abs(p.getX() - x) < 1e-6 && Math.abs(p.getZ() - z) < 1e-6,
                            "the body is at " + p.position());
                    viewer.send(jump(s.origin().getX() - 40, s.origin().getY() + 20, z)); // beyond the reach
                    plain.send(jump(x, s.origin().getY() + 20, z)); // not a commander
                    for (int k = 0; k < 4; k++) {
                        viewer.send(jump(x + k, s.origin().getY() + 20, z)); // three more this second, then a fifth
                    }
                })
                .thenExecuteAfter(1, () -> {
                    check(commanders.refused() - refused == 2, "refused " + (commanders.refused() - refused));
                    check(
                            plain.player().getY() > 90,
                            "the unseated player moved to " + plain.player().position());
                    check(
                            Math.abs(viewer.player().getX() - (x + 2)) < 1e-6,
                            "the last accepted jump put the body at "
                                    + viewer.player().position());
                    viewer.close();
                    plain.close();
                })
                .thenSucceed();
    }

    /**
     * CAM-03.2: a mock commander pans at 5 blocks a tick through {@code handleMovePlayer} for 12,000 ticks with 20
     * jumps, and receives no position packet but the jumps'.
     */
    @StrategosTest(batch = "camera_pan", solo = true, timeoutTicks = 13_000)
    public static void panNoSnapBack(GameTestHelper h) {
        MatchRuntime rt =
                MatchFixture.create(h, space(h, 8_192, 256, 256), MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        MockViewer viewer = commander(h, rt, "strategos-pan");
        MatchSpace s = rt.space();
        double y = s.origin().getY() + 24;
        var state = new Object() {
            int ticks;
            double x;
            double z;
            double direction = 5;
            int jumps;
            int positions;
            boolean accepted;
        };
        h.onEachTick(() -> {
            viewer.poll();
            for (ClientboundPlayerPositionPacket p : viewer.capture().of(ClientboundPlayerPositionPacket.class)) {
                viewer.player()
                        .connection
                        .handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(p.getId()));
                if (state.accepted) {
                    state.positions++;
                }
                state.x = p.getX();
                state.z = p.getZ();
            }
            viewer.capture().clear();
            if (!state.accepted) {
                state.accepted = true; // the join's and the seat's teleports are answered; the pan starts
                return;
            }
            state.ticks++;
            if (state.ticks % 600 == 0 && state.jumps < 20) {
                state.jumps++;
                double jx = s.origin().getX() + (state.jumps % 2 == 0 ? 40 : 216);
                viewer.send(jump(jx, y, s.origin().getZ() + 128));
                return;
            }
            state.x += state.direction;
            if (state.x > s.origin().getX() + 240 || state.x < s.origin().getX() + 16) {
                state.direction = -state.direction;
                state.x += 2 * state.direction;
            }
            viewer.player()
                    .connection
                    .handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(state.x, y, state.z, 0, 55, false));
            if (state.ticks >= 12_000) {
                check(state.jumps == 20, state.jumps + " jumps");
                check(state.positions == 20, state.positions + " position packets for 20 jumps");
                viewer.close();
                h.succeed();
            }
        });
    }

    /**
     * CAM-03.3: after a 300-block jump the commander, standing still, is paired with the bodies of each newly sent chunk
     * within a tick of its sending, and with every body in range within 3 s; without the re-pair a standing viewer would
     * get none (M1's {@code MockViewer#moved}).
     */
    @StrategosTest(
            batch = "camera_repair",
            solo = true,
            timeoutTicks = 4_000) // 2,000 timed out once on a loaded CI runner
    public static void chunkSentRepair(GameTestHelper h) {
        MatchRuntime rt =
                MatchFixture.create(h, space(h, 10_240, 512, 64), MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        MatchSpace s = rt.space();
        List<ChunkPos> held = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) { // a match without a map holds no tickets: the units' chunks need some
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos c = new ChunkPos(
                        ((s.origin().getX() + 495) >> 4) + dx, ((s.origin().getZ() + 32) >> 4) + dz);
                GameTestTickets.force(h.getLevel(), c);
                held.add(c);
            }
        }
        spawn(rt, 495, 32, 20, 4);
        MockViewer viewer = commander(h, rt, "strategos-repair");
        viewer.player()
                .connection
                .teleport(
                        s.origin().getX() + 10,
                        s.origin().getY() + 20,
                        s.origin().getZ() + 32,
                        0,
                        55);
        var state = new Object() {
            int tick;
            long jumpedAt = -1;
            long jumpNanos;
            final Map<Integer, Integer> sentAt = new HashMap<>();
            int worstDelay;
            boolean answered;
        };
        h.onEachTick(() -> {
            state.tick++;
            viewer.poll();
            for (ClientboundPlayerPositionPacket p : viewer.capture().of(ClientboundPlayerPositionPacket.class)) {
                viewer.player()
                        .connection
                        .handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(p.getId()));
                state.answered = true;
            }
            viewer.capture().clear();
            if (state.tick < 40 || rt.bodies().count() < 20) {
                return; // the bodies come, far from the commander
            }
            ServerPlayer p = viewer.player();
            if (state.jumpedAt < 0) {
                viewer.send(jump(
                        s.origin().getX() + 480,
                        s.origin().getY() + 20,
                        s.origin().getZ() + 32));
                state.jumpedAt = state.tick;
                state.jumpNanos = System.nanoTime();
                return;
            }
            Set<Integer> paired = Set.copyOf(StrategosServer.get()
                    .interest()
                    .handles(p.getUUID(), rt.id().value()));
            int inRange = 0;
            for (UnitEntity body : h.getLevel()
                    .getEntitiesOfClass(UnitEntity.class, p.getBoundingBox().inflate(64))) {
                if (body.spawnData() == null) {
                    continue;
                }
                inRange++;
                int handle = body.spawnData().handle();
                ChunkPos c = body.chunkPosition();
                boolean sent = p.getChunkTrackingView().contains(c) && !p.connection.chunkSender.isPending(c.toLong());
                if (sent) {
                    state.sentAt.putIfAbsent(handle, state.tick);
                }
                if (paired.contains(handle) && state.sentAt.containsKey(handle)) {
                    state.worstDelay = Math.max(state.worstDelay, state.tick - state.sentAt.get(handle));
                } else if (state.sentAt.containsKey(handle)) {
                    check(
                            state.tick - state.sentAt.get(handle) <= 1,
                            "body " + handle + " unpaired " + (state.tick - state.sentAt.get(handle))
                                    + " ticks after its chunk");
                }
            }
            if (inRange == 20 && paired.size() >= 20) {
                long millis = (System.nanoTime() - state.jumpNanos) / 1_000_000;
                check(millis <= 3_000, "coverage took " + millis + " ms");
                check(state.worstDelay <= 1, "a pairing came " + state.worstDelay + " ticks after its chunk");
                viewer.close();
                held.forEach(c -> GameTestTickets.release(h.getLevel(), c));
                h.succeed();
            }
        });
    }

    /** CAM-03.4: a unit walks straight through a commander standing in its way: the commander is no agent. */
    @StrategosTest(timeoutTicks = 300)
    public static void unitsIgnoreCommander(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 16.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer viewer = commander(h, rt, "strategos-inway");
        var state = new Object() {
            boolean ordered;
            double worst;
        };
        viewer.player().connection.teleport(at.x, at.y, at.z, 0, 55); // in the unit's way, at its height
        h.onEachTick(() -> {
            accept(viewer);
            int[] units = handles(rt);
            if (units.length != 1) {
                return;
            }
            if (!state.ordered) {
                state.ordered = true;
                move(rt, units[0], 28.5, 16.5);
                return;
            }
            UnitView u = unit(rt, units[0]);
            state.worst = Math.max(state.worst, Math.abs(u.z() - 16.5));
            if (u.x() > 27) {
                check(state.worst < 0.05, "the unit swerved " + state.worst + " blocks round the commander");
                check(core(rt).queries().unitCount() == 1, "units");
                viewer.close();
                h.succeed();
            }
        });
    }

    /**
     * UI-11.3: a click on the minimap's pixel of a column, mapped back by the minimap's own geometry, sends a
     * {@code camera_jump} there that the server answers by the next tick.
     */
    @StrategosTest(timeoutTicks = 100)
    public static void aMinimapClickJumps(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer viewer = commander(h, rt, "strategos-mmap");
        MatchSpace s = rt.space();
        var minimap = new dev.strategos.core.client.hud.Minimap(
                dev.strategos.core.client.hud.HudLayout.compute(1920, 1080, 3).minimap(), s.sizeX(), s.sizeZ());
        double[] pixel = minimap.toMap(21.5, 13.5);
        double[] at = minimap.toWorld(pixel[0], pixel[1]);
        double x = s.origin().getX() + at[0];
        double z = s.origin().getZ() + at[1];
        h.startSequence()
                .thenExecute(() -> {
                    accept(viewer);
                    viewer.send(jump(x, s.origin().getY() + 20, z));
                })
                .thenExecuteAfter(1, () -> {
                    var p = viewer.player();
                    check(
                            Math.abs(p.getX() - (s.origin().getX() + 21.5)) < 1e-6
                                    && Math.abs(p.getZ() - (s.origin().getZ() + 13.5)) < 1e-6,
                            "the body is at " + p.position());
                    viewer.close();
                })
                .thenSucceed();
    }

    /**
     * UI-12.2: an enemy spearman burns the commander's house; the commander hears one alert in {@code match_event} and
     * its jump to the alert's place is answered by the next tick.
     */
    @StrategosTest(timeoutTicks = 400)
    public static void theAlertKeyJumps(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer viewer = commander(h, rt, "strategos-alert");
        StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(0));
        MatchSpace s = rt.space();
        rt.submit(
                PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.SpawnBuilding(
                        new dev.strategos.core.shared.TemplateId("strategos:building/house"),
                        new dev.strategos.core.shared.FactionSlot(0),
                        20,
                        20,
                        0));
        rt.submit(
                PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.SpawnUnits(
                        dev.strategos.core.content.TemplateSet.INFANTRY,
                        new dev.strategos.core.shared.FactionSlot(1),
                        new dev.strategos.core.shared.Vec2d(22, 28),
                        1,
                        0));
        var state = new Object() {
            dev.strategos.core.protocol.MatchNews alert;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        !core(rt).queries().buildings().isEmpty()
                                && core(rt).queries().unitHandles().length == 1,
                        "not ready"))
                .thenExecute(() -> rt.submit(
                        new PlayerSlot(1),
                        dev.strategos.core.protocol.Attack.replace(
                                dev.strategos.core.protocol.UnitSet.of(
                                        core(rt).queries().unitHandles()),
                                core(rt).queries().buildings().get(0).handle())))
                .thenWaitUntil(() -> {
                    viewer.poll();
                    for (var p : viewer.capture()
                            .of(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class)) {
                        if (p.payload() instanceof dev.strategos.mod.server.net.payload.MatchEventPayload m
                                && m.news().kind() == dev.strategos.core.protocol.MatchNews.ALERT) {
                            state.alert = m.news();
                        }
                    }
                    check(state.alert != null, "no alert yet");
                })
                .thenExecute(() -> {
                    accept(viewer);
                    viewer.send(jump(
                            s.origin().getX() + state.alert.x(),
                            s.origin().getY() + 20,
                            s.origin().getZ() + state.alert.z()));
                })
                .thenExecuteAfter(1, () -> {
                    var p = viewer.player();
                    check(
                            Math.abs(p.getX() - (s.origin().getX() + 22.5)) <= 1 // the commander's limits may
                                    && Math.abs(p.getZ() - (s.origin().getZ() + 22.5)) <= 1, // nudge the eye
                            "the body is " + (p.getX() - s.origin().getX()) + ", "
                                    + (p.getZ() - s.origin().getZ()) + " into the space for the alert " + state.alert);
                    viewer.close();
                })
                .thenSucceed();
    }

    /**
     * A space {@code east} blocks east of the test's corner, larger than its structure: batches put their structures at
     * the same place, and a commander panning there with its view loaded the chunks a later batch's test needs unloaded
     * (bodytests.bodies_follow_entity_ticking_chunks), so the camera tests go far off, as the map tests do.
     */
    private static MatchSpace space(GameTestHelper h, int east, int sizeX, int sizeZ) {
        BlockPos o = h.absolutePos(BlockPos.ZERO).offset(east, 0, 0);
        return new MatchSpace(h.getLevel().dimension(), o, sizeX, sizeZ, o.getY(), o.getY() + 63);
    }

    /** A viewer seated in slot 0 of {@code rt} and made its commander. */
    private static MockViewer commander(GameTestHelper h, MatchRuntime rt, String name) {
        Vec3 at = h.absoluteVec(new Vec3(8.5, 20, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 4);
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(viewer.player(), rt);
        return viewer;
    }

    /** Answers the teleports the viewer has had, as a client does. */
    private static void accept(MockViewer viewer) {
        viewer.poll();
        List<ClientboundPlayerPositionPacket> teleports = viewer.capture().of(ClientboundPlayerPositionPacket.class);
        for (ClientboundPlayerPositionPacket p : teleports) {
            viewer.player().connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(p.getId()));
        }
        viewer.capture().clear();
    }

    private static CameraJumpPayload jump(double x, double y, double z) {
        return new CameraJumpPayload(new CameraJump(x, y, z, 0, 55));
    }
}
