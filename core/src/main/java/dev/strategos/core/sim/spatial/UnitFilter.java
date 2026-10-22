package dev.strategos.core.sim.spatial;

/**
 * Which units a query reports (04 §10.1): those of a faction in {@code factions} (bit {@code f} for faction {@code
 * f}) that have every class in {@code classes} (bits of the set's class tokens; none: any class).
 */
public record UnitFilter(int factions, long classes) {
    /** Every unit. */
    public static final UnitFilter ANY = new UnitFilter(-1, 0);

    /** The units of one faction. */
    public static UnitFilter of(int faction) {
        return new UnitFilter(1 << faction, 0);
    }

    /** The units of every faction but one: its enemies, until alliances exist. */
    public static UnitFilter allBut(int faction) {
        return new UnitFilter(~(1 << faction), 0);
    }

    /** This filter, keeping only units that also have every class in {@code classes}. */
    public UnitFilter withClasses(long classes) {
        return new UnitFilter(factions, this.classes | classes);
    }

    public boolean passes(int faction, long unitClasses) {
        return (factions & (1 << faction)) != 0 && (unitClasses & classes) == classes;
    }
}
