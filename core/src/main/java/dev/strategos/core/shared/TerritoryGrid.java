package dev.strategos.core.shared;

import java.util.Arrays;

/**
 * Who owns each 8 × 8-block tile (M9 spec §2.2): a faction or nobody, and whether the tile is connected to one of its
 * owner's roots. Tile (0, 0) covers blocks 0..7 on both axes of the match's bounds. A value per tile: bit 7 set is
 * neutral; otherwise bits 0..5 are the owner and bit 6 is "connected". Shared by the simulation, the payload and the
 * client (rule B3 keeps the client out of {@code sim}).
 */
@CoreApi
public final class TerritoryGrid {
    /** Blocks per tile side. */
    public static final int TILE = 8;
    /** A neutral tile's value. */
    public static final byte NEUTRAL = (byte) 0x80;
    /** The most factions a tile can name. */
    public static final int MAX_FACTIONS = 64;

    public static final int CONNECTED = 0x40;
    public static final int OWNER = 0x3F;

    private final int tilesX;
    private final int tilesZ;
    private final byte[] tiles;

    public TerritoryGrid(int tilesX, int tilesZ) {
        if (tilesX <= 0 || tilesZ <= 0) {
            throw new IllegalArgumentException("a grid of " + tilesX + " × " + tilesZ + " tiles");
        }
        this.tilesX = tilesX;
        this.tilesZ = tilesZ;
        this.tiles = new byte[tilesX * tilesZ];
        Arrays.fill(tiles, NEUTRAL);
    }

    /** The grid covering {@code sizeX × sizeZ} blocks: a partial edge tile counts as a whole one. */
    public static TerritoryGrid forBlocks(int sizeX, int sizeZ) {
        return new TerritoryGrid(tilesFor(sizeX), tilesFor(sizeZ));
    }

    public static int tilesFor(int blocks) {
        return (blocks + TILE - 1) / TILE;
    }

    public int tilesX() {
        return tilesX;
    }

    public int tilesZ() {
        return tilesZ;
    }

    /** The owner of tile ({@code tx}, {@code tz}), or -1 for neutral or outside the grid. */
    public int owner(int tx, int tz) {
        if (tx < 0 || tz < 0 || tx >= tilesX || tz >= tilesZ) {
            return -1;
        }
        int v = tiles[tz * tilesX + tx];
        return (v & 0x80) != 0 ? -1 : v & OWNER;
    }

    /** Whether tile ({@code tx}, {@code tz}) is owned and connected to one of its owner's roots. */
    public boolean connected(int tx, int tz) {
        if (owner(tx, tz) < 0) {
            return false;
        }
        return (tiles[tz * tilesX + tx] & CONNECTED) != 0;
    }

    /** The owner of the tile under block column ({@code x}, {@code z}), or -1. */
    public int ownerAt(int x, int z) {
        return x < 0 || z < 0 ? -1 : owner(x / TILE, z / TILE);
    }

    /** Whether the tile under block column ({@code x}, {@code z}) is owned and connected. */
    public boolean connectedAt(int x, int z) {
        return x >= 0 && z >= 0 && connected(x / TILE, z / TILE);
    }

    /** The raw value of tile {@code index} (row-major): the payload's unit. */
    public byte value(int index) {
        return tiles[index];
    }

    public int size() {
        return tiles.length;
    }

    /** Sets tile {@code index}'s raw value: for the kernel and the payload's decoder. */
    public void set(int index, byte value) {
        tiles[index] = value;
    }

    /** Copies {@code other}'s tiles into this grid of the same size. */
    public void copyFrom(TerritoryGrid other) {
        if (other.tilesX != tilesX || other.tilesZ != tilesZ) {
            throw new IllegalArgumentException("grids of different sizes");
        }
        System.arraycopy(other.tiles, 0, tiles, 0, tiles.length);
    }

    /** A 64-bit FNV-1a hash of the tiles, mixed into the match's state hash. */
    public long hash() {
        long h = 0xcbf29ce484222325L;
        for (byte b : tiles) {
            h = (h ^ (b & 0xFF)) * 0x100000001b3L;
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TerritoryGrid g
                && g.tilesX == tilesX
                && g.tilesZ == tilesZ
                && Arrays.equals(g.tiles, tiles);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(tiles);
    }

    @Override
    public String toString() {
        return "TerritoryGrid[" + tilesX + " × " + tilesZ + "]";
    }
}
