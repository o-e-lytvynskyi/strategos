package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.onlyUnit;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** The match container in a real server (CORE-01): its holder, its space and its lifecycle. */
public final class MatchTests {
    private MatchTests() {}

    /** CORE-01.4: the holder belongs to this server; a disposed match leaves no runtime and no body behind. */
    @StrategosTest
    public static void holderBoundToServer(GameTestHelper h) {
        StrategosServer server = StrategosServer.get();
        check(server.server().equals(h.getLevel().getServer()), "the holder belongs to another server");
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 3, 4);
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] units = handles(rt);
                    check(units.length == 3, "not spawned yet");
                    for (int handle : units) {
                        body(rt, handle);
                    }
                })
                .thenExecute(() -> {
                    check(
                            server.matches().all().contains(rt) && rt.lifecycle() == MatchLifecycle.RUNNING,
                            "the running match is not listed");
                    server.matches().dispose(rt.id());
                    check(
                            server.matches().get(rt.id()) == null && rt.lifecycle() == MatchLifecycle.DISPOSED,
                            "the disposed match is still registered, " + rt.lifecycle());
                    AABB bounds = h.getBounds();
                    check(
                            h.getLevel()
                                    .getEntities(
                                            EntityTypeTest.forClass(UnitEntity.class),
                                            b -> bounds.contains(b.position()))
                                    .isEmpty(),
                            "unit bodies outlived their match");
                })
                .thenSucceed();
    }

    /** CORE-01.3: a match closer than 2 blocks to another is refused, naming it; 2 blocks apart is fine. */
    @StrategosTest
    public static void overlapRejected(GameTestHelper h) {
        MatchOptions options = MatchFixture.options(SimHarness.DEFAULT_SEED, h);
        MatchRuntime first = MatchFixture.create(h, 0, 0, 12, 32, options);
        try {
            MatchRuntime near = MatchFixture.create(h, 13, 0, 12, 32, options); // a gap of 1 block
            check(false, "a match 1 block from " + first.id() + " was created as " + near.id());
        } catch (MatchRefusedException e) {
            check(
                    String.valueOf(e.getMessage()).contains(first.id().toString()),
                    "the refusal does not name " + first.id() + ": " + e.getMessage());
        }
        MatchRuntime apart = MatchFixture.create(h, 14, 0, 12, 32, options); // a gap of 2 blocks
        check(apart.lifecycle() == MatchLifecycle.RUNNING, "the match 2 blocks away is " + apart.lifecycle());
        h.succeed();
    }

    /** CORE-01: a paused match keeps its tick and its bodies still for 20 ticks; resumed, both move on. */
    @StrategosTest
    public static void pauseFreezesResumeContinues(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 4.5, 8.5, 1, 0);
        var state = new Object() {
            long tick;
            double x;
        };
        h.startSequence()
                .thenWaitUntil(() -> body(rt, onlyUnit(rt)))
                .thenExecute(() -> move(rt, onlyUnit(rt), 28.5, 8.5))
                .thenWaitUntil(() -> check(unit(rt, onlyUnit(rt)).state() == UnitState.WALKING, "not walking yet"))
                .thenIdle(5)
                .thenExecute(() -> {
                    StrategosServer.get().matches().pause(rt.id());
                    state.tick = core(rt).queries().tick();
                    state.x = body(rt, onlyUnit(rt)).getX();
                })
                .thenExecuteFor(20, () -> {
                    check(core(rt).queries().tick() == state.tick, "a paused match stepped");
                    check(body(rt, onlyUnit(rt)).getX() == state.x, "a paused match's body moved");
                })
                .thenExecute(() -> StrategosServer.get().matches().resume(rt.id()))
                .thenExecuteAfter(3, () -> {
                    check(core(rt).queries().tick() > state.tick, "the resumed match does not step");
                    check(body(rt, onlyUnit(rt)).getX() > state.x, "the resumed match's body does not move");
                })
                .thenSucceed();
    }
}
