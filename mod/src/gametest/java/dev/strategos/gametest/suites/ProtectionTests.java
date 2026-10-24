package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.world.ProtectedBlockIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Building protection (BLD-07, 04 §11.9): for each engine source a GameTest shows it working on a control case, a
 * block of no building or a place with no match yet, and then failing on a building's block; a direct chunk write is
 * undone by the end of the tick; and a house raised by workers has every block of it in the index.
 */
public final class ProtectionTests {
    private ProtectionTests() {}

    /** A match with a whole house of faction 0 at (10, 10); the house is protected once the writer has placed it. */
    private static MatchRuntime town(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(ConstructionTests.HOUSE, new FactionSlot(0), 10, 10, 0));
        return rt;
    }

    private static ProtectedBlockIndex index(GameTestHelper h) {
        return StrategosServer.get().writer().protectedBlocks(h.getLevel());
    }

    private static BuildingView house(MatchRuntime rt) {
        List<BuildingView> houses = ConstructionTests.houses(rt);
        check(!houses.isEmpty(), "no house yet");
        return houses.get(0);
    }

    /** The house's blocks above its floor, absolute, in build order. */
    private static List<BlockPos> walls(MatchRuntime rt) {
        BuildingView b = house(rt);
        Blueprint bp = Objects.requireNonNull(core(rt).queries()
                .templates()
                .blueprint(Objects.requireNonNull(core(rt).queries().templates().byId(b.template()))
                        .netIndex()));
        Footprint at = b.footprint();
        BlockPos origin = rt.space().origin();
        List<BlockPos> out = new ArrayList<>();
        for (int k = 0; k < bp.count(); k++) {
            if (bp.y(k) >= 1) {
                out.add(new BlockPos(
                        origin.getX() + at.worldX(bp.x(k), bp.z(k)),
                        b.baseY() + bp.y(k),
                        origin.getZ() + at.worldZ(bp.x(k), bp.z(k))));
            }
        }
        return out;
    }

    /** One of the house's wall blocks, once the writer has placed and protected all of them. */
    private static BlockPos wall(GameTestHelper h, MatchRuntime rt) {
        for (BlockPos pos : walls(rt)) {
            check(index(h).owner(pos) == house(rt).handle(), pos + " not protected yet");
        }
        return walls(rt).get(0);
    }

    /** A cell inside the house's box with no block of it: air above its floor, away from the walls. */
    private static BlockPos inside(MatchRuntime rt) {
        BuildingView b = house(rt);
        Footprint at = b.footprint();
        BlockPos origin = rt.space().origin();
        return new BlockPos(
                origin.getX() + at.anchorX() + at.spanX() / 2,
                b.baseY() + 1,
                origin.getZ() + at.anchorZ() + at.spanZ() / 2);
    }

    /** BLD-07.1, a player's break: a block of no building breaks; the house's wall does not. */
    @StrategosTest(batch = "protection", timeoutTicks = 400)
    public static void aPlayerCannotBreakABuilding(GameTestHelper h) {
        MatchRuntime rt = town(h);
        FakePlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        h.startSequence()
                .thenWaitUntil(() -> wall(h, rt))
                .thenExecute(() -> {
                    BlockPos floor = h.absolutePos(new BlockPos(3, 1, 3));
                    check(player.gameMode.destroyBlock(floor), "the control block did not break");
                    check(h.getLevel().getBlockState(floor).isAir(), "the control block stands");
                    BlockPos wall = wall(h, rt);
                    BlockState before = h.getLevel().getBlockState(wall);
                    check(!player.gameMode.destroyBlock(wall), "the wall broke");
                    check(h.getLevel().getBlockState(wall).equals(before), "the wall changed");
                })
                .thenSucceed();
    }

    /** BLD-07.1 and .3, an entity's place: a block placed outside the footprint stands; inside its box, refused. */
    @StrategosTest(batch = "protection", timeoutTicks = 400)
    public static void nothingIsPlacedInAFootprint(GameTestHelper h) {
        MatchRuntime rt = town(h);
        FakePlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        h.startSequence()
                .thenWaitUntil(() -> wall(h, rt))
                .thenExecute(() -> {
                    BlockPos out = h.absolutePos(new BlockPos(3, 2, 3));
                    check(
                            !EventHooks.onBlockPlace(
                                    player,
                                    BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), out),
                                    Direction.UP),
                            "a place outside every footprint was refused");
                    BlockPos in = inside(rt);
                    check(h.getLevel().getBlockState(in).isAir(), in + " is no air inside the house");
                    check(
                            EventHooks.onBlockPlace(
                                    player,
                                    BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), in),
                                    Direction.UP),
                            "a place inside the house's box was let through");
                })
                .thenSucceed();
    }

    /**
     * BLD-07.1, an explosion: TNT's blast beside the house takes the arena's floor round it but none of the house's
     * blocks, and crushes the house instead.
     */
    @StrategosTest(batch = "protection", timeoutTicks = 400)
    public static void anExplosionSparesABuildingAndHurtsIt(GameTestHelper h) {
        MatchRuntime rt = town(h);
        var state = new Object() {
            int standing;
        };
        h.startSequence()
                .thenWaitUntil(() -> wall(h, rt))
                .thenExecute(() -> {
                    BuildingView b = house(rt);
                    BlockPos origin = rt.space().origin();
                    BlockPos blast = new BlockPos(
                            origin.getX() + b.footprint().anchorX() - 2,
                            b.baseY() + 1,
                            origin.getZ() + b.footprint().anchorZ() + 2);
                    for (BlockPos pos : walls(rt)) {
                        state.standing += h.getLevel().getBlockState(pos).isAir() ? 0 : 1;
                    }
                    h.getLevel()
                            .explode(
                                    null,
                                    blast.getX() + 0.5,
                                    blast.getY(),
                                    blast.getZ() + 0.5,
                                    3f,
                                    Level.ExplosionInteraction.TNT);
                    check(h.getLevel().getBlockState(blast.below()).isAir(), "the blast took no floor: no control");
                    int now = 0;
                    for (BlockPos pos : walls(rt)) {
                        now += h.getLevel().getBlockState(pos).isAir() ? 0 : 1;
                    }
                    check(now == state.standing, (state.standing - now) + " of the house's blocks went");
                })
                .thenWaitUntil(() -> check(house(rt).hitPoints() < house(rt).maxHitPoints(), "the house took no crush"))
                .thenSucceed();
    }

    /** BLD-07.1, pistons: one pushes before the match exists; in the match one never moves. */
    @StrategosTest(batch = "protection", timeoutTicks = 400)
    public static void pistonsDoNothingInAMatch(GameTestHelper h) {
        var state = new Object() {
            MatchRuntime rt;
        };
        BlockPos control = new BlockPos(4, 2, 4);
        h.setBlock(control, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
        h.setBlock(control.above(), Blocks.DIRT);
        h.setBlock(control.east(), Blocks.REDSTONE_BLOCK);
        BlockPos guarded = new BlockPos(8, 2, 4);
        h.startSequence()
                .thenWaitUntil(() ->
                        check(h.getBlockState(control.above(2)).is(Blocks.DIRT), "the control piston pushed nothing"))
                .thenExecute(() -> {
                    state.rt = Units.match(h);
                    h.setBlock(
                            guarded, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
                    h.setBlock(guarded.above(), Blocks.DIRT);
                    h.setBlock(guarded.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenExecuteAfter(20, () -> {
                    check(!h.getBlockState(guarded).getValue(PistonBaseBlock.EXTENDED), "the match's piston extended");
                    check(h.getBlockState(guarded.above()).is(Blocks.DIRT), "the match's piston moved its block");
                })
                .thenSucceed();
    }

    /** BLD-07.1, mobs: before the match a sheep may grief and a zombie break a block; in it neither may. */
    @StrategosTest(batch = "protection", timeoutTicks = 200)
    public static void mobsGriefNothingInAMatch(GameTestHelper h) {
        var sheep = h.spawn(EntityType.SHEEP, new BlockPos(5, 2, 5));
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 5));
        BlockPos dirt = h.absolutePos(new BlockPos(7, 1, 6));
        boolean griefBefore = EventHooks.canEntityGrief(h.getLevel(), sheep);
        boolean destroyBefore =
                EventHooks.onEntityDestroyBlock(zombie, dirt, h.getLevel().getBlockState(dirt));
        Units.match(h);
        check(griefBefore && destroyBefore, "no control: " + griefBefore + ", " + destroyBefore);
        check(!EventHooks.canEntityGrief(h.getLevel(), sheep), "a sheep may grief in the match");
        check(
                !EventHooks.onEntityDestroyBlock(zombie, dirt, h.getLevel().getBlockState(dirt)),
                "a zombie may break a block in the match");
        sheep.discard();
        zombie.discard();
        h.succeed();
    }

    /**
     * BLD-07.1, natural spawns (M3's): a zombie's natural spawn check is left to vanilla before the match, and fails in
     * it.
     */
    @StrategosTest(batch = "protection", timeoutTicks = 200)
    public static void noMonsterSpawnsInAMatch(GameTestHelper h) {
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
        var before = NeoForge.EVENT_BUS
                .post(new MobSpawnEvent.PositionCheck(zombie, h.getLevel(), MobSpawnType.NATURAL, null))
                .getResult();
        Units.match(h);
        var during = NeoForge.EVENT_BUS
                .post(new MobSpawnEvent.PositionCheck(zombie, h.getLevel(), MobSpawnType.NATURAL, null))
                .getResult();
        zombie.discard();
        check(before == MobSpawnEvent.PositionCheck.Result.DEFAULT, "no control: " + before);
        check(during == MobSpawnEvent.PositionCheck.Result.FAIL, "a zombie may spawn in the match: " + during);
        h.succeed();
    }

    /** BLD-07.1, fluids: lava meeting water turns a block of no building, never the house's. */
    @StrategosTest(batch = "protection", timeoutTicks = 400)
    public static void fluidsTurnNoBuildingBlock(GameTestHelper h) {
        MatchRuntime rt = town(h);
        h.startSequence()
                .thenWaitUntil(() -> wall(h, rt))
                .thenExecute(() -> {
                    BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
                    BlockPos free = h.absolutePos(new BlockPos(3, 2, 3));
                    check(
                            EventHooks.fireFluidPlaceBlockEvent(h.getLevel(), free, free.above(), stone)
                                    .equals(stone),
                            "the control was refused");
                    BlockPos wall = wall(h, rt);
                    BlockState before = h.getLevel().getBlockState(wall);
                    check(
                            EventHooks.fireFluidPlaceBlockEvent(h.getLevel(), wall, wall.above(), stone)
                                    .equals(before),
                            "a fluid turned the house's wall");
                })
                .thenSucceed();
    }

    /** BLD-07.2: a direct chunk write takes a wall block away; by the end of the tick it is back, and a free block not. */
    @StrategosTest(batch = "protection", timeoutTicks = 400)
    public static void aDirectChunkWriteIsUndone(GameTestHelper h) {
        MatchRuntime rt = town(h);
        var state = new Object() {
            BlockPos wall;
            BlockState was;
            long restored;
        };
        BlockPos free = h.absolutePos(new BlockPos(3, 1, 3));
        h.startSequence()
                .thenWaitUntil(() -> wall(h, rt))
                .thenExecute(() -> {
                    state.wall = wall(h, rt);
                    state.was = h.getLevel().getBlockState(state.wall);
                    state.restored = StrategosServer.get().protection().restored();
                    h.getLevel()
                            .getChunkAt(state.wall)
                            .setBlockState(state.wall, Blocks.AIR.defaultBlockState(), false);
                    h.getLevel().getChunkAt(free).setBlockState(free, Blocks.AIR.defaultBlockState(), false);
                    check(h.getLevel().getBlockState(state.wall).isAir(), "the write did not land");
                })
                .thenExecuteAfter(1, () -> {
                    check(h.getLevel().getBlockState(state.wall).equals(state.was), "the wall is not back");
                    check(h.getLevel().getBlockState(free).isAir(), "a free block came back");
                    check(
                            StrategosServer.get().protection().restored() == state.restored + 1,
                            "restored more than one");
                })
                .thenSucceed();
    }

    /** BLD-07.3: a house raised by workers has each of its blocks in the index, owned by it. */
    @StrategosTest(batch = "protection", timeoutTicks = 1_500)
    public static void buildersBlocksAreProtected(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        ConstructionTests.town(rt, 3);
        h.startSequence()
                .thenWaitUntil(() -> check(handles(rt).length == 3, "no workers yet"))
                .thenExecute(() -> rt.submit(
                        ConstructionTests.P0,
                        new PlaceBuilding(
                                ConstructionTests.HOUSE, 20, 20, 0, UnitSet.of(handles(rt)), QueueMode.REPLACE)))
                .thenWaitUntil(() -> check(house(rt).state() == BuildingStore.COMPLETE, "not finished"))
                .thenExecuteAfter(20, () -> {
                    int owner = house(rt).handle();
                    for (BlockPos pos : walls(rt)) {
                        check(index(h).owner(pos) == owner, pos + " is owned by " + index(h).owner(pos));
                    }
                })
                .thenSucceed();
    }
}
