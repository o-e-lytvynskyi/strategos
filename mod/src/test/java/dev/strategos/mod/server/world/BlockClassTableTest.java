package dev.strategos.mod.server.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.nav.BlockClass;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.Test;

/** PATH-02.1: every registered block state classifies, and the fixture blocks match the expected table. */
class BlockClassTableTest {
    private static final BlockClassTable TABLE = BlockClassTable.build(block -> null);

    @Test
    void everyRegisteredStateClassifies() {
        assertThat(TABLE.size()).isEqualTo(Block.BLOCK_STATE_REGISTRY.size()).isGreaterThan(20_000);
        int standable = 0;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            short c = TABLE.classOf(state);
            assertThat(BlockClass.top(c)).as("%s", state).isBetween(0, 24);
            if (BlockClass.is(c, BlockClass.STANDABLE)) {
                standable++;
                assertThat(BlockClass.top(c))
                        .as("a standable %s has a top", state)
                        .isPositive();
            }
        }
        assertThat(standable).isGreaterThan(5_000);
    }

    @Test
    void theFixtureBlocksMatchTheTable() {
        expect(Blocks.AIR.defaultBlockState(), 0, BlockClass.PASSABLE);
        expect(Blocks.STONE.defaultBlockState(), 16, BlockClass.STANDABLE);
        expect(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 8, BlockClass.STANDABLE);
        expect(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 16, BlockClass.STANDABLE);
        expect(
                Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH),
                16,
                BlockClass.STANDABLE);
        expect(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), 0, BlockClass.PASSABLE);
        expect(Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4), 6, BlockClass.STANDABLE);
        expect(Blocks.WHITE_CARPET.defaultBlockState(), 1, BlockClass.STANDABLE);
        expect(Blocks.DIRT_PATH.defaultBlockState(), 15, BlockClass.STANDABLE);
        expect(Blocks.FARMLAND.defaultBlockState(), 15, BlockClass.STANDABLE);
        expect(Blocks.OAK_FENCE.defaultBlockState(), 24, 0);
        expect(Blocks.COBBLESTONE_WALL.defaultBlockState(), 24, 0);
        expect(Blocks.WATER.defaultBlockState(), 0, BlockClass.PASSABLE | BlockClass.WATER);
        expect(Blocks.LAVA.defaultBlockState(), 0, BlockClass.PASSABLE | BlockClass.LAVA | BlockClass.HAZARD);
        expect(Blocks.OAK_LEAVES.defaultBlockState(), 16, BlockClass.LEAVES);
        expect(Blocks.MAGMA_BLOCK.defaultBlockState(), 16, BlockClass.STANDABLE | BlockClass.HAZARD);
        expect(Blocks.SOUL_SAND.defaultBlockState(), 14, BlockClass.STANDABLE | BlockClass.SLOW);
        expect(Blocks.COBWEB.defaultBlockState(), 0, BlockClass.PASSABLE | BlockClass.SLOW);
        short door = TABLE.classOf(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        assertThat(BlockClass.is(door, BlockClass.DOOR)).isTrue();
        assertThat(BlockClass.is(door, BlockClass.STANDABLE)).isFalse();
        short ladder = TABLE.classOf(Blocks.LADDER.defaultBlockState());
        assertThat(BlockClass.is(ladder, BlockClass.CLIMBABLE) && !BlockClass.is(ladder, BlockClass.STANDABLE))
                .isTrue();
        assertThat(BlockClass.is(TABLE.classOf(Blocks.BAMBOO.defaultBlockState()), BlockClass.POSITION_DEPENDENT))
                .as("bamboo has a random offset")
                .isTrue();
        assertThat(BlockClass.is(TABLE.classOf(Blocks.CACTUS.defaultBlockState()), BlockClass.HAZARD))
                .isTrue();
    }

    @Test
    void aNavClassOverrideWinsAndIsValidated() {
        NavClassOverride override = new NavClassOverride(Optional.of(16), List.of("standable"), List.of("leaves"));
        BlockClassTable table = BlockClassTable.build(block -> Blocks.OAK_LEAVES.equals(block) ? override : null);

        assertThat(table.classOf(Blocks.OAK_LEAVES.defaultBlockState())).isEqualTo(BlockClass.SOLID);
        assertThat(table.classOf(Blocks.BIRCH_LEAVES.defaultBlockState()))
                .isEqualTo(TABLE.classOf(Blocks.BIRCH_LEAVES.defaultBlockState()));
        assertThat(NavClassOverride.CODEC
                        .parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"set\": [\"flying\"]}"))
                        .error())
                .isPresent();
        assertThatThrownBy(() ->
                        new NavClassOverride(Optional.empty(), List.of("flying"), List.of()).apply(BlockClass.SOLID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void expect(BlockState state, int top, int flags) {
        short c = TABLE.classOf(state);
        assertThat(BlockClass.top(c)).as("the top of %s", state).isEqualTo(top);
        assertThat(c & ~BlockClass.TOP_MASK & ~BlockClass.POSITION_DEPENDENT)
                .as("the flags of %s", state)
                .isEqualTo(flags);
    }
}
