package dev.strategos.mod.server.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.strategos.core.testkit.ProvingGround;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;

/**
 * MAP-01: map tiles are vanilla structures both ways. Vanilla's {@code StructureTemplate} loads the tiles this mod
 * writes, this mod reads what vanilla saves, and the committed proving-ground tiles hold the generator's blocks.
 */
class MapTileCodecTest {
    private static final List<MapTile.Placed> BLOCKS = List.of(
            new MapTile.Placed(0, 0, 0, Blocks.STONE.defaultBlockState()),
            new MapTile.Placed(1, 2, 3, Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true)),
            new MapTile.Placed(4, 5, 6, Blocks.WATER.defaultBlockState()),
            new MapTile.Placed(7, 1, 2, Blocks.STONE.defaultBlockState()));

    @Test
    void vanillaLoadsTheTilesThisModWrites() {
        CompoundTag tag = MapTile.write(8, 6, 8, BLOCKS);

        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);

        assertThat(template.getSize()).isEqualTo(new Vec3i(8, 6, 8));
        assertThat(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.STONE))
                .extracting(StructureTemplate.StructureBlockInfo::pos)
                .containsExactlyInAnyOrder(new BlockPos(0, 0, 0), new BlockPos(7, 1, 2));
        assertThat(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.OAK_FENCE))
                .extracting(StructureTemplate.StructureBlockInfo::state)
                .containsExactly(Blocks.OAK_FENCE.defaultBlockState().setValue(FenceBlock.EAST, true));
    }

    @Test
    void thisModReadsWhatVanillaSavesAndSkipsAir() {
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), MapTile.write(8, 6, 8, BLOCKS));
        CompoundTag saved = template.save(new CompoundTag());

        List<MapTile.Placed> read = MapTile.read(saved, BuiltInRegistries.BLOCK.asLookup());

        assertThat(read).containsExactlyInAnyOrderElementsOf(BLOCKS);
        assertThat(MapTile.read(MapTile.write(8, 6, 8, BLOCKS), BuiltInRegistries.BLOCK.asLookup()))
                .containsExactlyElementsOf(BLOCKS);
    }

    @Test
    void theCommittedTilesHoldTheGeneratorsColumns() throws IOException {
        ProvingGround ground = ProvingGround.generate();
        Path tiles = Path.of(System.getProperty("strategos.repoRoot"))
                .resolve("mod/src/devtools/resources/data/strategos/structure/strategos/maps/proving_ground");
        for (int[] tile : new int[][] {{0, 0}, {5, 2}, {6, 4}, {9, 8}, {11, 11}}) {
            CompoundTag tag = NbtIo.readCompressed(
                    tiles.resolve("x" + tile[0] + "_z" + tile[1] + ".nbt"), NbtAccounter.unlimitedHeap());
            Set<String> read = new HashSet<>();
            for (MapTile.Placed p : MapTile.read(tag, BuiltInRegistries.BLOCK.asLookup())) {
                read.add((tile[0] * 32 + p.x()) + "," + p.y() + "," + (tile[1] * 32 + p.z()) + "="
                        + BlockStateParser.serialize(p.state()));
            }
            Set<String> expected = new HashSet<>();
            for (int x = tile[0] * 32; x < tile[0] * 32 + 32; x++) {
                for (int z = tile[1] * 32; z < tile[1] * 32 + 32; z++) {
                    for (int y = ground.lowestStored(x, z); y <= ground.top(x, z); y++) {
                        byte b = ground.block(x, y, z);
                        if (b != ProvingGround.AIR) {
                            expected.add(x + "," + y + "," + z + "=" + canonical(ProvingGround.PALETTE.get(b)));
                        }
                    }
                }
            }
            assertThat(read).as("tile %d, %d", tile[0], tile[1]).isEqualTo(expected);
        }
    }

    /** A palette entry as Minecraft prints the whole state, every property included. */
    private static String canonical(String state) {
        try {
            return BlockStateParser.serialize(
                    BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false)
                            .blockState());
        } catch (CommandSyntaxException e) {
            throw new AssertionError(state, e);
        }
    }
}
