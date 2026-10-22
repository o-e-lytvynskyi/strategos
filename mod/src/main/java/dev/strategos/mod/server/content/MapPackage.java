package dev.strategos.mod.server.content;

import dev.strategos.core.content.MapManifest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A loaded map package: its manifest, every tile's blocks ({@code tiles} in Z then X order of tiles), the base block,
 * and the manifest's palette as block states, indexed for the terrain hash.
 */
public final class MapPackage {
    private final MapManifest manifest;
    private final List<List<MapTile.Placed>> tiles;
    private final BlockState base;
    private final Map<BlockState, Integer> palette = new HashMap<>();

    public MapPackage(
            MapManifest manifest, List<List<MapTile.Placed>> tiles, BlockState base, List<BlockState> palette) {
        if (tiles.size() != manifest.tilesX() * manifest.tilesZ()) {
            throw new IllegalArgumentException(manifest.id() + ": " + tiles.size() + " tiles, the manifest names "
                    + manifest.tilesX() * manifest.tilesZ());
        }
        this.manifest = manifest;
        this.tiles = List.copyOf(tiles);
        this.base = base;
        for (int i = 0; i < palette.size(); i++) {
            this.palette.putIfAbsent(palette.get(i), i);
        }
    }

    public MapManifest manifest() {
        return manifest;
    }

    /** Tile ({@code i}, {@code j}): {@code i} along X, {@code j} along Z. */
    public List<MapTile.Placed> tile(int i, int j) {
        return tiles.get(j * manifest.tilesX() + i);
    }

    public BlockState base() {
        return base;
    }

    /** A block state's palette index, or 0xFFFF when the manifest does not list it. */
    public int paletteIndex(BlockState state) {
        Integer index = palette.get(state);
        return index == null ? 0xFFFF : index;
    }
}
