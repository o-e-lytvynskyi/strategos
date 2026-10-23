package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * One entry of a slice's material legend (ECO-02): the kind, the resource and subtype it yields ({@code ""} for leaves,
 * which yield nothing), and the amount per block in thousandths: per log for a tree, a bush's whole amount.
 */
@CoreApi
public record Material(MaterialKind kind, String resource, String subtype, long amount) {
    public Material {
        if (amount < 0) {
            throw new IllegalArgumentException("a negative amount " + amount);
        }
    }
}
