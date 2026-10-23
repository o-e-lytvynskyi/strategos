package dev.strategos.mod.server.world;

import dev.strategos.core.port.JobScratch;
import dev.strategos.core.port.PureJob;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.VoxelSlice;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;

/**
 * A page build as a pure job (04 §9.2, PATH-15): it classifies the captured section copies through the immutable class
 * table, applies the classes {@link SliceCapture} found with real positions, and builds the page. It holds no level,
 * chunk or entity (rule {@code JOBS_NEVER_TOUCH_THE_LEVEL}), so it runs on any thread with the same result.
 */
final class PageJob implements PureJob<TerrainPage> {
    private final PageCoord coord;
    private final int x0;
    private final int z0;
    private final int minY;
    private final int height;
    private final int chunkX0;
    private final int chunkZ0;
    private final int chunksX;
    private final int sectionY0;
    private final int sections;
    private final List<@Nullable PalettedContainer<BlockState>> copies;
    private final int[] overrideIndices;
    private final short[] overrideClasses;
    private final BlockClassTable table;
    private final long sampleTick;

    PageJob(
            PageCoord coord,
            int x0,
            int z0,
            int minY,
            int height,
            int chunkX0,
            int chunkZ0,
            int chunksX,
            int sectionY0,
            int sections,
            List<@Nullable PalettedContainer<BlockState>> copies,
            int[] overrideIndices,
            short[] overrideClasses,
            BlockClassTable table,
            long sampleTick) {
        this.coord = coord;
        this.x0 = x0;
        this.z0 = z0;
        this.minY = minY;
        this.height = height;
        this.chunkX0 = chunkX0;
        this.chunkZ0 = chunkZ0;
        this.chunksX = chunksX;
        this.sectionY0 = sectionY0;
        this.sections = sections;
        this.copies = copies;
        this.overrideIndices = overrideIndices;
        this.overrideClasses = overrideClasses;
        this.table = table;
        this.sampleTick = sampleTick;
    }

    @Override
    public TerrainPage run(JobScratch scratch) {
        short[] voxels = new short[VoxelSlice.SIDE * VoxelSlice.SIDE * height];
        for (int x = 0; x < VoxelSlice.SIDE; x++) {
            int wx = x0 + x;
            for (int z = 0; z < VoxelSlice.SIDE; z++) {
                int wz = z0 + z;
                int chunk = ((wz >> 4) - chunkZ0) * chunksX + ((wx >> 4) - chunkX0);
                for (int y = 0; y < height; y++) {
                    int wy = minY + y;
                    PalettedContainer<BlockState> section = copies.get(chunk * sections + (wy >> 4) - sectionY0);
                    voxels[(x * VoxelSlice.SIDE + z) * height + y] =
                            section == null ? BlockClass.AIR : table.classOf(section.get(wx & 15, wy & 15, wz & 15));
                }
            }
        }
        for (int i = 0; i < overrideIndices.length; i++) {
            voxels[overrideIndices[i]] = overrideClasses[i];
        }
        PageBuilder builder = scratch.get(PageBuilder.class, () -> new PageBuilder(MobilityClass.INFANTRY));
        return TerrainPage.build(coord, new VoxelSlice(height, voxels), builder, sampleTick);
    }
}
