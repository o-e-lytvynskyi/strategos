package dev.strategos.core.sim.stats;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Evaluated values (ADR-0013): one per template and faction, or a unit's overlay over its own sources. It is fresh
 * while its stamps equal its faction's modifier-set version and, for an overlay, its unit's personal version.
 */
final class StatBlock {
    final double[] values;
    final List<@Nullable List<String>> tokens;
    long version = -1; // the faction's version it was evaluated at; -1 before the first evaluation
    long personal = -1; // an overlay's: its unit's personal version

    StatBlock(int numbers, int lists) {
        values = new double[numbers];
        tokens = new ArrayList<>(lists);
        for (int k = 0; k < lists; k++) {
            tokens.add(null);
        }
    }
}
