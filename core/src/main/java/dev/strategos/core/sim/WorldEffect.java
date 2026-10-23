package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.world.WritePriority;

/**
 * A block the core wants placed or removed (04 §11.7, BLD-15): at match-local {@code x}, {@code z} and absolute {@code
 * y}; the block state as Minecraft writes it ({@code minecraft:oak_stairs[facing=east]}; {@link #AIR} removes; one that
 * is only properties, {@code [age=2]}, sets them on the block standing there, as a bush shows what it holds); the site
 * and priority the writer schedules it by; and the owner that protects it, a building's or node's handle, or 0. The
 * blocks are the picture: the core never waits for them. No producer in M1; construction, nodes and damage add them.
 * It lives beside {@link OutputEvent}, since a sealed type outside a named module permits only its own package.
 */
@CoreApi
public record WorldEffect(int site, WritePriority priority, int x, int y, int z, String state, int owner)
        implements OutputEvent {
    public static final String AIR = "minecraft:air";

    public WorldEffect {
        if (state.isEmpty()) {
            throw new IllegalArgumentException("a world effect needs a block state");
        }
        if (owner < 0) {
            throw new IllegalArgumentException("owner " + owner + " is negative");
        }
    }

    public boolean removes() {
        return state.equals(AIR);
    }

    /** Whether the state is only properties, set on the block that stands there. */
    public boolean propertiesOnly() {
        return state.charAt(0) == '[';
    }
}
