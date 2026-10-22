package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.assertOnCorePose;
import static dev.strategos.gametest.suites.Units.bodiesOf;
import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.testkit.JournalFiles;
import dev.strategos.core.testkit.ReplayRunner;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.ThrowingMetrics;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.FaultPolicy;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.tick.MatchFaultException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Fault containment in a real server (CORE-10, 04 §2.4): a fault stops its own match, leaves its report, and the
 * other match steps on. Each test sets CONTAIN on its matches, since GameTest runs rethrow by default.
 */
public final class FaultTests {
    private static final Vec2d TARGET = new Vec2d(6.5, 28.5);

    private FaultTests() {}

    private static MatchOptions contain(GameTestHelper h) {
        return MatchFixture.options(SimHarness.DEFAULT_SEED, h).withFaultPolicy(FaultPolicy.CONTAIN);
    }

    /** A in the arena's west 12 columns, B in its east 14, six blocks apart. */
    private static MatchRuntime west(GameTestHelper h, MatchOptions options) {
        return MatchFixture.create(h, 0, 0, 12, 32, options);
    }

    private static MatchRuntime east(GameTestHelper h) {
        return MatchFixture.create(h, 18, 0, 14, 32, contain(h));
    }

    private static boolean ready(MatchRuntime rt) {
        int[] units = handles(rt);
        return units.length == 3 && rt.bodies().count() == 3;
    }

