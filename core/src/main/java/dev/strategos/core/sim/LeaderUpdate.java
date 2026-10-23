package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Where the commander of player slot {@code slot} stands in first person this tick (CAM-07): X and Z in the match's
 * own coordinates and the way it faces, Minecraft's yaw in degrees. Journaled, and read by the units that follow it; it
 * holds for its tick only, so a commander back in the RTS view leads no one and nothing about it is saved or hashed.
 */
@CoreApi
public record LeaderUpdate(int slot, float x, float z, float yaw) implements InputEvent {
    /** The most player slots a match seats. */
    public static final int SLOTS = 16;

    public LeaderUpdate {
        if (slot < 0 || slot >= SLOTS) {
            throw new IllegalArgumentException("leader slot " + slot + " outside 0.." + (SLOTS - 1));
        }
        if (!Float.isFinite(x) || !Float.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("leader " + slot + " has a coordinate or yaw that is not finite");
        }
    }
}
