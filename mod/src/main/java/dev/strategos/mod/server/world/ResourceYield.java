package dev.strategos.mod.server.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;

/**
 * A {@code strategos:resource_yield} data map entry (ECO-02, 04 §8.5): what a block is to the economy, as in
 * {@code {"kind": "log", "resource": "wood", "subtype": "tree", "amount": 20}}: a tree's log yields its amount when it is
 * felled, a forage plant its amount in all; leaves yield nothing and name no resource. Other mods' blocks become
 * resources by an entry, with no code.
 */
public record ResourceYield(String kind, String resource, String subtype, double amount) {
    public static final Codec<ResourceYield> CODEC = RecordCodecBuilder.<ResourceYield>create(i -> i.group(
                            Codec.STRING.fieldOf("kind").forGetter(ResourceYield::kind),
                            Codec.STRING.optionalFieldOf("resource", "").forGetter(ResourceYield::resource),
                            Codec.STRING.optionalFieldOf("subtype", "").forGetter(ResourceYield::subtype),
                            Codec.DOUBLE.optionalFieldOf("amount", 0.0).forGetter(ResourceYield::amount))
                    .apply(i, ResourceYield::new))
            .validate(ResourceYield::validate);

    /** The core's material for this entry. */
    public Material material() {
        return new Material(kindOf(kind), resource, subtype, Amount.of(amount));
    }

    private static MaterialKind kindOf(String key) {
        for (MaterialKind k : MaterialKind.values()) {
            if (k.key().equals(key)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown material kind '" + key + "'");
    }

    private static DataResult<ResourceYield> validate(ResourceYield y) {
        boolean known = false;
        for (MaterialKind k : MaterialKind.values()) {
            known |= k.key().equals(y.kind);
        }
        if (!known) {
            return DataResult.error(() -> "unknown material kind '" + y.kind + "'");
        }
        if (!(y.amount >= 0) || Double.isInfinite(y.amount)) {
            return DataResult.error(() -> "a yield of " + y.amount);
        }
        boolean yields = !y.kind.equals(MaterialKind.LEAVES.key());
        if (yields && (y.resource.isEmpty() || y.subtype.isEmpty())) {
            return DataResult.error(() -> "a " + y.kind + " entry names its resource and subtype");
        }
        return DataResult.success(y);
    }
}
