package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Winners;
import java.util.List;

/**
 * The match is over (MATCH-01): at most one faction stands. {@code winner} is that faction, -1 when none does; {@code
 * ticks} how long it lasted.
 */
@CoreApi
public record MatchEnded(List<Integer> winners, long ticks) implements MatchEvent {
    public MatchEnded {
        Winners.mask(winners);
        winners = List.copyOf(winners);
    }

    public MatchEnded(int winner, long ticks) {
        this(Winners.legacy(winner), ticks);
    }

    /** Compatibility view: first winning faction, or -1 for a draw; use winners for team results. */
    public int winner() {
        return winners.isEmpty() ? -1 : winners.getFirst();
    }
}
