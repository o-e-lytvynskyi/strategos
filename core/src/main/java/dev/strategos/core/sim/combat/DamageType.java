package dev.strategos.core.sim.combat;

/** The three damage types (CMB-01); fire comes with torches (CMB-03). Each has its resistance path. */
public enum DamageType {
    HACK,
    PIERCE,
    CRUSH;

    /** Its index among the three, the order of a packet's amounts and of a unit's resistances. */
    public int index() {
        return switch (this) {
            case HACK -> 0;
            case PIERCE -> 1;
            case CRUSH -> 2;
        };
    }
}
