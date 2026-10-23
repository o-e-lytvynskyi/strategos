package dev.strategos.core.sim.combat;

/**
 * The damage types (CMB-01): hack, pierce and crush, and fire, which only torches deal and only buildings take (CMB-03).
 * Each has its resistance path.
 */
public enum DamageType {
    HACK,
    PIERCE,
    CRUSH,
    FIRE;

    /** Its index, the order of a packet's amounts and of a target's resistances. */
    public int index() {
        return switch (this) {
            case HACK -> 0;
            case PIERCE -> 1;
            case CRUSH -> 2;
            case FIRE -> 3;
        };
    }
}
