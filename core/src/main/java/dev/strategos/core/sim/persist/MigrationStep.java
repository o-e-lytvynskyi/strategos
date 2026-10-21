package dev.strategos.core.sim.persist;

import com.mojang.serialization.Dynamic;

/** Upgrades a saved snapshot from version {@link #from()} to the next, on a format-neutral DFU {@code Dynamic}. */
public interface MigrationStep {
    int from();

    <T> Dynamic<T> apply(Dynamic<T> input);
}
