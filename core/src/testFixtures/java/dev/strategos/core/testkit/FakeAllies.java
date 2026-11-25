package dev.strategos.core.testkit;

import dev.strategos.core.sim.Allies;

/**
 * Alliances for tests until MATCH-06 (M9 spec §2.1): teams given as a faction → team array; factions of one team are
 * allied, Gaia (index {@code teams.length} and beyond) with nobody. AFTER-M7: the allied GameTests run with M7's real
 * teams too.
 */
public record FakeAllies(int[] teams) implements Allies {
    public FakeAllies {
        teams = teams.clone();
    }

    /** Factions 0 and 1 allied against 2 (and 3, if present). */
    public static FakeAllies twoAgainstRest(int players) {
        int[] t = new int[players];
        for (int f = 2; f < players; f++) {
            t[f] = 1;
        }
        return new FakeAllies(t);
    }

    @Override
    public boolean allied(int a, int b) {
        return a >= 0 && b >= 0 && a < teams.length && b < teams.length && teams[a] == teams[b];
    }
}
