package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A map's terrain hash (MAP-01): SHA-256 over the palette index of every voxel, each as two bytes big-endian, X then Z
 * then Y, in hex. The generator, the importer reading the world back and the exporter all compute it here.
 */
@CoreApi
public final class TerrainHash {
    /** The palette index of the voxel at map-local {@code x}, {@code y}, {@code z}, 0–65,535. */
    @FunctionalInterface
    public interface Voxels {
        int indexAt(int x, int y, int z);
    }

    private TerrainHash() {}

    public static String of(int sizeX, int sizeZ, int height, Voxels voxels) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("no SHA-256", e);
        }
        byte[] column = new byte[height * 2];
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int y = 0; y < height; y++) {
                    int index = voxels.indexAt(x, y, z);
                    column[y * 2] = (byte) (index >>> 8);
                    column[y * 2 + 1] = (byte) index;
                }
                sha.update(column);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : sha.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
