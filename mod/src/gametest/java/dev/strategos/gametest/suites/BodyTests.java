package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.assertOnCorePose;
import static dev.strategos.gametest.suites.Units.bodiesOf;
import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.onlyUnit;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.GameTestTickets;
import dev.strategos.gametest.LogCapture;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.registry.ModEntityTypes;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The unit body as a view of its core unit (UNIT-01, CORE-06): made, posed, never saved, re-made, never pushed. */
public final class BodyTests {
    private BodyTests() {}

    @StrategosTest
    public static void materialisesOneBodyPerUnit(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 10, 6);
        h.runAfterDelay(2, () -> {
            int[] units = handles(rt);
            check(units.length == 10, units.length + " units, not 10");
            List<UnitEntity> bodies = bodiesOf(h.getLevel(), rt);
            check(
                    bodies.size() == 10 && rt.bodies().count() == 10,
                    bodies.size() + " bodies in the level and " + rt.bodies().count() + " registered, not 10");
            Set<Integer> shown = new TreeSet<>();
            for (UnitEntity body : bodies) {
                shown.add(body.spawnData().handle());
            }
            check(shown.size() == 10, "two bodies show the same unit: " + shown);
            for (int handle : units) {
                UnitEntity body = body(rt, handle);
                check(body.isBound() && shown.contains(handle), "unit " + handle + " has no bound body in the level");
                assertOnCorePose(rt, body, handle, 1e-9);
            }
            h.succeed();
        });
    }

    @StrategosTest
    public static void bodyNeverSaved(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 1, 0);
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> {
                    UnitEntity body = body(rt, onlyUnit(rt));
                    check(body.getEncodeId() == null, "a unit body has the encode id " + body.getEncodeId());
                    check(!body.save(new CompoundTag()), "a unit body saved itself");
                })
                .thenSucceed();
    }

    /** CORE-06.2: a body discarded by something else is back next tick, a new entity on the core pose, logged once. */
    @StrategosTest
    public static void discardedBodyRematerialises(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 1, 0);
        var state = new Object() {
            UnitEntity old;
            LogCapture log;
        };
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> {
                    state.old = body(rt, onlyUnit(rt));
                    state.log = LogCapture.of(rt.id());
                    state.old.discard();
                })
                .thenExecuteAfter(1, () -> {
                    try {
                        int handle = onlyUnit(rt);
                        List<UnitEntity> bodies = bodiesOf(h.getLevel(), rt, handle);
                        check(bodies.size() == 1, bodies.size() + " bodies for the unit, not 1");
                        UnitEntity again = bodies.get(0);
                        check(
                                again.getId() != state.old.getId() && state.old.isRemoved(),
                                "the discarded body is still the unit's body");
                        assertOnCorePose(rt, again, handle, 1.0 / 16);
                        List<String> lines = state.log.lines();
                        check(
                                lines.size() == 1 && lines.get(0).contains("left the level"),
                                "expected one log line about the lost body, got " + lines);
                    } finally {
                        state.log.close();
                    }
                })
                .thenSucceed();
    }

    /** CORE-06.3: unloaded with its chunk, a body leaves its unit bodiless and walking; nothing is logged. */
    @StrategosTest
    public static void unloadedBodyStaysBodiless(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 8.5, 1, 0);
        var state = new Object() {
            double x;
            LogCapture log;
        };
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> move(rt, onlyUnit(rt), 28.5, 8.5))
                .thenWaitUntil(() -> check(unit(rt, onlyUnit(rt)).state() == UnitState.WALKING, "not walking yet"))
                .thenExecute(() -> {
                    int handle = onlyUnit(rt);
                    state.x = unit(rt, handle).x();
                    state.log = LogCapture.of(rt.id());
                    body(rt, handle).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                })
                .thenExecuteFor(20, () -> {
                    int handle = onlyUnit(rt);
                    check(
                            rt.bodies().get(handle) == null
                                    && bodiesOf(h.getLevel(), rt, handle).isEmpty(),
                            "the unit got a body back although its arena still ticks");
                    check(unit(rt, handle).state() == UnitState.WALKING, "the bodiless unit stopped walking");
                })
                .thenExecute(() -> {
                    try {
                        check(unit(rt, onlyUnit(rt)).x() > state.x + 4, "the bodiless unit did not walk on");
                        check(state.log.lines().isEmpty(), "a parked unit was logged: " + state.log.lines());
                    } finally {
                        state.log.close();
                    }
                })
                .thenSucceed();
    }

    /** Only a body registry makes bodies: a stray unbound body never enters the level. */
    @StrategosTest
    public static void strayBodyRefused(GameTestHelper h) {
        UnitEntity stray = ModEntityTypes.UNIT_HUMANOID.get().create(h.getLevel());
        check(stray != null, "the unit body type cannot be created here");
        Vec3 at = h.absoluteVec(new Vec3(16.5, 1, 16.5));
        stray.moveTo(at.x, at.y, at.z, 0, 0);

        boolean added = h.getLevel().addFreshEntity(stray);

        check(!added, "an unbound unit body was added to the level");
        check(h.getLevel().getEntity(stray.getId()) == null, "an unbound unit body is in the level");
        h.succeed();
    }

    /** CORE-06.4: /tp moves a walking unit's body, and the next pose puts it back where the core says. */
    @StrategosTest
    public static void teleportOverwrittenByNextPose(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 8.5, 1, 0);
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> move(rt, onlyUnit(rt), 28.5, 8.5))
                .thenWaitUntil(() -> check(unit(rt, onlyUnit(rt)).state() == UnitState.WALKING, "not walking yet"))
                .thenExecute(() -> {
                    UnitEntity body = body(rt, onlyUnit(rt));
                    double before = body.getX();
                    MinecraftServer server = h.getLevel().getServer();
                    // The arena lies millions of blocks out, where Double.toString writes 1.13E7, which /tp rejects.
                    server.getCommands()
                            .performPrefixedCommand(
                                    server.createCommandSourceStack().withSuppressedOutput(),
                                    String.format(
                                            Locale.ROOT,
                                            "tp %s %.6f %.6f %.6f",
                                            body.getStringUUID(),
                                            before + 5,
                                            body.getY(),
                                            body.getZ()));
                    check(Math.abs(body.getX() - (before + 5)) < 1e-6, "/tp did not move the body");
                })
                .thenExecuteAfter(1, () -> {
                    int handle = onlyUnit(rt);
                    assertOnCorePose(rt, body(rt, handle), handle, 1e-9);
                })
                .thenSucceed();
    }

    /**
     * UNIT-01.2. Controls first (README): a pig walking past a sculk sensor wakes it, and two pigs on nearly one point
     * push apart. Then, with the pigs gone: a sensor 1 block from a unit's path stays inactive, two units on one point
     * keep their exact core positions, and hurting a body does nothing. The two sensors are more than 8 blocks (a
     * sensor's range) from everything of the other part.
     */
    @StrategosTest(timeoutTicks = 300)
    public static void noVibrationsNoPush(GameTestHelper h) {
        BlockPos controlSensor = new BlockPos(2, 1, 16);
        BlockPos unitSensor = new BlockPos(22, 1, 9); // centre z 9.5: 1 block from the walker's path at z 8.5
        h.setBlock(controlSensor, Blocks.SCULK_SENSOR);
        h.setBlock(unitSensor, Blocks.SCULK_SENSOR);
        MatchRuntime rt = MatchFixture.create(h, 12, 0, 20, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        Pig walker = h.spawn(EntityType.PIG, new Vec3(1.5, 1, 10.5));
        Vec3 end = h.absoluteVec(new Vec3(1.5, 1, 22.5));
        walker.getNavigation().moveTo(end.x, end.y, end.z, 1.0);
        // Entity#push ignores two entities closer than 0.01 on both axes, so "one point" is 0.1 apart.
        Pig left = h.spawn(EntityType.PIG, new Vec3(6.5, 1, 27.5));
        Pig right = h.spawn(EntityType.PIG, new Vec3(6.6, 1, 27.5));
        var state = new Object() {
            boolean sensorWoke;
            boolean pigsPushed;
            int walkerUnit;
            List<Integer> stacked = new ArrayList<>();
            List<Vec3> stackedAt = new ArrayList<>();
            float health;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    state.sensorWoke |= phase(h, controlSensor) != SculkSensorPhase.INACTIVE;
                    state.pigsPushed |= left.distanceTo(right) > 0.5;
                    check(
                            state.sensorWoke && state.pigsPushed,
                            "controls: sensor woke " + state.sensorWoke + ", pigs pushed apart " + state.pigsPushed);
                })
                .thenExecute(() -> {
                    walker.discard();
                    left.discard();
                    right.discard();
                    check(phase(h, unitSensor) == SculkSensorPhase.INACTIVE, "the pigs woke the unit's sensor");
                    spawn(rt, 0.5, 8.5, 1, 0); // relative x 12.5: more than 8 blocks from the unit's sensor
                    spawn(rt, 10.5, 24.5, 2, 0);
                })
                .thenWaitUntil(() -> {
                    int[] all = handles(rt);
                    check(all.length == 3, all.length + " units, not 3");
                    for (int handle : all) {
                        body(rt, handle);
                    }
                })
                .thenExecute(() -> {
                    int[] all = handles(rt);
                    for (int handle : all) {
                        UnitView unit = unit(rt, handle);
                        if (unit.z() < 16) {
                            state.walkerUnit = handle;
                        } else {
                            state.stacked.add(handle);
                            state.stackedAt.add(new Vec3(unit.x(), unit.y(), unit.z()));
                        }
                    }
                    check(state.stacked.size() == 2, "the two units on one point are " + state.stacked);
                    move(rt, state.walkerUnit, 19.5, 8.5);
                })
                .thenExecuteFor(85, () -> {
                    check(phase(h, unitSensor) == SculkSensorPhase.INACTIVE, "a walking unit woke a sculk sensor");
                    for (int i = 0; i < 2; i++) {
                        int handle = state.stacked.get(i);
                        UnitView unit = unit(rt, handle);
                        check(
                                new Vec3(unit.x(), unit.y(), unit.z()).equals(state.stackedAt.get(i)),
                                "a unit on a shared point moved in the core");
                        assertOnCorePose(rt, body(rt, handle), handle, 0);
                    }
                })
                .thenExecute(() -> {
                    UnitView walked = unit(rt, state.walkerUnit);
                    check(
                            walked.state() == UnitState.IDLE && walked.x() == 19.5,
                            "the unit did not walk past the sensor: " + walked);
                    int handle = state.stacked.get(0);
                    UnitEntity body = body(rt, handle);
                    state.health = body.getHealth();
                    check(!body.hurt(h.getLevel().damageSources().generic(), 5), "hurting a unit body succeeded");
                    check(body.getHealth() == state.health, "hurting a unit body changed its health");
                    assertOnCorePose(rt, body, handle, 0);
                })
                .thenSucceed();
    }

    private static SculkSensorPhase phase(GameTestHelper h, BlockPos sensor) {
        return SculkSensorBlock.getPhase(h.getBlockState(sensor));
    }

    /**
     * CORE-06.3 on the engine's own path (spec 1.6 D row 53). A unit walks out of the loaded area and back: its body
     * goes and comes back once. Then the chunk under a standing body really unloads (its ticket is released): the leave
     * event carries no removal reason, nothing is logged, and when the chunk loads again the unit has exactly one
     * body, not the old one shown again beside a new one. The match reaches past the arena, so the test runs alone.
     * The far chunk lies at least three chunks past the arena, whose chunks the GameTest forces (level 31): two chunks
     * from a forced one a chunk stays FULL (level 33) when released, its entities tracked but not ticking, so it never
     * unloads; with a run's random origin, 72 blocks out was two chunks away about half the time. The GameTest server
     * ticks back to back (GameTestServer#waitUntilNextTick only runs tasks), while a forced chunk's entities load on the
     * IO worker in wall-clock time: on CI 1,600 ticks once ran out in 3 s, before they had, so the timeout is in ticks
     * enough for about half a minute there.
     */
    @StrategosTest(batch = "chunks", solo = true, timeoutTicks = 16_000)
    public static void bodiesFollowEntityTickingChunks(GameTestHelper h) {
        AABB arena = h.getBounds();
        MatchSpace space = new MatchSpace(
                h.getLevel().dimension(),
                new BlockPos((int) arena.minX, (int) arena.minY, (int) arena.minZ),
                32,
                96,
                (int) arena.minY,
                (int) arena.maxY - 1);
        MatchRuntime rt = MatchFixture.create(h, space, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        ChunkPos far = new ChunkPos(BlockPos.containing(space.worldX(16), 0, space.worldZ(88)));
        spawn(rt, 16.5, 8.5, 1, 0);
        var state = new Object() {
            LogCapture log;
        };
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> {
                    state.log = LogCapture.of(rt.id());
                    move(rt, onlyUnit(rt), 16.5, 88.5);
                })
                .thenWaitUntil(() -> check(unit(rt, onlyUnit(rt)).z() > 60, "not far out yet"))
                .thenExecute(() -> check(
                        rt.bodies().get(onlyUnit(rt)) == null
                                && bodiesOf(h.getLevel(), rt).isEmpty(),
                        "a body stands where entities do not tick"))
                .thenWaitUntil(() -> check(unit(rt, onlyUnit(rt)).state() == UnitState.IDLE, "still walking out"))
                .thenExecute(() -> move(rt, onlyUnit(rt), 16.5, 8.5))
                .thenWaitUntil(() -> check(
                        unit(rt, onlyUnit(rt)).state() == UnitState.IDLE
                                && unit(rt, onlyUnit(rt)).z() == 8.5,
                        "still walking back"))
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> {
                    check(bodiesOf(h.getLevel(), rt, onlyUnit(rt)).size() == 1, "two bodies after the walk back");
                    GameTestTickets.forceTicking(h.getLevel(), far);
                })
                .thenWaitUntil(() -> check(
                        h.getLevel().isPositionEntityTicking(far.getMiddleBlockPosition(0)),
                        "the far chunk does not tick entities yet"))
                .thenExecute(() -> move(rt, onlyUnit(rt), 16.5, 88.5))
                .thenWaitUntil(() -> check(
                        unit(rt, onlyUnit(rt)).state() == UnitState.IDLE
                                && unit(rt, onlyUnit(rt)).z() == 88.5,
                        "still walking to the far chunk"))
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> GameTestTickets.release(h.getLevel(), far))
                .thenWaitUntil(() -> check(rt.bodies().get(onlyUnit(rt)) == null, "the far chunk still holds the body"))
                .thenIdle(10)
                .thenExecute(() -> GameTestTickets.forceTicking(h.getLevel(), far))
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        int handle = onlyUnit(rt);
                        check(
                                bodiesOf(h.getLevel(), rt, handle).size() == 1,
                                "the reloaded chunk shows "
                                        + bodiesOf(h.getLevel(), rt, handle).size() + " bodies");
                        check(state.log.lines().isEmpty(), "a lost body was logged: " + state.log.lines());
                    } finally {
                        state.log.close();
                        GameTestTickets.release(h.getLevel(), far);
                    }
                })
                .thenSucceed();
    }

    /** CORE-06.4: /kill and vanilla damage leave M0 units and their bodies untouched (spec 1.5). */
    @StrategosTest
    public static void vanillaKillIgnored(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 1, 0);
        var state = new Object() {
            UnitEntity body;
        };
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> {
                    state.body = body(rt, onlyUnit(rt));
                    state.body.kill();
                })
                .thenExecuteAfter(1, () -> {
                    int handle = onlyUnit(rt);
                    check(
                            !state.body.isRemoved()
                                    && state.body.equals(rt.bodies().get(handle)),
                            "the killed body is gone");
                    check(unit(rt, handle).state() == UnitState.IDLE, "the unit changed");
                })
                .thenSucceed();
    }

    /** UNIT-01: an idle body is never despawned. Batch slow: only runGameTestSlow (verifySlow) runs it. */
    @StrategosTest(batch = "slow", timeoutTicks = 6_100)
    public static void idleBodyPersists6000Ticks(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 1, 0);
        var state = new Object() {
            UnitEntity body;
        };
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> state.body = body(rt, onlyUnit(rt)))
                .thenExecuteAfter(6_000, () -> {
                    check(
                            !state.body.isRemoved()
                                    && state.body.equals(rt.bodies().get(onlyUnit(rt)))
                                    && state.body.equals(h.getLevel().getEntity(state.body.getId())),
                            "the idle body did not last 6,000 ticks");
                })
                .thenSucceed();
    }
}
