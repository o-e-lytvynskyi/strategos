package dev.strategos.mod.platform;

import dev.strategos.mod.Strategos;
import net.minecraft.resources.ResourceLocation;

/** The mod's resource ids; the one place that names {@code ResourceLocation}'s factory. */
public final class Ids {
    private Ids() {}

    /** A full {@code namespace:path} id; a malformed one throws. */
    public static ResourceLocation parse(String id) {
        return ResourceLocation.parse(id);
    }

    /** {@code strategos:<path>}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Strategos.MOD_ID, path);
    }
}
