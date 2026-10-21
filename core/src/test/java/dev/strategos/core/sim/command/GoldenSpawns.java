package dev.strategos.core.sim.command;

/**
 * Spawn positions pinned once from the spawn formula and matched bit for bit by an independent implementation of it; a
 * change is a determinism break, never a value to update.
 */
final class GoldenSpawns {
    private GoldenSpawns() {}

    /** Seed 7, 4 units around (16, 16), spread 3, at tick 1, issued by ADMIN with sequence 1. */
    static final double[][] SEED_7 = {
        {13.944066265926805, 17.536466684650744},
        {15.951234105966053, 13.735593985468508},
        {13.403679756981635, 15.437817329954028},
        {16.1694059280871, 15.753565609984722},
    };
}
