package dev.strategos.core.sim;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.sim.build.DamageRanks;
import java.util.TreeMap;

/** Each blueprint's damage ranks (BLD-05), worked out once a match: derived, neither hashed nor saved. */
final class DamageRankCache {
    private final TreeMap<String, int[]> byBlueprint = new TreeMap<>(); // by blueprint id

    int[] of(Blueprint b) {
        return byBlueprint.computeIfAbsent(b.id(), id -> DamageRanks.of(b));
    }
}
