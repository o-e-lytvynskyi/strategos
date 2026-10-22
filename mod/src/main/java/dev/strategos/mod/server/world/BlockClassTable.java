package dev.strategos.mod.server.world;

import dev.strategos.core.sim.nav.BlockClass;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Every block state's {@link BlockClass} (04 §9.2, PATH-02.1), indexed by its id in {@code Block.BLOCK_STATE_REGISTRY}
 * (Block.java:68): built once on the main thread at server start and after a data map reload, then immutable, so
 * jobs read it from any thread.
 *
 * <p>Vanilla's doors, gates, fences, walls, leaves, climbables and fluids are recognised by their classes and fluid types,
 * so the table is complete before tags load (L2 tests); the tags add other mods' blocks, and a tag reload rebuilds the
 * table.
 *
 * <ul>
 *   <li>the collision top from {@code getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)}
 *       (BlockBehaviour.java:729), in 1/16 block, capped at 24 (fences and walls are 1.5 high);
 *   <li>standable when the shape's top faces cover at least half the block and the block is not a fence, wall,
 *       leaves or climbable (BlockTags.java:53, 46, 49, 102);
 *   <li>doors and fence gates (24, 111), water and lava from the fluid state, a fixed list of hazards and slow blocks;
 *   <li>states with a random offset or a context-dependent shape are flagged {@code POSITION_DEPENDENT};
 *   <li>the {@code strategos:nav_class} data map overrides last.
 * </ul>
 */
public final class BlockClassTable {
    private final short[] classes;

    private BlockClassTable(short[] classes) {
        this.classes = classes;
    }

    /** Classifies every registered state, then applies each block's override, if any. */
    public static BlockClassTable build(Function<Block, @Nullable NavClassOverride> overrides) {
        short[] classes = new short[Block.BLOCK_STATE_REGISTRY.size()];
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            short c = classify(state);
            NavClassOverride override = overrides.apply(state.getBlock());
            if (override != null) {
                c = override.apply(c);
            }
            classes[Block.BLOCK_STATE_REGISTRY.getId(state)] = c;
        }
        return new BlockClassTable(classes);
    }

    public short classOf(BlockState state) {
        return classes[Block.BLOCK_STATE_REGISTRY.getId(state)];
    }

    public short classOf(int stateId) {
        return classes[stateId];
    }

    /** Whether a class needs the block's real position: its shape depends on it, and it has a collision. */
    public static boolean needsPosition(short c) {
        return BlockClass.is(c, BlockClass.POSITION_DEPENDENT) && !BlockClass.is(c, BlockClass.PASSABLE);
    }

    /**
     * The class of {@code state} where it stands: the table's, unless {@link #needsPosition} says its shape depends on
     * its position, then from its real collision shape there. Main thread only, since it reads the level.
     */
    public short classAt(BlockState state, BlockGetter level, BlockPos pos) {
        short c = classOf(state);
        if (!needsPosition(c)) {
            return c;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        int top = shape.isEmpty() ? 0 : Math.min(24, (int) Math.round(shape.max(Direction.Axis.Y) * 16));
        int flags = c & ~(BlockClass.TOP_MASK | BlockClass.PASSABLE | BlockClass.STANDABLE);
        if (shape.isEmpty()) {
            flags |= BlockClass.PASSABLE;
        }
        boolean obstacleOnly = (flags & (BlockClass.DOOR | BlockClass.CLIMBABLE | BlockClass.LEAVES)) != 0;
        if (!obstacleOnly && top > 0 && topFaceArea(shape) >= 0.5) {
            flags |= BlockClass.STANDABLE;
        }
        return BlockClass.of(top, flags);
    }

    public int size() {
        return classes.length;
    }

    /** The class from the state alone, before any data map override. */
    static short classify(BlockState state) {
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        int top = shape.isEmpty() ? 0 : Math.min(24, (int) Math.round(shape.max(Direction.Axis.Y) * 16));
        int flags = 0;
        if (shape.isEmpty()) {
            flags |= BlockClass.PASSABLE;
        }
        FluidState fluid = state.getFluidState();
        if (fluid.getType().isSame(Fluids.WATER) || fluid.is(FluidTags.WATER)) {
            flags |= BlockClass.WATER;
        }
        if (fluid.getType().isSame(Fluids.LAVA) || fluid.is(FluidTags.LAVA)) {
            flags |= BlockClass.LAVA | BlockClass.HAZARD;
        }
        if (hazard(state)) {
            flags |= BlockClass.HAZARD;
        }
        if (state.is(Blocks.SOUL_SAND) || state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.COBWEB)) {
            flags |= BlockClass.SLOW;
        }
        boolean obstacleOnly = false;
        Block block = state.getBlock();
        if (block instanceof DoorBlock
                || block instanceof FenceGateBlock
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.FENCE_GATES)) {
            flags |= BlockClass.DOOR;
            obstacleOnly = true;
        }
        if (climbable(block) || state.is(BlockTags.CLIMBABLE)) {
            flags |= BlockClass.CLIMBABLE;
            obstacleOnly = true;
        }
        if (block instanceof LeavesBlock || state.is(BlockTags.LEAVES)) {
            flags |= BlockClass.LEAVES;
            obstacleOnly = true;
        }
        if (block instanceof FenceBlock
                || block instanceof WallBlock
                || state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS)) {
            obstacleOnly = true;
        }
        if (!obstacleOnly && top > 0 && topFaceArea(shape) >= 0.5) {
            flags |= BlockClass.STANDABLE;
        }
        if (state.hasOffsetFunction() || state.is(Blocks.SCAFFOLDING) || state.is(Blocks.POWDER_SNOW)) {
            flags |= BlockClass.POSITION_DEPENDENT;
        }
        return BlockClass.of(top, flags);
    }

    /** Vanilla's climbables by class, so the table needs no loaded tags; the tag adds other mods' ones. */
    private static boolean climbable(Block block) {
        return block instanceof LadderBlock
                || block instanceof VineBlock
                || block instanceof ScaffoldingBlock
                || block instanceof WeepingVinesBlock
                || block instanceof TwistingVinesBlock
                || block instanceof CaveVinesBlock
                || block instanceof CaveVinesPlantBlock;
    }

    private static boolean hazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE);
    }

    /** The area of the shape's boxes whose top is the shape's top, at most 1. */
    private static double topFaceArea(VoxelShape shape) {
        double top = shape.max(Direction.Axis.Y);
        double area = 0;
        for (AABB box : shape.toAabbs()) {
            if (box.maxY >= top - 1e-6) {
                area += (box.maxX - box.minX) * (box.maxZ - box.minZ);
            }
        }
        return Math.min(area, 1.0);
    }
}
