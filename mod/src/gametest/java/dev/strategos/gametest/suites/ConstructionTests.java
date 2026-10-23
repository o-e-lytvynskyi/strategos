package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.client.build.ClientPlacement;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRecord;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Buildings placed and raised in a real level (BLD-02, BLD-03): refusals, the finished blocks, sites and the writer. */
public final class ConstructionTests {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final PlayerSlot P0 = new PlayerSlot(0);

    private ConstructionTests() {}

    /** Faction 0's civic centre at (2, 2), 1,000 wood, and {@code workers} workers round (20, 6). */
    static void town(MatchRuntime rt, int workers) {
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 2, 2, 0));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 1_000));
        if (workers > 0) {
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(20, 6), workers, 1.5));
        }
    }

    static List<BuildingView> houses(MatchRuntime rt) {
        return core(rt).queries().buildings().stream()
                .filter(b -> b.template().equals(HOUSE))
                .toList();
    }

    /** The level's blocks over house {@code b}'s blueprint equal it, block for block, and its air is air. */
    static void equalsBlueprint(GameTestHelper h, MatchRuntime rt, BuildingView house) {
        MatchCore core = core(rt);
        int index =
                Objects.requireNonNull(core.queries().templates().byId(HOUSE)).netIndex();
        Blueprint b = Objects.requireNonNull(core.queries().templates().blueprint(index));
        Footprint at = house.footprint();
        BlockWriter writer = StrategosServer.get().writer();
        BlockPos origin = rt.space().origin();
        boolean[] filled = new boolean[b.sizeX() * b.sizeY() * b.sizeZ()];
        for (int k = 0; k < b.count(); k++) {
            filled[(b.x(k) * b.sizeY() + b.y(k)) * b.sizeZ() + b.z(k)] = true;
            BlockPos pos = new BlockPos(
                    origin.getX() + at.worldX(b.x(k), b.z(k)),
                    house.baseY() + b.y(k),
                    origin.getZ() + at.worldZ(b.x(k), b.z(k)));
            BlockState want = writer.state(b.palette().get(b.state(k)));
            check(
                    h.getLevel().getBlockState(pos).equals(want),
                    pos + " is " + h.getLevel().getBlockState(pos) + ", not " + want);
        }
        for (int x = 0; x < b.sizeX(); x++) {
            for (int z = 0; z < b.sizeZ(); z++) {
                for (int y = 0; y < b.sizeY() && b.covers(x, z); y++) {
                    if (!filled[(x * b.sizeY() + y) * b.sizeZ() + z]) {
                        BlockPos pos = new BlockPos(
                                origin.getX() + at.worldX(x, z), house.baseY() + y, origin.getZ() + at.worldZ(x, z));
                        check(h.getLevel().getBlockState(pos).isAir(), pos + " should be air inside the house");
                    }
                }
            }
        }
    }

    /**
     * BLD-02.3: a client that does not know of a log standing where a house would go accepts the placement; the
     * server refuses it as blocked, and the issuer is told so.
     */
    @StrategosTest(batch = "construction", timeoutTicks = 200)
    public static void aPlacementTheServerRefusesHasAReason(GameTestHelper h) {
        h.setBlock(new BlockPos(20, 2, 20), Blocks.OAK_LOG);
        MatchRuntime rt = Units.match(h);
        town(rt, 0);
        var state = new Object() {
            CommandResult result;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        !core(rt).queries().buildings().isEmpty()
                                && !core(rt).queries().nodes().isEmpty(),
                        "not ready"))
                .thenExecute(() -> {
                    MatchCore core = core(rt);
                    int index = Objects.requireNonNull(
                                    core.queries().templates().byId(HOUSE))
                            .netIndex();
                    Blueprint b =
                            Objects.requireNonNull(core.queries().templates().blueprint(index));
                    PlacementRules.World blind = ClientPlacement.world(
                            core.queries().templates(), List.of(), 32, 32, new ClientPlacement.Columns() {
                                @Override
                                public double floor(int x, int z) {
                                    return Math.floor(h.getBounds().minY) + 1;
                                }

                                @Override
                                public boolean node(int x, int z) {
                                    return false; // this client has not seen the log
                                }
                            });
                    PlacementRules.Params params = new PlacementRules.Params(false, 0, 0, true); // its own centre aside
                    check(
                            PlacementRules.check(blind, b, Footprint.of(b, 18, 18, 0), 0, params)
                                    .ok(),
                            "the client refused it too");
                    rt.submit(
                            P0,
                            new PlaceBuilding(HOUSE, 18, 18, 0, UnitSet.of(), QueueMode.REPLACE),
                            result -> state.result = result);
                })
                .thenWaitUntil(() -> check(state.result != null, "no reply yet"))
                .thenExecute(() -> {
                    check(state.result.outcome() == CommandOutcome.REJECTED, "the server took it: " + state.result);
                    check(
                            state.result.firstReason() == RejectReason.BLOCKED,
                            "refused as " + state.result.firstReason());
                    check(houses(rt).isEmpty(), "a foundation was laid");
                })
                .thenSucceed();
    }

    /**
     * BLD-03.2 and .3: three workers raise a house; once its site is committed no unit stands on its columns, the two
     * walkers waiting there moved off it; finished, its blocks equal its blueprint's.
     */
    @StrategosTest(batch = "construction", timeoutTicks = 1_500)
    public static void aFinishedHouseEqualsItsBlueprint(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        town(rt, 3);
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(22.5, 22.5), 2, 1));
        var state = new Object() {
            boolean committed;
        };
        h.onEachTick(() -> {
            if (!state.committed) {
                return;
            }
            for (BuildingView house : houses(rt)) {
                Footprint at = house.footprint();
                for (int handle : handles(rt)) {
                    var u = unit(rt, handle);
                    int x = (int) Math.floor(u.x());
                    int z = (int) Math.floor(u.z());
                    if (x >= at.anchorX()
                            && x < at.anchorX() + at.spanX()
                            && z >= at.anchorZ()
                            && z < at.anchorZ() + at.spanZ()) {
                        h.fail("a unit stands in the committed house at " + u.x() + ", " + u.z());
                    }
                }
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 5, "not spawned yet"))
                .thenExecute(() -> {
                    int[] workers = java.util.Arrays.stream(handles(rt))
                            .filter(u -> unit(rt, u).faction().value() == 0)
                            .toArray();
                    rt.submit(P0, new PlaceBuilding(HOUSE, 20, 20, 0, UnitSet.of(workers), QueueMode.REPLACE));
                })
                .thenWaitUntil(() -> {
                    check(
                            !houses(rt).isEmpty() && houses(rt).get(0).state() != BuildingStore.FOUNDATION,
                            "not committed");
                    state.committed = true;
                })
                .thenWaitUntil(() -> check(houses(rt).get(0).state() == BuildingStore.COMPLETE, "not finished"))
                .thenExecuteAfter(20, () -> equalsBlueprint(h, rt, houses(rt).get(0)))
                .thenSucceed();
    }

    /** BLD-03.4: saved halfway, unloaded and restored, the site is at the same block and hash, and it finishes as drawn. */
    @StrategosTest(batch = "construction", timeoutTicks = 1_800)
    public static void saveMidBuildResumesAtTheSameBlock(GameTestHelper h) {
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        town(rt, 1);
        var state = new Object() {
            MatchRuntime back = rt;
            int progress;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 1, "no worker yet"))
                .thenExecute(() ->
                        rt.submit(P0, new PlaceBuilding(HOUSE, 20, 20, 0, UnitSet.of(handles(rt)), QueueMode.REPLACE)))
                .thenWaitUntil(
                        () -> check(!houses(rt).isEmpty() && houses(rt).get(0).progress() >= 400, "not halfway yet"))
                .thenExecute(() -> {
                    MatchCore live = core(rt);
                    long tick = live.queries().tick();
                    long hash = live.stateHash();
                    state.progress = (int) houses(rt).get(0).progress();
                    MatchData data = rt.data();
                    MatchRecord record = registry.record(rt.id());
                    check(data != null && record != null, "no blob or record");
                    CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
                    registry.unload(rt.id());
                    state.back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag));
                    check(core(state.back).queries().tick() == tick, "restored at another tick");
                    check(core(state.back).stateHash() == hash, "restored with another hash");
                    check(houses(state.back).get(0).progress() == state.progress, "restored at another stage");
                    registry.resume(state.back.id());
                })
                .thenWaitUntil(() -> check(houses(state.back).get(0).state() == BuildingStore.COMPLETE, "not finished"))
                .thenExecuteAfter(
                        20,
                        () -> equalsBlueprint(h, state.back, houses(state.back).get(0)))
                .thenSucceed();
    }

    /**
     * BLD-03.5: one seeded match raised once with the writer's budget at 64 and once at 8 has the same hash at the same
     * tick: the core never waits on the blocks.
     */
    @StrategosTest(batch = "construction_budget", timeoutTicks = 2_400, solo = true)
    public static void writerBudgetNeverChangesTheHash(GameTestHelper h) {
        BlockWriter writer = StrategosServer.get().writer();
        int before = writer.budget();
        long[] hashes = new long[2];
        BlockState floor = h.getBlockState(new BlockPos(0, 1, 0));
        var state = new Object() {
            MatchRuntime rt;
            int run;
        };
        Runnable start = () -> {
            writer.budget(state.run == 0 ? 64 : 8);
            state.rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
            town(state.rt, 2);
        };
        start.run();
        h.onEachTick(() -> {
            MatchRuntime rt = state.rt;
            if (rt == null || rt.core() == null) {
                return;
            }
            long tick = core(rt).queries().tick();
            if (tick == 5) {
                rt.submit(
                        P0,
                        new PlaceBuilding(HOUSE, 20, 20, 0, UnitSet.of(handles(rt)), QueueMode.REPLACE),
                        result ->
                                check(result.outcome() == CommandOutcome.ACCEPTED, "run " + state.run + ": " + result));
            }
            if (tick == 700) {
                hashes[state.run] = core(rt).stateHash();
                check(houses(rt).get(0).state() == BuildingStore.COMPLETE, "the house is not up by tick 700");
                StrategosServer.get().matches().dispose(rt.id());
                state.rt = null;
                if (state.run == 0) {
                    state.run = 1;
                    for (int x = 0; x < 32; x++) { // the first run's town goes: the second starts on the same ground
                        for (int z = 0; z < 32; z++) {
                            h.setBlock(new BlockPos(x, 1, z), floor);
                            for (int y = 2; y < 12; y++) {
                                h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                            }
                        }
                    }
                    start.run();
                } else {
                    writer.budget(before);
                    check(hashes[0] == hashes[1], "the hashes differ with budgets 64 and 8");
                    h.succeed();
                }
            }
        });
    }

    /**
     * BLD-03.6: three houses rising at once never take more than 64 placements in a tick between them, and each site's
     * writes come in bursts at most every 4 ticks.
     */
    @StrategosTest(batch = "construction", arena = "arena_flat_48", timeoutTicks = 1_500)
    public static void threeSitesWithinTheWriterBudget(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        town(rt, 9);
        BlockWriter writer = StrategosServer.get().writer();
        Map<Long, Integer> perTick = new HashMap<>();
        Map<Integer, List<Long>> bursts = new HashMap<>();
        writer.observe((level, tick, site, pos, s) -> {
            if (site.match() == rt.id().value()) {
                perTick.merge(tick, 1, Integer::sum);
                List<Long> ticks = bursts.computeIfAbsent(site.site(), k -> new ArrayList<>());
                if (ticks.isEmpty() || ticks.get(ticks.size() - 1) != tick) {
                    ticks.add(tick);
                }
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 9, "no workers yet"))
                .thenExecute(() -> {
                    int[] w = handles(rt);
                    for (int k = 0; k < 3; k++) {
                        rt.submit(
                                P0,
                                new PlaceBuilding(
                                        HOUSE,
                                        18 + 8 * k,
                                        20,
                                        0,
                                        UnitSet.of(w[3 * k], w[3 * k + 1], w[3 * k + 2]),
                                        QueueMode.REPLACE));
                    }
                })
                .thenWaitUntil(() -> check(
                        houses(rt).size() == 3
                                && houses(rt).stream().allMatch(b -> b.state() == BuildingStore.COMPLETE),
                        "not all up"))
                .thenExecuteAfter(20, () -> {
                    writer.observe(null);
                    int most = perTick.values().stream()
                            .mapToInt(Integer::intValue)
                            .max()
                            .orElse(0);
                    check(most <= writer.budget(), most + " placements in one tick");
                    for (BuildingView house : houses(rt)) {
                        List<Long> ticks = bursts.get(dev.strategos.core.shared.Handle.index(house.handle()));
                        check(ticks != null && ticks.size() > 3, "site " + house.handle() + " wrote in " + ticks);
                        for (int k = 1; k < ticks.size(); k++) {
                            check(
                                    ticks.get(k) - ticks.get(k - 1) >= 4,
                                    "site " + house.handle() + " burst at " + ticks);
                        }
                        equalsBlueprint(h, rt, house);
                    }
                })
                .thenSucceed();
    }
}
