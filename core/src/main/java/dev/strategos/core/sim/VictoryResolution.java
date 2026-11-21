package dev.strategos.core.sim;

import dev.strategos.core.shared.Winners;

/** Called only after every faction's defeat is evaluated, so a mutual elimination cannot emit a transient winner. */
final class VictoryResolution {
    private VictoryResolution() {}

    static void finish(MatchState state, boolean anyDefeated, int standing) {
        if (!anyDefeated || state.outcome().ended()) return;
        if (Integer.bitCount(standing) > 1) {
            if (!state.diplomacy().teamsLocked()) return;
            int first = Winners.first(standing);
            for (int f = first + 1; f < state.config().factions(); f++) {
                if ((standing & (1 << f)) != 0 && !state.diplomacy().alliedFactions(first, f)) return;
            }
        }
        var winners = Winners.fromMask(standing);
        state.outcome().end(winners, state.tick());
        state.outputs().add(new MatchEnded(winners, state.tick()));
    }
}
