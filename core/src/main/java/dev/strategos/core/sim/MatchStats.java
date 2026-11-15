package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What each faction trained, lost and built over the match (M6 §1.6): the end screen's counters. Saved and hashed
 * once any is non-zero; a match that never counts saves and hashes as before.
 */
public final class MatchStats {
    private final int factions;
    private final int[] unitsTrained;
    private final int[] unitsLost;
    private final int[] buildingsBuilt;
    private final int[] buildingsLost;
    private final long[][] phaseTicks;

    public MatchStats(int factions) {
        this.factions = factions;
        unitsTrained = new int[factions];
        unitsLost = new int[factions];
        buildingsBuilt = new int[factions];
        buildingsLost = new int[factions];
        phaseTicks = new long[factions][0];
    }

    public void unitTrained(int faction) {
        unitsTrained[faction]++;
    }

    public void unitLost(int faction) {
        unitsLost[faction]++;
    }

    public void buildingBuilt(int faction) {
        buildingsBuilt[faction]++;
    }

    public void buildingLost(int faction) {
        buildingsLost[faction]++;
    }

    public void phaseReached(int faction, int ordinal, long tick) {
        if (phaseTicks[faction].length <= ordinal) {
            long[] grown = Arrays.copyOf(phaseTicks[faction], ordinal + 1);
            Arrays.fill(grown, phaseTicks[faction].length, grown.length, -1L);
            phaseTicks[faction] = grown;
        }
        phaseTicks[faction][ordinal] = tick;
    }

    public long phaseTick(int faction, int ordinal) {
        return ordinal < phaseTicks[faction].length ? phaseTicks[faction][ordinal] : -1;
    }

    public int unitsTrained(int f) {
        return unitsTrained[f];
    }

    public int unitsLost(int f) {
        return unitsLost[f];
    }

    public int buildingsBuilt(int f) {
        return buildingsBuilt[f];
    }

    public int buildingsLost(int f) {
        return buildingsLost[f];
    }

    public boolean empty() {
        for (int f = 0; f < factions; f++) {
            if (unitsTrained[f] != 0
                    || unitsLost[f] != 0
                    || buildingsBuilt[f] != 0
                    || buildingsLost[f] != 0
                    || phaseTicks[f].length != 0) {
                return false;
            }
        }
        return true;
    }

    public long mix(long h) {
        if (empty()) {
            return h;
        }
        for (int[] a : new int[][] {unitsTrained, unitsLost, buildingsBuilt, buildingsLost}) {
            for (int v : a) {
                h = Hasher.mix(h, v);
            }
        }
        for (long[] ticks : phaseTicks) {
            h = Hasher.mix(h, ticks.length);
            for (long t : ticks) {
                h = Hasher.mix(h, t);
            }
        }
        return h;
    }

    public List<Long> save() {
        if (empty()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>(1 + 5 * factions);
        out.add((long) factions);
        for (int[] a : new int[][] {unitsTrained, unitsLost, buildingsBuilt, buildingsLost}) {
            for (int v : a) {
                out.add((long) v);
            }
        }
        for (long[] ticks : phaseTicks) {
            out.add((long) ticks.length);
            for (long t : ticks) {
                out.add(t);
            }
        }
        return out;
    }

    public void restore(List<Long> saved) {
        int counters = 1 + 4 * factions;
        if (saved.size() < counters + factions || saved.get(0) != factions) {
            return;
        }
        int at = 1;
        for (int[] a : new int[][] {unitsTrained, unitsLost, buildingsBuilt, buildingsLost}) {
            for (int f = 0; f < factions; f++) {
                a[f] = (int) (long) saved.get(at++);
            }
        }
        for (int f = 0; f < factions; f++) {
            int n = (int) (long) saved.get(at++);
            phaseTicks[f] = new long[n];
            for (int k = 0; k < n; k++) {
                phaseTicks[f][k] = saved.get(at++);
            }
        }
    }
}
