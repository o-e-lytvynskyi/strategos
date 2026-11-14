package dev.strategos.core.sim;

import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.ai.ComputerPlayer;
import dev.strategos.core.sim.ai.StrategyScript;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.econ.GatherRates;
import dev.strategos.core.sim.stats.Modifier;

/**
 * The open handicap of a computer player's level (D-19, AI-05): the faction modifier source {@link #SOURCE} that
 * multiplies its gathering rates and its units' training times by the script's {@code level.handicap}. Never saved:
 * derived from the player's script when it is set, when a match is restored and when the template set is swapped, as a
 * tech's effects are. A handicap only ever slows the AI; no source here adds to it (row 9).
 */
@CoreApi
public final class AiHandicap {
    /** The source id of the handicap: below every tech's ({@code -1 - netIndex}) and every handle's. */
    public static final int SOURCE = Integer.MIN_VALUE;

    private AiHandicap() {}

    /** Faction {@code faction}'s handicap from its computer player's script; none when it has no computer player. */
    public static void apply(MatchState s, int faction) {
        FactionSlot slot = new FactionSlot(faction);
        s.stats().remove(slot, SOURCE);
        ComputerPlayer player = s.aiSlots().get(faction);
        if (player == null) {
            return;
        }
        StrategyScript.Handicap h = player.handicap();
        if (h.gather() != 1) {
            for (ValuePath p : s.templates().valuePaths().all()) {
                if (p.path().startsWith(GatherRates.RATES)) {
                    s.stats().add(slot, Modifier.mul(p.path(), h.gather(), SOURCE));
                }
            }
        }
        if (h.train() != 1) {
            s.stats()
                    .add(
                            slot,
                            Modifier.mul(Construction.TIME, h.train(), SOURCE).withFilter("Unit"));
        }
    }

    /** Every faction's, after a restore or a swap of the template set. */
    public static void reapply(MatchState s) {
        for (int f = 0; f < s.config().factions(); f++) {
            apply(s, f);
        }
    }
}
