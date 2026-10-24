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

    static final dev.strategos.core.shared.TemplateId CENTRE =
            new dev.strategos.core.shared.TemplateId("strategos:building/civic_centre");

    /** MATCH-01.1: the first-loop scenario, set up from two start slots, leaves exactly the entities it lists. */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 200)
    public static void aScenarioLoadsExactlyItsEntities(GameTestHelper h) {
        MatchRuntime rt = match(h);
        var server = h.getLevel().getServer();
        var scenario = dev.strategos.mod.server.content.Scenarios.load(
                server, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("strategos", "first_loop"));
        dev.strategos.mod.server.content.Scenarios.apply(
                rt,
                scenario,
                java.util.List.of(
                        new dev.strategos.core.content.StartSlot(12, 0, 12, 0),
                        new dev.strategos.core.content.StartSlot(36, 0, 36, 180)),
                core(rt).queries().templates());
        h.startSequence()
                .thenExecuteAfter(10, () -> {
                    var q = core(rt).queries();
                    check(q.buildings().size() == 2, q.buildings().size() + " buildings");
                    for (int f = 0; f < 2; f++) {
                        int faction = f;
                        check(
                                q.buildings().stream()
                                        .filter(b -> b.faction() == faction)
                                        .allMatch(b -> b.template().equals(CENTRE)),
                                "faction " + f + "'s building is no centre");
                        int[] units = q.unitHandlesOf(new dev.strategos.core.shared.FactionSlot(f));
                        check(units.length == 5, "faction " + f + " has " + units.length + " units");
                        for (int u : units) {
                            check(
                                    java.util.Objects.requireNonNull(q.unit(u))
                                            .template()
                                            .equals(dev.strategos.core.content.TemplateSet.WORKER),
                                    "a unit of faction " + f + " is no worker");
                        }
                        long food = q.treasury(new dev.strategos.core.shared.FactionSlot(f))
                                .stock()
                                .get(0);
                        check(food == 300_000, "faction " + f + " starts with " + food + " food");
                    }
                    check(
                            q.config().rules().play().victory()
                                    == dev.strategos.core.sim.PlayRules.Victory.CIVIC_CENTRE,
                            "the victory rule is " + q.config().rules().play().victory());
                })
                .thenSucceed();
    }

    /**
     * MATCH-01.4: a zombie's spawn check fails anywhere in a match's space, while one outside it is left to vanilla's
     * rules, as is a zombie from a spawn egg inside.
     */
    @StrategosTest(timeoutTicks = 100)
    public static void noHostileSpawnsInsideAMatch(GameTestHelper h) {
        MatchFixture.create(h, 0, 0, 24, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        h.startSequence()
                .thenExecute(() -> {
                    var level = h.getLevel();
                    var inside = new net.minecraft.world.entity.monster.Zombie(level);
                    var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(10.5, 2, 10.5));
                    inside.setPos(at.x, at.y, at.z);
                    var outside = new net.minecraft.world.entity.monster.Zombie(level);
                    var out = h.absoluteVec(new net.minecraft.world.phys.Vec3(28.5, 2, 10.5));
                    outside.setPos(out.x, out.y, out.z);
                    check(
                            result(inside, net.minecraft.world.entity.MobSpawnType.NATURAL)
                                    == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result
                                            .FAIL,
                            "a natural spawn in the match is not refused");
                    check(
                            result(inside, net.minecraft.world.entity.MobSpawnType.SPAWNER)
                                    == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result
                                            .FAIL,
                            "a spawner's spawn in the match is not refused");
                    check(
                            result(outside, net.minecraft.world.entity.MobSpawnType.NATURAL)
                                    == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result
                                            .DEFAULT,
                            "a spawn outside every match is not left to vanilla");
                    check(
                            result(inside, net.minecraft.world.entity.MobSpawnType.SPAWN_EGG)
                                    == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result
                                            .DEFAULT,
                            "a spawn egg in the match is refused");
                    check(
                            !net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(
                                    inside, level, net.minecraft.world.entity.MobSpawnType.NATURAL),
                            "vanilla's own check let a zombie spawn in the match");
                })
                .thenSucceed();
    }

    private static net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result result(
            net.minecraft.world.entity.Mob mob, net.minecraft.world.entity.MobSpawnType type) {
        var event = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(
                mob, (net.minecraft.server.level.ServerLevel) mob.level(), type, null);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        return event.getResult();
    }

    /**
     * MATCH-01.5: two seated players, faction 1 resigns: both hear it defeated, and the match's end with faction 0 as
     * its winner and its duration; the match steps no more.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 300)
    public static void matchEventReachesEveryParticipant(GameTestHelper h) {
        MatchRuntime rt = match(h);
        var admin = dev.strategos.core.shared.PlayerSlot.ADMIN;
        rt.submit(
                admin,
                new dev.strategos.core.protocol.SpawnBuilding(
                        CENTRE, new dev.strategos.core.shared.FactionSlot(0), 4, 4, 0));
        rt.submit(
                admin,
                new dev.strategos.core.protocol.SpawnBuilding(
                        CENTRE, new dev.strategos.core.shared.FactionSlot(1), 30, 30, 0));
        rt.submit(admin, new dev.strategos.core.protocol.RuleChange("victory", "civic_centre"));
        var a = BuildingTests.seated(h, rt, "strategos-news-a", 0);
        var b = BuildingTests.seated(h, rt, "strategos-news-b", 1);
        long[] ended = {-1};
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().buildings().size() == 2, "no centres yet"))
                .thenExecuteAfter(
                        5,
                        () -> rt.submit(
                                new dev.strategos.core.shared.PlayerSlot(1),
                                new dev.strategos.core.protocol.Resign(new dev.strategos.core.shared.FactionSlot(1))))
                .thenWaitUntil(() -> check(rt.lifecycle() == MatchLifecycle.ENDED, "not ended: " + rt.lifecycle()))
                .thenExecuteAfter(2, () -> {
                    for (var viewer : java.util.List.of(a, b)) {
                        viewer.poll();
                        java.util.List<dev.strategos.core.protocol.MatchNews> news = new java.util.ArrayList<>();
                        for (var p : viewer.capture()
                                .of(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class)) {
                            if (p.payload() instanceof dev.strategos.mod.server.net.payload.MatchEventPayload m
                                    && m.news().matchId() == rt.id().value()) {
                                news.add(m.news());
                            }
                        }
                        check(news.size() == 2, viewer.player().getName().getString() + " heard " + news);
                        check(
                                news.get(0).kind() == dev.strategos.core.protocol.MatchNews.DEFEATED
                                        && news.get(0).faction() == 1,
                                "first " + news.get(0));
                        check(
                                news.get(1).kind() == dev.strategos.core.protocol.MatchNews.ENDED
                                        && news.get(1).faction() == 0
                                        && news.get(1).ticks() == news.get(1).tick()
                                        && news.get(1).ticks() > 0,
                                "then " + news.get(1));
                    }
                    ended[0] = core(rt).queries().tick();
                    a.close();
                    b.close();
                })
                .thenExecuteAfter(
                        10,
                        () -> check(
                                core(rt).queries().tick() == rt.tick() && rt.lifecycle() == MatchLifecycle.ENDED,
                                "an ended match stepped on"))
                .thenSucceed();
    }
}