    private static void walk(MatchRuntime rt) {
        rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handles(rt)), TARGET));
    }

    /**
     * The report of a fault at {@code tick} holds its three files, names where it happened, and its journal replays
     * to the hash the match had when it faulted.
     */
    private static void checkReport(MatchRuntime rt, long tick, String where) {
        Path dir = StrategosServer.get().faults().faultDir().resolve(rt.id() + "-" + tick);
        for (String file : List.of("fault.txt", "snapshot.json", "journal.strj")) {
            check(Files.isRegularFile(dir.resolve(file)), "the fault report lacks " + file + ": " + dir);
        }
        try {
            String text = Files.readString(dir.resolve("fault.txt"), StandardCharsets.UTF_8);
            check(text.contains("where " + where + "\n") && text.contains("injected"), "fault.txt says: " + text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        JournalExport export = JournalFiles.read(dir.resolve("journal.strj"));
        ReplayRunner.ReplayReport replay =
                ReplayRunner.check(export.journal(), TemplateSet.builtin(), export.liveHash());
        check(replay.ok(), "the fault's journal does not replay: " + replay.message());
    }

    private static Map<Integer, Vec3> poses(GameTestHelper h, MatchRuntime rt) {
        Map<Integer, Vec3> poses = new HashMap<>();
        for (UnitEntity body : bodiesOf(h.getLevel(), rt)) {
            poses.put(body.getId(), body.position());
        }
        return poses;
    }

    /** CORE-10.1 (04 §4.8): A's metrics throw at tick 50; A is FAULTED with its report and frozen bodies, B walks on. */
    @StrategosTest
    public static void faultContainedOtherMatchContinues(GameTestHelper h) {
        MatchRuntime a = west(h, contain(h).withMetrics(new ThrowingMetrics(50)));
        MatchRuntime b = east(h);
        spawn(a, 4, 4, 3, 1);
        spawn(b, 4, 4, 3, 1);
        var state = new Object() {
            Map<Integer, Vec3> frozen = Map.of();
        };
        h.startSequence()
                .thenWaitUntil(() -> check(ready(a) && ready(b), "the units and their bodies are not there yet"))
                .thenExecute(() -> {
                    walk(a);
                    walk(b);
                })
                .thenWaitUntil(() -> check(a.lifecycle() == MatchLifecycle.FAULTED, "A has not faulted yet"))
                .thenExecute(() -> {
                    check(a.tick() == 50, "A faulted at tick " + a.tick() + ", not 50");
                    check(String.valueOf(a.faultMessage()).contains("injected fault at tick 50"), a.faultMessage());
                    checkReport(a, 50, "tick");
                    state.frozen = poses(h, a);
                    check(state.frozen.size() == 3, state.frozen.size() + " bodies of A after the fault");
                })
                .thenWaitUntil(() -> check(core(b).queries().tick() >= 60, "B is not at tick 60 yet"))
                .thenExecute(() -> {
                    check(a.tick() == 50, "the faulted match stepped on to tick " + a.tick());
                    check(poses(h, a).equals(state.frozen), "a body of the faulted match moved");
                    for (int handle : handles(b)) {
                        check(unit(b, handle).z() > 8, "a unit of B did not walk: " + unit(b, handle));
                        assertOnCorePose(b, body(b, handle), handle, 1.0 / 16);
                    }
                })
                .thenSucceed();
    }

    /** CORE-10.2: a fault in UnitEntity's server override, here {@code hurt}, is contained the same way. */
    @StrategosTest
    public static void unitEntityOverrideFaultContained(GameTestHelper h) {
        MatchRuntime a = west(h, contain(h));
        MatchRuntime b = east(h);
        spawn(a, 4, 4, 3, 1);
        spawn(b, 4, 4, 3, 1);
        var state = new Object() {
            long faultTick;
            long bTick;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(ready(a) && ready(b), "the units and their bodies are not there yet"))
                .thenExecute(() -> {
                    walk(b);
                    DamageSource throwing = new DamageSource(
                            h.getLevel().damageSources().generic().typeHolder()) {
                        @Override
                        public String getMsgId() {
                            throw new IllegalStateException("injected fault in getMsgId");
                        }
                    };
                    UnitEntity victim = body(a, handles(a)[0]);
                    check(!victim.hurt(throwing, 1.0F), "a unit body took damage");
                    check(a.lifecycle() == MatchLifecycle.FAULTED, "A is " + a.lifecycle() + " after the hurt");
                    state.faultTick = a.tick();
                    state.bTick = core(b).queries().tick();
                    checkReport(a, state.faultTick, "hurt");
                })
                .thenExecuteAfter(10, () -> {
                    check(a.tick() == state.faultTick, "the faulted match stepped on");
                    check(core(b).queries().tick() >= state.bTick + 10, "B stopped stepping");
                })
                .thenSucceed();
    }

    /** CORE-10.2: a fault inside an output applier (a command's reply) is contained the same way. */
    @StrategosTest
    public static void outputApplierFaultContained(GameTestHelper h) {
        MatchRuntime a = west(h, contain(h));
        MatchRuntime b = east(h);
        spawn(a, 4, 4, 3, 1);
        spawn(b, 4, 4, 3, 1);
        var state = new Object() {
            long bTick;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(ready(a) && ready(b), "the units and their bodies are not there yet"))
                .thenExecute(() -> {
                    walk(b);
                    a.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handles(a)), TARGET), reply -> {
                        throw new IllegalStateException("injected fault in a reply");
                    });
                })
                .thenWaitUntil(() -> check(a.lifecycle() == MatchLifecycle.FAULTED, "A has not faulted yet"))
                .thenExecute(() -> {
                    check(String.valueOf(a.faultMessage()).contains("injected fault in a reply"), a.faultMessage());
                    checkReport(a, a.tick(), "tick");
                    state.bTick = core(b).queries().tick();
                })
                .thenExecuteAfter(10, () -> check(core(b).queries().tick() >= state.bTick + 10, "B stopped stepping"))
                .thenSucceed();
    }

    /** CORE-10.3: under RETHROW the fault is rethrown, and only after its report was written and the match faulted. */
    @StrategosTest
    public static void rethrowAfterRecord(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withFaultPolicy(FaultPolicy.RETHROW));
        h.startSequence()
                .thenWaitUntil(() -> check(rt.tick() >= 5, "the match has not stepped yet"))
                .thenExecute(() -> {
                    long tick = rt.tick();
                    MatchFaultException rethrown = null;
                    try {
                        StrategosServer.get().faults().run(rt, "test", () -> {
                            throw new IllegalStateException("injected fault, rethrown");
                        });
                    } catch (MatchFaultException e) {
                        rethrown = e;
                    }
                    check(rethrown != null, "the RETHROW match did not rethrow");
                    check(rt.lifecycle() == MatchLifecycle.FAULTED, "the match is " + rt.lifecycle());
                    checkReport(rt, tick, "test");
                })
                .thenSucceed();
    }
}
