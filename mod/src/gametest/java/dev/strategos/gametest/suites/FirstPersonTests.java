package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.match;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.ModeSwitch;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.body.AgentTracker;
import dev.strategos.mod.server.commander.CommanderService;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * First person and back (CAM-05, D-08, 04 §13.7) with mock commanders: a {@code mode_switch} makes the commander an
 * observer in adventure mode at its drop point, which nothing hurts, which cannot attack a body and which units pass
 * through; the next one makes it the RTS camera again above where it stood.
 */
public final class FirstPersonTests {
    private static final Logger LOG = LoggerFactory.getLogger(FirstPersonTests.class);
    /** {@code CommanderService}'s cooldown between two switches, and a tick. */
    private static final int AFTER_COOLDOWN = 101;

    private FirstPersonTests() {}

    /**
     * CAM-05.1: to first person, the commander is an observer in adventure mode at the drop point's floor; damage does
     * not reach it, an attack on a body is cancelled and it is no avoidance agent; back, it is a spectator 24 blocks
     * above where it stood, commanding again.
     */
    @StrategosTest(timeoutTicks = 300)
    public static void toFirstPersonAndBack(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer viewer = commander(h, rt, "strategos-fp1");
        CommanderService commanders = StrategosServer.get().commanders();
        MatchSpace s = rt.space();
        double x = s.origin().getX() + 10.5;
        double z = s.origin().getZ() + 12.5;
        int floor = (int) Math.floor(h.getBounds().minY) + 1;
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(20, 20), 1, 0));
        h.startSequence()
                .thenWaitUntil(() -> check(!Units.bodiesOf(h.getLevel(), rt).isEmpty(), "the body is not there yet"))
                .thenExecute(() -> {
                    accept(viewer);
                    viewer.send(new ModeSwitchPayload(new ModeSwitch(true, x, z)));
                })
                .thenExecuteAfter(2, () -> {
                    ServerPlayer p = viewer.player();
                    check(
                            p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                            "the observer is in " + p.gameMode.getGameModeForPlayer());
                    check(commanders.observes(p) && !commanders.commands(p), "observes, does not command");
                    check(
                            Math.abs(p.getX() - x) < 1e-6
                                    && Math.abs(p.getZ() - z) < 1e-6
                                    && Math.abs(p.getY() - floor) < 1e-6,
                            "the observer stands at " + p.position() + ", not on the floor at " + x + ", " + z);
                    float health = p.getHealth();
                    p.hurt(p.damageSources().generic(), 5);
                    check(p.getHealth() == health, "the observer took damage: " + health + " to " + p.getHealth());
                    List<UnitEntity> bodies = Units.bodiesOf(h.getLevel(), rt);
                    check(!bodies.isEmpty(), "no body to attack");
                    check(
                            NeoForge.EVENT_BUS
                                    .post(new AttackEntityEvent(p, bodies.get(0)))
                                    .isCanceled(),
                            "an observer's attack on a body was not cancelled");
                    AgentUpdate agents =
                            new AgentTracker(s).track(h.getLevel(), rt.roster().members());
                    check(
                            agents == null || agents.agents().stream().noneMatch(a -> a.id() == p.getId()),
                            "the observer is an avoidance agent");
                })
                .thenExecuteAfter(AFTER_COOLDOWN, () -> viewer.send(new ModeSwitchPayload(ModeSwitch.BACK)))
                .thenExecuteAfter(2, () -> {
                    ServerPlayer p = viewer.player();
                    check(p.isSpectator() && commanders.commands(p), "back, it does not command");
                    check(!commanders.observes(p), "back, it still observes");
                    check(
                            Math.abs(p.getY() - (floor + 24)) < 1e-6,
                            "back, the camera is at " + p.position() + ", not 24 above the floor");
                    viewer.close();
                })
                .thenSucceed();
    }

    /**
     * CAM-05.2: a drop point outside the space or on water is refused, so is a request within a second of the last,
     * and a switch back within the 5 s cooldown; the commander stays where it is.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void dropPointsAndCooldown(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer viewer = commander(h, rt, "strategos-fp2");
        CommanderService commanders = StrategosServer.get().commanders();
        MatchSpace s = rt.space();
        double inX = s.origin().getX() + 10.5;
        double inZ = s.origin().getZ() + 10.5;
        h.setBlock(new BlockPos(5, 1, 5), Blocks.WATER.defaultBlockState());
        BlockPos water = h.absolutePos(new BlockPos(5, 1, 5));
        java.util.UUID id = viewer.player().getUUID();
        h.startSequence()
                .thenExecute(() -> {
                    accept(viewer);
                    viewer.send(new ModeSwitchPayload(
                            new ModeSwitch(true, s.origin().getX() - 10, inZ)));
                    viewer.send(new ModeSwitchPayload(new ModeSwitch(true, inX, inZ))); // a second within a second
                })
                .thenExecuteAfter(
                        21,
                        () -> viewer.send(
                                new ModeSwitchPayload(new ModeSwitch(true, water.getX() + 0.5, water.getZ() + 0.5))))
                .thenExecuteAfter(21, () -> {
                    check(commanders.switchesRefused(id) == 3, "refused " + commanders.switchesRefused(id));
                    check(commanders.commands(viewer.player()), "a refused switch moved the commander");
                    viewer.send(new ModeSwitchPayload(new ModeSwitch(true, inX, inZ)));
                })
                .thenExecuteAfter(21, () -> {
                    check(commanders.observes(viewer.player()), "a valid switch was refused");
                    viewer.send(new ModeSwitchPayload(ModeSwitch.BACK)); // within the cooldown
                })
                .thenExecuteAfter(2, () -> {
                    check(commanders.switchesRefused(id) == 4, "the cooldown did not hold");
                    check(commanders.observes(viewer.player()), "the switch back in the cooldown was applied");
                    viewer.close();
                })
                .thenSucceed();
    }

    /**
     * CAM-05.4: an observer standing in a one-block gap of a wall delays an enemy column of ten through it by less than
     * a second: the column's crossing with it in the gap against the same crossing, the other way, without it.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 1_200)
    public static void observerInAGapDelaysLittle(GameTestHelper h) {
        MatchRuntime rt = match(h);
        for (int x = 2; x < 46; x++) {
            if (x != 24) {
                h.setBlock(new BlockPos(x, 1, 24), Blocks.STONE.defaultBlockState());
                h.setBlock(new BlockPos(x, 2, 24), Blocks.STONE.defaultBlockState());
            }
        }
        StrategosServer.get().matches().rebuildPages(rt); // the wall into the terrain pages
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24.5, 12.5), 10, 2));
        MockViewer viewer = commander(h, rt, "strategos-fp3");
        CommanderService commanders = StrategosServer.get().commanders();
        MatchSpace s = rt.space();
        Vec3 gap = h.absoluteVec(new Vec3(24.5, 1, 24.5));
        var state = new Object() {
            long started;
            long without = -1;
            long with = -1;
            int phase;
        };
        h.onEachTick(() -> {
            accept(viewer);
            long tick = h.getTick();
            switch (state.phase) {
                case 0 -> {
                    if (Units.handles(rt).length == 10 && tick > 20) {
                        order(rt, 24.5, 36.5);
                        state.started = tick;
                        state.phase = 1;
                    }
                }
                case 1 -> {
                    if (arrived(rt, 24.5, 36.5)) {
                        state.without = tick - state.started;
                        viewer.send(new ModeSwitchPayload(new ModeSwitch(true, gap.x, gap.z)));
                        state.phase = 2;
                    }
                }
                case 2 -> {
                    if (commanders.observes(viewer.player())) {
                        check(
                                Math.abs(viewer.player().getX() - gap.x) < 1e-6
                                        && Math.abs(viewer.player().getZ() - gap.z) < 1e-6,
                                "the observer is not in the gap: "
                                        + viewer.player().position());
                        order(rt, 24.5, 12.5);
                        state.started = tick;
                        state.phase = 3;
                    }
                }
                case 3 -> {
                    if (arrived(rt, 24.5, 12.5)) {
                        state.with = tick - state.started;
                        state.phase = 4;
                        LOG.info(
                                "CAM-05.4: the column crossed in {} ticks without the observer, {} with it",
                                state.without,
                                state.with);
                        check(
                                state.with <= state.without + 20,
                                "with the observer in the gap the column took " + state.with + " ticks, without "
                                        + state.without);
                        viewer.close();
                        h.succeed();
                    }
                }
                default -> {}
            }
            check(
                    !commanders.observes(viewer.player())
                            || s.contains(viewer.player().blockPosition()),
                    "the observer left the space");
        });
    }

    private static void order(MatchRuntime rt, double x, double z) {
        rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(Units.handles(rt)), new Vec2d(x, z)));
    }

    /** Every unit idle within 4 blocks of the match-local point. */
    private static boolean arrived(MatchRuntime rt, double x, double z) {
        for (int handle : Units.handles(rt)) {
            UnitView u = core(rt).queries().unit(handle);
            if (u == null || u.state() != UnitState.IDLE || Math.hypot(u.x() - x, u.z() - z) > 4) {
                return false;
            }
        }
        return true;
    }

    /**
     * CAM-07.3: six walkers follow their commander, who stepped down into first person and walks east 0.15 a tick; while
     * it walks they keep behind it, never more than 12 blocks back, and once it stops they wait in their places behind
     * it, each its own, and stay there.
     */
    @StrategosTest(timeoutTicks = 600)
    public static void followersKeepPlacesBehindAWalkingPlayer(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer viewer = commander(h, rt, "strategos-fol");
        MatchSpace s = rt.space();
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(5, 16), 6, 2));
        var state = new Object() {
            int[] units = new int[0];
            int walked;
            double most;
            double[] rest = new double[0];
        };
        h.startSequence()
                .thenWaitUntil(() ->
                        check(Units.bodiesOf(h.getLevel(), rt).size() == 6, "the bodies are not there yet"))
                .thenExecute(() -> {
                    accept(viewer);
                    viewer.send(new ModeSwitchPayload(new ModeSwitch(true, s.worldX(9.5), s.worldZ(16.5))));
                })
                .thenExecuteAfter(2, () -> {
                    check(StrategosServer.get().commanders().observes(viewer.player()), "not in first person");
                    state.units = core(rt).queries().unitHandlesOf(new FactionSlot(0));
                    rt.submit(new PlayerSlot(0), Follow.replace(UnitSet.of(state.units)));
                })
                .thenWaitUntil(() -> {
                    ServerPlayer p = viewer.player();
                    if (state.walked < 100) {
                        state.walked++;
                        p.moveTo(p.getX() + 0.15, p.getY(), p.getZ(), -90, 0); // east, facing it
                    }
                    double leader = s.localX(p.getX());
                    if (state.walked > 40 && state.walked < 100) {
                        for (int unit : state.units) {
                            UnitView u = core(rt).queries().unit(unit);
                            check(u != null && u.x() < leader, "a follower ahead of a player walking east");
                            state.most = Math.max(state.most, leader - u.x());
                        }
                    }
                    check(state.walked >= 100, "still walking");
                })
                .thenExecuteAfter(100, () -> {
                    double leader = s.localX(viewer.player().getX());
                    double lz = s.localZ(viewer.player().getZ());
                    check(state.most < 12, "a follower fell " + state.most + " blocks behind");
                    state.rest = new double[state.units.length * 2];
                    for (int k = 0; k < state.units.length; k++) {
                        UnitView u = core(rt).queries().unit(state.units[k]);
                        check(u != null, "a follower died");
                        check(u.state() == UnitState.IDLE, "a follower is " + u.state() + " once the player stopped");
                        check(
                                leader - u.x() > 1 && leader - u.x() < 8 && Math.abs(u.z() - lz) < 4,
                                "a follower waits at " + u.x() + ", " + u.z() + ", not behind " + leader + ", " + lz);
                        state.rest[2 * k] = u.x();
                        state.rest[2 * k + 1] = u.z();
                    }
                })
                .thenExecuteAfter(40, () -> {
                    for (int k = 0; k < state.units.length; k++) {
                        UnitView u = core(rt).queries().unit(state.units[k]);
                        check(u.x() == state.rest[2 * k] && u.z() == state.rest[2 * k + 1], "a waiting follower moved");
                    }
                    viewer.close();
                })
                .thenSucceed();
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
        for (ClientboundPlayerPositionPacket p : viewer.capture().of(ClientboundPlayerPositionPacket.class)) {
            viewer.player().connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(p.getId()));
        }
        viewer.capture().clear();
    }
}
