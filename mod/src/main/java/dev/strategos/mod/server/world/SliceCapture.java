package dev.strategos.mod.server.world;

import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.VoxelSlice;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;

/**
 * Captures a page on the main thread (04 §9.2, PATH-15): copies of the sections under its 16 x 16 columns over the
 * match's band ({@code PalettedContainer#copy}, PalettedContainer.java:290; pages align to the match's origin, so one
 * may span four chunks), and, for the rare sections holding a state whose shape depends on its position, those voxels'
 * classes taken there with their real position; then the match's waiting writes, which win. The {@link PageJob} it
 * returns does the rest anywhere.
 */
public final class SliceCapture {
    private SliceCapture() {}

    public static PageJob capture(ServerLevel level, MatchSpace space, PageCoord coord, BlockClassTable table) {
        return capture(level, space, coord, table, Map.of(), TerrainPage.UNKNOWN);
    }

    /**
     * A capture after the step of match tick {@code sampleTick}, with {@code pending}, the writes the match's sites still
     * wait to make there, laid over the copied blocks, so the page is the world as the core has asked for it up to that
     * tick (M3 spec §1.6 row 5).
     */
    public static PageJob capture(
            ServerLevel level,
            MatchSpace space,
            PageCoord coord,
            BlockClassTable table,
            Map<BlockPos, BlockState> pending,
            long sampleTick) {
        int x0 = space.origin().getX() + coord.x() * VoxelSlice.SIDE;
        int z0 = space.origin().getZ() + coord.z() * VoxelSlice.SIDE;
        int minY = space.minY();
        int height = space.maxY() - space.minY() + 1;
        int chunkX0 = x0 >> 4;
        int chunkZ0 = z0 >> 4;
        int chunksX = ((x0 + VoxelSlice.SIDE - 1) >> 4) - chunkX0 + 1;
        int chunksZ = ((z0 + VoxelSlice.SIDE - 1) >> 4) - chunkZ0 + 1;
        int sectionY0 = minY >> 4;
        int sections = (space.maxY() >> 4) - sectionY0 + 1;
        List<@Nullable PalettedContainer<BlockState>> copies = new ArrayList<>(chunksX * chunksZ * sections);
        List<Integer> indices = new ArrayList<>();
        List<Short> classes = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cz = 0; cz < chunksZ; cz++) {
            for (int cx = 0; cx < chunksX; cx++) {
                LevelChunk chunk = level.getChunk(chunkX0 + cx, chunkZ0 + cz);
                for (int s = 0; s < sections; s++) {
                    int sectionIndex = chunk.getSectionIndexFromSectionY(sectionY0 + s);
                    LevelChunkSection section = sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()
                            ? null
                            : chunk.getSection(sectionIndex);
                    if (section == null || section.hasOnlyAir()) {
                        copies.add(null);
                        continue;
                    }
                    copies.add(section.getStates().copy());
                    if (section.maybeHas(state -> BlockClassTable.needsPosition(table.classOf(state)))) {
                        positional(
                                level,
                                table,
                                section,
                                chunkX0 + cx,
                                chunkZ0 + cz,
                                sectionY0 + s,
                                x0,
                                z0,
                                minY,
                                height,
                                pos,
                                indices,
                                classes);
                    }
                }
            }
        }
        for (Map.Entry<BlockPos, BlockState> w : pending.entrySet()) {
            BlockPos at = w.getKey();
            int px = at.getX() - x0;
            int pz = at.getZ() - z0;
            int py = at.getY() - minY;
            if (px >= 0 && pz >= 0 && px < VoxelSlice.SIDE && pz < VoxelSlice.SIDE && py >= 0 && py < height) {
                short cls = table.classOf(w.getValue());
                indices.add((px * VoxelSlice.SIDE + pz) * height + py);
                classes.add(BlockClassTable.needsPosition(cls) ? table.classAt(w.getValue(), level, at) : cls);
            }
        }
        int[] overrideIndices = new int[indices.size()];
        short[] overrideClasses = new short[classes.size()];
        for (int i = 0; i < overrideIndices.length; i++) {
            overrideIndices[i] = indices.get(i);
            overrideClasses[i] = classes.get(i);
        }
        return new PageJob(
                coord,
                x0,
                z0,
                minY,
                height,
                chunkX0,
                chunkZ0,
                chunksX,
                sectionY0,
                sections,
                copies,
                overrideIndices,
                overrideClasses,
                table,
                sampleTick);
    }

    /** The page's voxels of one section whose class needs the real position, classified there. */
    private static void positional(
            ServerLevel level,
            BlockClassTable table,
            LevelChunkSection section,
            int chunkX,
            int chunkZ,
            int sectionY,
            int x0,
            int z0,
            int minY,
            int height,
            BlockPos.MutableBlockPos pos,
            List<Integer> indices,
            List<Short> classes) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int px = chunkX * 16 + lx - x0;
                int pz = chunkZ * 16 + lz - z0;
                if (px < 0 || pz < 0 || px >= VoxelSlice.SIDE || pz >= VoxelSlice.SIDE) {
                    continue;
                }
                for (int ly = 0; ly < 16; ly++) {
                    int y = sectionY * 16 + ly - minY;
                    if (y < 0 || y >= height) {
                        continue;
                    }
                    BlockState state = section.getBlockState(lx, ly, lz);
                    if (BlockClassTable.needsPosition(table.classOf(state))) {
                        pos.set(chunkX * 16 + lx, sectionY * 16 + ly, chunkZ * 16 + lz);
                        indices.add((px * VoxelSlice.SIDE + pz) * height + y);
                        classes.add(table.classAt(state, level, pos));
                    }
                }
            }
        }
    }
}
