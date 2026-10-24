package dev.strategos.core.sim.prod;

import dev.strategos.core.shared.CoreApi;

/**
 * Each faction's population (ECO-06, 04 §11.5), kept as counters as units and buildings come and go, never by scans:
 * {@code used} is its units' {@code Cost/population} and what the entries training for it have reserved; {@code
 * housing} the {@code Housing/capacity} of its finished buildings; its cap is {@code min(housing + base, the match's
 * maximum)}. Neither hashed nor saved: both follow from the state, and a restore counts them once.
 */
@CoreApi
public final class Population {
    /** The path of a unit's population cost. */
    public static final String COST = "Cost/population";

    /** The path of a building's housing. */
    public static final String HOUSING = "Housing/capacity";

    /** The civilisation's base housing, before any building: none until civilisations (CIV-01). */
    public static final int BASE = 0;

    private int max;
    private final int[] used;
    private final int[] housing;

    public Population(int factions, int max) {
        this.max = max;
        this.used = new int[factions];
        this.housing = new int[factions];
    }

    public int used(int faction) {
        return used[faction];
    }

    public int housing(int faction) {
        return housing[faction];
    }

    /** A rule change's new maximum (MATCH-01). */
    public void setMax(int max) {
        this.max = max;
    }

    /** The most population the faction may use now. */
    public int cap(int faction) {
        return Math.min(housing[faction] + BASE, max);
    }

    /** Whether {@code amount} more fits under the faction's cap. */
    public boolean fits(int faction, int amount) {
        return used[faction] + amount <= cap(faction);
    }

    public void add(int faction, int amount) {
        used[faction] += amount;
    }

    public void remove(int faction, int amount) {
        used[faction] -= amount;
    }

    public void house(int faction, int amount) {
        housing[faction] += amount;
    }

    public void unhouse(int faction, int amount) {
        housing[faction] -= amount;
    }

    /** Clears both counters: a restore counts them again. */
    public void reset() {
        java.util.Arrays.fill(used, 0);
        java.util.Arrays.fill(housing, 0);
    }

    public int factions() {
        return used.length;
    }
}
