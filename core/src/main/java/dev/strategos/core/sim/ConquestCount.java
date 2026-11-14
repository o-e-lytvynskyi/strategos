package dev.strategos.core.sim;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.List;

/**
 * Conquest (M6 §2.3), FINAL under {@link PlayRules.Victory#CONQUEST}: a faction in play that has no unit and no
 * building but fields and decorations is defeated; once someone was defeated and at most one in play stands, the match
 * ends, that one the winner.
 */
final class ConquestCount {
    private ConquestCount() {}

    static void run(MatchState s) {
        Outcome o = s.outcome();
        if (s.config().rules().play().victory() != PlayRules.Victory.CONQUEST || o.ended()) {
            return;
        }
        if (Math.floorMod(s.tick() - 13, 20) != 0) {
            return;
        }
        int factions = s.config().factions();
        int[] count = new int[factions];
        EntityStore entities = s.entities();
        for (int i = 0; i < entities.highWater(); i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            int faction = s.identity().faction(i);
            if (faction < factions) {
                count[faction]++;
            }
        }
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            int state = b.state(handle);
            if (state != BuildingStore.CONSTRUCTING && state != BuildingStore.COMPLETE) {
                continue;
            }
            List<String> classes = s.templates().byIndex(b.template(handle)).classes();
            if (classes.contains("Field") || classes.contains("Decoration")) {
                continue;
            }
            int faction = b.faction(handle);
            if (faction < factions) {
                count[faction]++;
            }
        }
        boolean anyDefeated = false;
        int standing = 0;
        int last = Outcome.NONE;
        for (int f = 0; f < factions; f++) {
            if (count[f] > 0) {
                o.hold(f);
            }
            if (o.held(f) && count[f] == 0 && !o.defeated(f)) {
                o.defeat(f);
                s.outputs().add(new FactionDefeated(new FactionSlot(f), FactionDefeated.CONQUERED));
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
