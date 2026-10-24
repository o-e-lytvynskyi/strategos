package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.move;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRecord;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.persist.MatchData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jspecify.annotations.Nullable;

/** The economy in a real level (M3): resource nodes, gathering, fields. */
public final class EcoTests {
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    /** The level's random tick speed before the growth test raised it, put back after its batch; -1 untouched. */
    private static final AtomicInteger RANDOM_TICKS = new AtomicInteger(-1);

    private EcoTests() {}

    /** After the growth and felling batches: the random tick speed their test found. */
    public static void restoreRandomTicks(ServerLevel level) {
        int before = RANDOM_TICKS.getAndSet(-1);
        if (before >= 0) {
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(before, level.getServer());
        }
    }

    /** Faction 0's {@code count} workers round match-local (x, z), spawned at the next step. */
    static void workers(MatchRuntime rt, double x, double z, int count) {
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(x, z), count, 1.5));
    }

    /** The node whose blocks include match-local (x, z) at height y; null before discovery. */
    static @Nullable NodeView nodeAt(MatchRuntime rt, double anchorX, double anchorZ) {
        for (NodeView n : core(rt).queries().nodes()) {
            if (n.anchorX() == anchorX && n.anchorZ() == anchorZ) {
                return n;
            }
        }
        return null;
    }

    private static int bushAge(GameTestHelper h, BlockPos relative) {
        BlockState state = h.getBlockState(relative);
        check(state.is(Blocks.SWEET_BERRY_BUSH), "no bush at " + relative + ": " + state);
        return state.getValue(SweetBerryBushBlock.AGE);
    }

    /**
     * ECO-05.7: with random ticks fast, a bush in the match changes only through the core: shown full once found, then
     * picked to half, it shows age 2 and keeps it while a control bush just outside the match grows from 0.
     */
    @StrategosTest(batch = "eco_growth", timeoutTicks = 2_400, solo = true)
    public static void bushesChangeOnlyThroughTheCore(GameTestHelper h) {
        BlockPos bush = new BlockPos(8, 2, 8);
        BlockPos control = new BlockPos(28, 2, 8); // the match covers x 0..23
        h.setBlock(bush.below(), Blocks.DIRT);
        h.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 0));
        h.setBlock(control.below(), Blocks.DIRT);
        h.setBlock(control, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 0));
        MatchRuntime rt = MatchFixture.create(h, 0, 0, 24, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 12, 16, 0));
        workers(rt, 10, 12, 3);
        int y = h.absolutePos(bush).getY();
        h.startSequence()
                .thenWaitUntil(() -> check(bushAge(h, bush) == 3, "the core has not shown the bush full yet"))
                .thenWaitUntil(() -> check(handles(rt).length == 3, "no workers yet"))
                .thenExecute(() -> rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(handles(rt)), 8, y, 8)))
                .thenWaitUntil(() -> {
                    NodeView n = nodeAt(rt, 8.5, 8.5);
                    check(n != null && n.amount() <= 30_000, "not picked to half yet: " + n);
                })
                .thenExecute(() -> {
                    rt.submit(PlayerSlot.ADMIN, new Stop(UnitSet.of(handles(rt))));
                    GameRules.IntegerValue rule = h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
                    RANDOM_TICKS.compareAndSet(-1, rule.get());
                    rule.set(1_000, h.getLevel().getServer());
                })
                .thenWaitUntil(() -> {
                    check(bushAge(h, bush) == 2, "the match bush shows " + bushAge(h, bush) + ", not 2");
                    check(bushAge(h, control) >= 2, "the control bush has not grown yet");
                })
                .thenExecuteAfter(100, () -> {
                    NodeView n = nodeAt(rt, 8.5, 8.5);
                    check(n != null && n.amount() <= 40_000, "it regrew past two thirds: " + n);
                    check(bushAge(h, bush) == 2, "a random tick grew the match bush to " + bushAge(h, bush));
                })
                .thenSucceed();
    }

    /**
     * ECO-05.8: a worker gathers wood and another berries, the first with an axe in hand, each showing its load in the
     * off hand, and each load reaches the treasury; saved, unloaded and restored, the match has the same hash, the bush
     * its amount and its block's age, and the work goes on.
     */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 2_400)
    public static void gatherDeliverAndSave(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(6, y, 6), Blocks.OAK_LOG);
        }
        BlockPos bush = new BlockPos(6, 2, 26);
        h.setBlock(bush.below(), Blocks.DIRT);
        h.setBlock(bush, Blocks.SWEET_BERRY_BUSH);
        MatchRegistry registry = StrategosServer.get().matches();
        MatchRuntime rt = MatchFixture.create(
                h, MatchFixture.options(SimHarness.DEFAULT_SEED, h).withPersistent(true));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 14, 10, 0));
        workers(rt, 10, 16, 2);
        int y = h.absolutePos(bush).getY();
        var state = new Object() {
            MatchRuntime back = rt;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 2 && nodeAt(rt, 6.5, 6.5) != null, "not ready yet"))
                .thenExecute(() -> {
                    int[] units = handles(rt);
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(units[0]), 6, y + 1, 6));
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(units[1]), 6, y, 26));
                })
                .thenWaitUntil(() -> {
                    UnitEntity woodcutter = body(rt, handles(rt)[0]);
                    check(woodcutter.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_AXE), "no axe in hand yet");
                    check(woodcutter.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.OAK_LOG), "no log carried yet");
                    UnitEntity picker = body(rt, handles(rt)[1]);
                    check(picker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "a tool for berries");
                    check(
                            picker.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SWEET_BERRIES),
                            "no berries carried yet");
                })
                .thenWaitUntil(() -> {
                    FactionTreasury t = core(rt).queries().treasury(new FactionSlot(0));
                    check(t.gathered().get(1) >= 10_000 && t.gathered().get(0) >= 10_000, "loads not home yet: " + t);
                })
                .thenExecute(() -> {
                    MatchCore live = core(rt);
                    long tick = live.queries().tick();
                    long hash = live.stateHash();
                    NodeView before = nodeAt(rt, 6.5, 26.5);
                    int age = bushAge(h, bush);
                    MatchData data = rt.data();
                    MatchRecord record = registry.record(rt.id());
                    check(data != null && record != null && before != null, "no blob, record or bush");
                    CompoundTag tag = data.save(new CompoundTag(), h.getLevel().registryAccess());
                    registry.unload(rt.id());
                    state.back = registry.restore(h.getLevel(), record, MatchData.load(rt.id(), tag));
                    check(core(state.back).queries().tick() == tick, "restored at another tick");
                    check(core(state.back).stateHash() == hash, "restored with another hash");
                    NodeView after = nodeAt(state.back, 6.5, 26.5);
                    check(
                            after != null && after.amount() == before.amount(),
                            "the bush " + before + " came back " + after);
                    check(bushAge(h, bush) == age, "the bush's age changed over the save");
                    registry.resume(state.back.id());
                })
                .thenWaitUntil(() -> {
                    FactionTreasury t = core(state.back).queries().treasury(new FactionSlot(0));
                    check(t.gathered().get(0) >= 20_000, "no load home after the restore: " + t);
                })
                .thenSucceed();
    }

    /**
     * ECO-02.4: blocks the {@code strategos:resource_yield} data map names become nodes when the match prepares: a
     * three-log oak a tree of 60 wood, a berry bush a forage node of 60 food; a melon, which the map does not name,
     * none. The map is the only source: the material table holds the log's entry and nothing for the melon.
     */
    @StrategosTest(batch = "eco_nodes", timeoutTicks = 100)
    public static void aResourceYieldEntryMakesANode(GameTestHelper h) {
        for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(8, y, 8), Blocks.OAK_LOG);
        }
        h.setBlock(new BlockPos(20, 1, 20), Blocks.DIRT); // relative y 1 is the floor layer
        h.setBlock(new BlockPos(20, 2, 20), Blocks.SWEET_BERRY_BUSH);
        h.setBlock(new BlockPos(26, 2, 8), Blocks.MELON);
        var table = StrategosServer.get().materials();
        check(table.of(Blocks.OAK_LOG.defaultBlockState()) != null, "the oak log has no material");
        check(table.of(Blocks.MELON.defaultBlockState()) == null, "the melon has a material");
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        h.startSequence()
                .thenWaitUntil(() -> check(!core(rt).queries().nodes().isEmpty(), "no node yet"))
                .thenExecute(() -> {
                    List<NodeView> nodes = core(rt).queries().nodes();
                    check(nodes.size() == 2, "nodes: " + nodes);
                    NodeView tree = nodes.stream()
                            .filter(n -> n.kind() == MaterialKind.LOG)
                            .findFirst()
                            .orElseThrow();
                    check(
                            tree.blocks() == 3
                                    && tree.amount() == 60_000
                                    && tree.resource().equals("wood"),
                            "the tree " + tree);
                    check(
                            tree.anchorX() == 8.5 && tree.anchorZ() == 8.5 && tree.slots() == 8,
                            "the tree's base " + tree);
                    NodeView bush = nodes.stream()
                            .filter(n -> n.kind() == MaterialKind.FORAGE)
                            .findFirst()
                            .orElseThrow();
                    check(
                            bush.amount() == 60_000
                                    && bush.resource().equals("food")
                                    && bush.subtype().equals("fruit"),
                            "the bush " + bush);
                })
                .thenSucceed();
    }

    /**
     * The leaves vanilla would decay in the box: non-persistent leaves farther than six leaf steps (the six faces) from
     * every log, vanilla's own rule (LeavesBlock.java:29), searched from the logs outward.
     */
    private static List<BlockPos> decayable(ServerLevel level, BlockPos min, BlockPos max) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> leaves = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(p);
            if (state.is(BlockTags.LEAVES) && !state.getValue(LeavesBlock.PERSISTENT)) {
                leaves.add(p.immutable());
            } else if (state.is(BlockTags.LOGS)) {
                distance.put(p.immutable(), 0);
                queue.add(p.immutable());
            }
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int d = distance.get(p);
            if (d >= LeavesBlock.DECAY_DISTANCE - 1) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                BlockState state = level.getBlockState(n);
                if (state.is(BlockTags.LEAVES) && !distance.containsKey(n)) {
                    distance.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        return leaves.stream().filter(leaf -> !distance.containsKey(leaf)).toList();
    }

    /**
     * ECO-03.5: four vanilla-grown trees, their leaves not persistent, felled by workers log by log with random ticks
     * off, so only the core takes leaves: no leaf vanilla would decay is seen twice in a row, a second apart (the card
     * allows 5 s; the writer's budget may hold one for a tick or two), and at the end no leaf is left. The match reaches
     * 24 blocks up, past the arena, so the test runs alone.
     */
    @StrategosTest(batch = "eco_felling", arena = "arena_flat_48", timeoutTicks = 6_000, solo = true, skyAccess = true)
    public static void noDecayableLeafLingers(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        GameRules.IntegerValue ticks = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
        RANDOM_TICKS.compareAndSet(-1, ticks.get());
        ticks.set(0, level.getServer());
        var features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        RandomSource random = RandomSource.create(SimHarness.DEFAULT_SEED);
        List<BlockPos> bases =
                List.of(new BlockPos(6, 2, 6), new BlockPos(41, 2, 6), new BlockPos(6, 2, 41), new BlockPos(41, 2, 41));
        List<ResourceKey<ConfiguredFeature<?, ?>>> kinds =
                List.of(TreeFeatures.OAK, TreeFeatures.BIRCH, TreeFeatures.SPRUCE, TreeFeatures.ACACIA);
        for (int k = 0; k < bases.size(); k++) {
            BlockPos at = h.absolutePos(bases.get(k));
            level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 3);
            check(
                    features.getHolderOrThrow(kinds.get(k))
                            .value()
                            .place(level, level.getChunkSource().getGenerator(), random, at),
                    "no " + kinds.get(k).location() + " grew");
        }
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        MatchSpace space = new MatchSpace(level.dimension(), origin, 48, 48, origin.getY(), origin.getY() + 24);
        MatchRuntime rt = MatchFixture.create(h, space, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 18, 18, 0));
        workers(rt, 24, 15, 16);
        BlockPos min = origin;
        BlockPos max = origin.offset(47, 24, 47);
        var state = new Object() {
            List<BlockPos> lastSeen = List.of();
            int checks;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        handles(rt).length == 16 && core(rt).queries().nodes().size() >= 4, "not ready"))
                .thenExecute(() -> {
                    check(decayable(level, min, max).isEmpty(), "the grown trees have decayable leaves already");
                    int[] units = handles(rt);
                    for (int k = 0; k < bases.size(); k++) {
                        BlockPos b = bases.get(k);
                        int[] four = Arrays.copyOfRange(units, 4 * k, 4 * k + 4);
                        rt.submit(
                                PlayerSlot.ADMIN,
                                Gather.replace(
                                        UnitSet.of(four),
                                        b.getX(),
                                        h.absolutePos(b).getY(),
                                        b.getZ()));
                    }
                })
                .thenWaitUntil(() -> {
                    if (h.getTick() % 20 == 0) {
                        List<BlockPos> now = decayable(level, min, max);
                        List<BlockPos> lingering =
                                now.stream().filter(state.lastSeen::contains).toList();
                        if (!lingering.isEmpty()) {
                            h.fail("leaves vanilla would decay lingered a second: " + lingering);
                        }
                        state.lastSeen = now;
                        state.checks++;
                    }
                    boolean felled = core(rt).queries().nodes().stream()
                            .filter(n -> n.kind() == MaterialKind.LOG)
                            .allMatch(n -> n.amount() == 0);
                    check(felled, "the trees still stand");
                })
                .thenExecuteAfter(40, () -> {
                    int left = 0;
                    for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                        BlockState b = level.getBlockState(p);
                        if (b.is(BlockTags.LEAVES) || b.is(BlockTags.LOGS)) {
                            left++;
                        }
                    }
                    check(left == 0, left + " leaves or logs left of the felled trees");
                    check(state.checks > 10, "only " + state.checks + " checks while felling");
                })
                .thenSucceed();
    }

    /**
     * ECO-03.6: a corridor stopped by a three-log tree; once it is felled a unit walks through where the trunk stood,
     * and no page job is captured after the felling started: the core made the cells passable itself.
     */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 3_000)
    public static void fellThenWalkTheClearingWithNoPageJob(GameTestHelper h) {
        for (int x = 2; x <= 29; x++) {
            for (int y = 2; y <= 4; y++) {
                h.setBlock(new BlockPos(x, y, 13), Blocks.STONE);
                h.setBlock(new BlockPos(x, y, 15), Blocks.STONE);
            }
        }
        for (int y = 2; y <= 4; y++) {
            h.setBlock(new BlockPos(16, y, 14), Blocks.OAK_LOG);
        }
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 18, 18, 0));
        workers(rt, 10, 20, 2);
        var state = new Object() {
            long captured;
            int walker;
        };
        int y = h.absolutePos(new BlockPos(16, 2, 14)).getY();
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 2 && nodeAt(rt, 16.5, 14.5) != null, "not ready yet"))
                .thenExecute(() -> {
                    state.captured = rt.pages().captured();
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(handles(rt)), 16, y, 14));
                })
                .thenWaitUntil(() -> {
                    NodeView tree = nodeAt(rt, 16.5, 14.5);
                    check(tree != null && tree.amount() == 0, "the tree still stands: " + tree);
                })
                .thenExecuteAfter(10, () -> {
                    rt.submit(PlayerSlot.ADMIN, new Stop(UnitSet.of(handles(rt))));
                    state.walker = handles(rt)[0];
                    move(rt, state.walker, 4.5, 14.5); // the corridor's west end, from its east side
                })
                .thenWaitUntil(() -> {
                    var u = unit(rt, state.walker);
                    check(Math.hypot(u.x() - 4.5, u.z() - 14.5) < 1.0, "not through yet: " + u.x() + ", " + u.z());
                })
                .thenExecute(() -> check(
                        rt.pages().captured() == state.captured,
                        (rt.pages().captured() - state.captured) + " pages captured after the felling started"))
                .thenSucceed();
    }

    /**
     * ECO-07.4: three workers mine a 3 × 3 rock hill two blocks high to nothing; every block of it is air in the
     * world, the arena's floor under it stays, and no page job is captured after the mining started: the core made
     * the cells passable itself.
     */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 6_000)
    public static void aWorkerMinesAnOutcropWithNoPageJob(GameTestHelper h) {
        for (int x = 10; x <= 12; x++) {
            for (int z = 10; z <= 12; z++) {
                for (int y = 2; y <= 3; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 18, 18, 0));
        workers(rt, 6, 11, 3);
        var state = new Object() {
            long captured;
        };
        int top = h.absolutePos(new BlockPos(11, 3, 11)).getY();
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 3 && nodeAt(rt, 11.5, 11.5) != null, "not ready yet"))
                .thenExecute(() -> {
                    state.captured = rt.pages().captured();
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(handles(rt)), 11, top, 11));
                })
                .thenWaitUntil(() -> {
                    NodeView rock = nodeAt(rt, 11.5, 11.5);
                    check(rock != null && rock.amount() == 0, "the hill still stands: " + rock);
                })
                .thenExecuteAfter(20, () -> {
                    for (int x = 10; x <= 12; x++) {
                        for (int z = 10; z <= 12; z++) {
                            for (int y = 2; y <= 3; y++) {
                                check(
                                        h.getBlockState(new BlockPos(x, y, z)).isAir(),
                                        "rock left at " + x + ", " + y + ", " + z);
                            }
                            check(!h.getBlockState(new BlockPos(x, 1, z)).isAir(), "the floor under it went");
                        }
                    }
                    check(
                            rt.pages().captured() == state.captured,
                            (rt.pages().captured() - state.captured) + " pages captured after the mining started");
                })
                .thenSucceed();
    }

    /**
     * ECO-08.3: an iron mine spawned in the arena, its 18 blocks iron ore, worked by three workers: in the world, a
     * quarter of its blocks has turned to stone once 75 % is left, half at 50 %, three quarters at 25 % and all of
     * them when it is empty.
     */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 24_000)
    public static void aDepositChangesLookAtItsStages(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 18, 18, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new dev.strategos.core.protocol.SpawnDeposit(new TemplateId("strategos:building/iron_mine"), 10, 10));
        workers(rt, 6, 11, 3);
        var state = new Object() {
            int stage;
        };
        int top = h.absolutePos(new BlockPos(11, 2, 11)).getY();
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 3 && nodeAt(rt, 11.5, 11.5) != null, "not ready yet"))
                .thenWaitUntil(() -> check(stone(h) == 0 && ore(h) == 18, "the mine's blocks: " + ore(h) + " ore"))
                .thenExecute(() -> rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(handles(rt)), 11, top, 11)))
                .thenWaitUntil(() -> {
                    NodeView mine = nodeAt(rt, 11.5, 11.5);
                    check(mine != null, "the mine is gone");
                    int shown = 4 - (int) ((mine.amount() * 4 + mine.most() - 1) / mine.most());
                    if (shown > state.stage) {
                        state.stage = shown; // the writer turns them by the next tick
                        return;
                    }
                    check(stone(h) == state.stage * 18 / 4, "stage " + state.stage + " shows " + stone(h) + " stone");
                    check(state.stage == 4, "at stage " + state.stage);
                })
                .thenSucceed();
    }

    /**
     * ECO-16.3: two workers sent to a sheep slaughter it; its body stays in the world, lying, while its carcass is
     * gathered, and goes when the carcass is empty, with the template's 100 food at home or carried.
     */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 4_000)
    public static void aCarcassDepletesAndItsBodyGoes(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 18, 18, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(new TemplateId("strategos:unit/sheep"), new FactionSlot(2), new Vec2d(8.5, 8.5), 1, 0));
        workers(rt, 12, 12, 2);
        var state = new Object() {
            int sheep;

            @Nullable UnitEntity body;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(handles(rt).length == 3, "not spawned yet");
                    for (int handle : handles(rt)) {
                        check(rt.bodies().get(handle) != null, "no bodies yet");
                    }
                })
                .thenExecute(() -> {
                    List<Integer> workers = new ArrayList<>();
                    for (int handle : handles(rt)) {
                        if (unit(rt, handle).faction().value() == 2) {
                            state.sheep = handle;
                        } else {
                            workers.add(handle);
                        }
                    }
                    state.body = body(rt, state.sheep);
                    rt.submit(
                            PlayerSlot.ADMIN,
                            dev.strategos.core.protocol.Attack.replace(
                                    UnitSet.of(workers.stream()
                                            .mapToInt(Integer::intValue)
                                            .toArray()),
                                    state.sheep));
                })
                .thenWaitUntil(() -> check(core(rt).queries().unit(state.sheep) == null, "the sheep lives"))
                .thenExecute(() -> {
                    UnitEntity body = state.body;
                    check(body != null && !body.isRemoved(), "the carcass's body went at the death");
                    check(body.hasPose(net.minecraft.world.entity.Pose.DYING), "the carcass does not lie");
                    check(
                            rt.bodies().carcassCount() == 1,
                            "carcasses kept: " + rt.bodies().carcassCount());
                })
                .thenWaitUntil(() -> check(rt.bodies().carcassCount() == 0, "the carcass is still gathered"))
                .thenExecute(() -> {
                    UnitEntity body = state.body;
                    check(body != null && body.isRemoved(), "the empty carcass's body stays");
                })
                .thenWaitUntil(() -> {
                    FactionTreasury t = core(rt).queries().treasury(new FactionSlot(0));
                    long carried = 0;
                    for (int handle : handles(rt)) {
                        carried += core(rt).queries().carriedResource(handle) == 0
                                ? core(rt).queries().carriedAmount(handle)
                                : 0;
                    }
                    check(
                            t.gathered().get(0) + carried == 100_000,
                            "food: " + t.gathered().get(0) + " + " + carried);
                })
                .thenSucceed();
    }

    private static int stone(GameTestHelper h) {
        return count(h, Blocks.STONE);
    }

    private static int ore(GameTestHelper h) {
        return count(h, Blocks.IRON_ORE) + count(h, Blocks.DEEPSLATE_IRON_ORE);
    }

    private static int count(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        int n = 0;
        for (int x = 10; x <= 12; x++) {
            for (int z = 10; z <= 12; z++) {
                for (int y = 1; y <= 2; y++) { // its lowest layer took the arena floor's place
                    n += h.getBlockState(new BlockPos(x, y, z)).is(block) ? 1 : 0;
                }
            }
        }
        return n;
    }

    static final TemplateId FIELD = new TemplateId("strategos:building/field");

    private static int cropAge(GameTestHelper h, BlockPos relative) {
        BlockState state = h.getBlockState(relative);
        check(state.is(Blocks.WHEAT), "no wheat at " + relative + ": " + state);
        return state.getValue(net.minecraft.world.level.block.CropBlock.AGE);
    }

    /** The age the core shows for field node {@code n}, as {@code Fields#age} computes it, ripening over 600 ticks. */
    private static int expectedAge(NodeView n) {
        if (n.ripening() > 0) {
            return (int) Math.min(6, (600 - n.ripening()) * 7 / 600);
        }
        return n.amount() <= 0 ? 0 : (int) ((7 * n.amount() + n.most() - 1) / n.most());
    }

    /**
     * ECO-09.3: with random ticks running and a first-person player beside the field, its crop's age changes only as the
     * core says, a tick behind at most, while a control wheat outside the match grows by itself.
     */
    @StrategosTest(batch = "eco_crops", timeoutTicks = 1_600, solo = true)
    public static void cropsChangeOnlyThroughTheCore(GameTestHelper h) {
        BlockPos control = new BlockPos(28, 2, 8);
        h.setBlock(
                control.below(),
                Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
        h.setBlock(control, Blocks.WHEAT);
        MatchRuntime rt = MatchFixture.create(h, 0, 0, 24, 32, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 2, 16, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(FIELD, new FactionSlot(0), 8, 4, 0));
        net.minecraft.world.phys.Vec3 at = h.absoluteVec(new net.minecraft.world.phys.Vec3(14.5, 2, 6.5));
        dev.strategos.gametest.net.MockViewer player =
                dev.strategos.gametest.net.MockViewer.join(h.getLevel(), "strategos-crops", at.x, at.y, at.z, 4);
        GameRules.IntegerValue rule = h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
        RANDOM_TICKS.compareAndSet(-1, rule.get());
        rule.set(1_000, h.getLevel().getServer()); // the card's 3 would take minutes to grow the control once
        var state = new Object() {
            int lagging;
            int changes;
            int last = -1;
        };
        h.onEachTick(() -> {
            NodeView field = core(rt).queries().nodes().stream()
                    .filter(n -> n.kind() == MaterialKind.CROP)
                    .findFirst()
                    .orElse(null);
            if (field == null || !h.getBlockState(new BlockPos(10, 2, 6)).is(Blocks.WHEAT)) {
                return; // the writer has not raised the field yet
            }
            int shown = cropAge(h, new BlockPos(10, 2, 6));
            int want = expectedAge(field);
            if (shown != want) {
                state.lagging++; // the writer's flush may come a tick after the core's word
                if (state.lagging > 2) {
                    h.fail("the crop shows " + shown + " where the core says " + want);
                }
            } else {
                state.lagging = 0;
            }
            if (shown != state.last) {
                state.changes++;
                state.last = shown;
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(cropAge(h, control) >= 3, "the control has not grown yet");
                    check(state.changes >= 4, "the field's crop rose only " + state.changes + " times");
                })
                .thenExecute(player::close)
                .thenSucceed();
    }

    /** ECO-09.4: two farmers reap a field empty, its food comes home, and paid for, it is sown again and ripens. */
    @StrategosTest(batch = "eco_gather", timeoutTicks = 12_000)
    public static void aFieldYieldsAndIsReseeded(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 2, 16, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(FIELD, new FactionSlot(0), 8, 4, 0));
        rt.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Grant(new FactionSlot(0), "wood", 100));
        workers(rt, 12, 8, 2);
        int y = h.absolutePos(new BlockPos(10, 2, 6)).getY();
        var state = new Object() {
            int field;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        handles(rt).length == 2
                                && core(rt).queries().buildings().size() == 2,
                        "not ready"))
                .thenExecute(() -> {
                    state.field = core(rt).queries().buildings().stream()
                            .filter(b -> b.template().equals(FIELD))
                            .findFirst()
                            .orElseThrow()
                            .handle();
                    rt.submit(PlayerSlot.ADMIN, Gather.replace(UnitSet.of(handles(rt)), 10, y, 6));
                })
                .thenWaitUntil(() -> {
                    NodeView n = core(rt).queries().nodes().stream()
                            .filter(v -> v.kind() == MaterialKind.CROP)
                            .findFirst()
                            .orElseThrow();
                    check(n.amount() == 0 && n.ripening() == 0, "not reaped yet: " + n);
                })
                .thenWaitUntil(() -> {
                    FactionTreasury t = core(rt).queries().treasury(new FactionSlot(0));
                    check(t.gathered().get(0) >= 300_000, "not all home yet: " + t.gathered());
                    check(cropAge(h, new BlockPos(10, 2, 6)) == 0, "an empty field's crop is not at age 0");
                })
                .thenExecute(() -> rt.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.Reseed(state.field)))
                .thenWaitUntil(() -> {
                    NodeView n = core(rt).queries().nodes().stream()
                            .filter(v -> v.kind() == MaterialKind.CROP)
                            .findFirst()
                            .orElseThrow();
                    check(n.amount() == 300_000, "not ripe again: " + n);
                    check(
                            core(rt).queries()
                                            .treasury(new FactionSlot(0))
                                            .stock()
                                            .get(1)
                                    == 60_000,
                            "the reseed did not cost forty wood");
                    check(cropAge(h, new BlockPos(10, 2, 6)) == 7, "the ripe crop is not at age 7");
                })
                .thenSucceed();
    }
}
