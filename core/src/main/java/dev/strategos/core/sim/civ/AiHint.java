package dev.strategos.core.sim.civ;

import dev.strategos.core.shared.CoreApi;

/**
 * What the computer player must provide for its faction because of the rules the faction plays (ADR-0028, CIV-05): a
 * seam's rule declares what it implies in {@code hints()}, {@link CivRules} combines the declarations once at bind, and the
 * AI branches on the hint, never on a rule or civilisation id. A hint is a need: the default rules set every one, a
 * variant clears the one its mechanic removes.
 */
@CoreApi
public enum AiHint {
    /** Houses are needed to raise the population cap: the AI plans them and keeps a margin of free population. */
    NEEDS_HOUSING(1),
    /** Dropsites are needed to bank loads: the AI places storehouses and farmsteads near its nodes. */
    NEEDS_DROPSITES(2),
    /** Builders are needed to finish a foundation: the AI assigns them and waits on them. */
    NEEDS_BUILDERS(4);

    private final int bit;

    AiHint(int bit) {
        this.bit = bit;
    }

    /** This hint's bit in a mask. */
    public int mask() {
        return bit;
    }

    /** Whether {@code hints}, a mask, has this hint. */
    public boolean in(int hints) {
        return (hints & mask()) != 0;
    }

    /** The mask with every hint set: today's behaviour, and a faction outside the match. */
    public static final int ALL = 7;
}
