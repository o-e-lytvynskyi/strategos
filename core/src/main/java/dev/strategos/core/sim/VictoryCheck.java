package dev.strategos.core.sim;

import dev.strategos.core.shared.FactionSlot;

/**
 * The first victory rule (MATCH-01, M3 spec §1.6 row 18), each FINAL under {@link PlayRules.Victory#CIVIC_CENTRE}: a
 * faction that holds a finished civic centre (its civilisation's {@code DefeatRule}) is in play; one in play that holds none any more is defeated, as one that
 * resigned is; once someone was defeated and at most one in play stands, the match ends, that one the winner. A centre
 * still a foundation or a site counts as none. The same tick its last centre falls, so well within 20 ticks.
 */
final class VictoryCheck {
    private VictoryCheck() {}

    static void run(MatchState s) {
        Outcome o = s.outcome();
        if (s.config().rules().play().victory() != PlayRules.Victory.CIVIC_CENTRE || o.ended()) {
            return;
        }
        int factions = s.config().factions();
        boolean anyDefeated = false;
        int standing = 0;
        int last = Outcome.NONE;
        for (int f = 0; f < factions; f++) {
            boolean holds = s.civs().rules(f).defeat().holds(s, f);
            if (holds) {
                o.hold(f);
            }
            if (o.held(f) && !holds && !o.defeated(f)) {
                o.defeat(f);
                s.outputs().add(new FactionDefeated(new FactionSlot(f), FactionDefeated.CENTRE_LOST));
            }
            anyDefeated |= o.defeated(f);
            if (o.held(f) && !o.defeated(f)) {
                standing++;
                last = f;
            }
        }
        if (anyDefeated && standing <= 1) {
            int winner = standing == 1 ? last : Outcome.NONE;
            o.end(winner, s.tick());
            s.outputs().add(new MatchEnded(winner, s.tick()));
        }
    }
}
