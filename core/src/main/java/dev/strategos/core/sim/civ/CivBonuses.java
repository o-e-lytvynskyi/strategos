package dev.strategos.core.sim.civ;

import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.TechEffect;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.ModifierOp;

/**
 * A civilisation's bonuses (CIV-01): its {@link CivDef#bonuses()} as faction modifiers under {@link #SOURCE}, as a
 * technology's effects are. Never saved: derived at match start, when a match is restored, when the template set is
 * swapped and with {@code AiHandicap}'s, which it follows.
 */
@CoreApi
public final class CivBonuses {
    /** The source id of the bonuses: just above {@code AiHandicap.SOURCE}, below every tech's and every handle's. */
    public static final int SOURCE = Integer.MIN_VALUE + 1;

    private CivBonuses() {}

    /** Faction {@code faction}'s bonuses from its civilisation; drops only this source first. */
    public static void apply(MatchState s, int faction) {
        FactionSlot slot = new FactionSlot(faction);
        s.stats().remove(slot, SOURCE);
        CivDef def = s.civs().of(faction);
        if (def == null) {
            return;
        }
        for (TechEffect e : def.bonuses()) {
            s.stats()
                    .add(
                            slot,
                            new Modifier(
                                    e.path(),
                                    ModifierOp.valueOf(e.op().name()),
                                    e.value(),
                                    e.tokens() == null ? "" : e.tokens(),
                                    e.filter(),
                                    e.stage(),
                                    e.priority(),
                                    SOURCE));
        }
    }

    /** Every faction's, after a restore or a swap of the template set, and at match start. */
    public static void reapply(MatchState s) {
        for (int f = 0; f < s.config().factions(); f++) {
            apply(s, f);
        }
    }
}
