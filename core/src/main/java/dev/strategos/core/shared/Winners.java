package dev.strategos.core.shared;

import java.util.ArrayList;
import java.util.List;

/** Canonical ascending faction sets for terminal results; the empty set explicitly means a draw. */
@CoreApi
public final class Winners {
    public static final int MAX_MASK = (1 << (FactionSlot.MAX + 1)) - 1;

    private Winners() {}

    public static int mask(List<Integer> factions) {
        int previous = -1;
        int mask = 0;
        for (int faction : factions) {
            if (faction <= previous || faction > FactionSlot.MAX) {
                throw new IllegalArgumentException("winners must be ascending unique faction slots");
            }
            mask |= 1 << faction;
            previous = faction;
        }
        return mask;
    }

    public static List<Integer> fromMask(int mask) {
        if (mask < 0 || mask > MAX_MASK) throw new IllegalArgumentException("winner mask outside faction slots");
        var result = new ArrayList<Integer>(Integer.bitCount(mask));
        for (int f = 0; f <= FactionSlot.MAX; f++) {
            if ((mask & (1 << f)) != 0) result.add(f);
        }
        return List.copyOf(result);
    }

    public static List<Integer> legacy(int winner) {
        if (winner < -1 || winner > FactionSlot.MAX) throw new IllegalArgumentException("invalid winner faction");
        return winner == -1 ? List.of() : List.of(winner);
    }

    public static int first(int mask) {
        return mask == 0 ? -1 : Integer.numberOfTrailingZeros(mask);
    }
}
