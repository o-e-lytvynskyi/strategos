package dev.strategos.mod.server.world;

import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.match.SlotAllocator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * The assumptions that hold only in an arena world (04 §3.2). There, every match a player makes takes the next slot
 * and force-loads its whole space; M1's later tasks add page prewarm, a body for every unit, adventure mode,
 * protection and the camera box. Any other world keeps M0's behaviour, and M13's {@code OpenWorldPolicy} replaces this
 * one there.
 */
public final class ArenaPolicy {
    /** The Y band of a match made in a slot without a map: the map's own band replaces it (MAP-01). */
    public static final int MIN_Y = 0;

    public static final int MAX_Y = 127;

    /** Where such a match's units stand. */
    public static final int FLOOR_Y = 64;

    private ArenaPolicy() {}

    /** Whether the arena's rules govern a match: one a player made, in the overworld of an arena world. */
    public static boolean governs(MinecraftServer server, MatchSpace space, MatchSource source) {
        return source == MatchSource.GENERATED && space.level().equals(Level.OVERWORLD) && ArenaWorld.isArena(server);
    }

    /** Slot {@code slot}'s space of {@code sizeX} x {@code sizeZ} blocks over {@link #MIN_Y}..{@link #MAX_Y}. */
    public static MatchSpace slotSpace(int slot, int sizeX, int sizeZ) {
        return new MatchSpace(
                Level.OVERWORLD, new BlockPos(SlotAllocator.originX(slot), MIN_Y, 0), sizeX, sizeZ, MIN_Y, MAX_Y);
    }
}
