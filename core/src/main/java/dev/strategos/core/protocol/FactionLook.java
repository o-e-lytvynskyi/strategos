package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * A faction's equipment tiers (TECH-03): the weapon and the armour its units wear, each 0 (the starting gear) up to
 * {@code CorePaths.MAX_LOOK}. Sent in {@code match_state}'s {@code looks}, one entry per faction, as a byte.
 */
@CoreApi
public record FactionLook(int weapon, int armour) {
    public static final FactionLook BASE = new FactionLook(0, 0);

    public FactionLook {
        if (weapon < 0 || weapon > 15 || armour < 0 || armour > 15) {
            throw new IllegalArgumentException("a tier is 0 to 15: " + weapon + ", " + armour);
        }
    }

    /** The tiers in one byte: the weapon low, the armour high. */
    int pack() {
        return weapon | armour << 4;
    }

    static FactionLook unpack(int packed) {
        return new FactionLook(packed & 15, packed >> 4 & 15);
    }
}
