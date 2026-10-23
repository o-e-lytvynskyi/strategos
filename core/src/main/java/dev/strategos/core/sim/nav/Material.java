package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * One entry of a slice's material legend (ECO-02): the kind, the resource and subtype it yields ({@code ""} for leaves,
 * which yield nothing), the amount per block in thousandths (per log for a tree, a bush's whole amount), and for forage
 * what it regrows a second, in thousandths (ECO-05).
 */
@CoreApi
public record Material(MaterialKind kind, String resource, String subtype, long amount, long regrow) {
    public Material {
        if (amount < 0) {
            throw new IllegalArgumentException("a negative amount " + amount);
        }
        if (regrow < 0 || (regrow > 0 && kind != MaterialKind.FORAGE)) {
            throw new IllegalArgumentException("a regrowth of " + regrow + " for " + kind + ": only forage regrows");
        }
    }

    /** A material that does not regrow. */
    public Material(MaterialKind kind, String resource, String subtype, long amount) {
        this(kind, resource, subtype, amount, 0);
    }
}
