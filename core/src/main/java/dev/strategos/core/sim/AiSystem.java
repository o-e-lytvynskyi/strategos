package dev.strategos.core.sim;

import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.ai.AiBudget;
import dev.strategos.core.sim.ai.AiView;
import dev.strategos.core.sim.ai.ComputerPlayer;

/**
 * P3, AI: every computer player thinks every {@value #EVERY} ticks, its turn offset by its faction and the seed, or in the tick
 * after it was attacked or lost a building (AI-01, 04 §14). It reads the match's queries through an {@link AiView}
 * and its commands join the pending commands under its faction's slot, so the next tick's COMMANDS phase applies them
 * with the same validators as a human's; they are not journaled, since a replay thinks them again. A defeated
 * faction, or an ended match, thinks no more.
 */
final class AiSystem implements MatchSystem {
    static final int EVERY = 20;

    private final MatchQueries queries;

    AiSystem(MatchQueries queries) {
        this.queries = queries;
    }

    /**
     * Faction {@code f}'s turn within the {@value #EVERY} ticks: 7 apart, the order by the match's seed, so which
     * computer player thinks first is the seed's, not always the first slot's.
     */
    static long offset(MatchState s, int f) {
        int factions = s.config().factions();
        return 7L * Math.floorMod(f + s.config().seed(), factions);
    }

    @Override
    public Phase phase() {
        return Phase.AI;
    }

    @Override
    public void run(MatchState s) {
        AiSlots slots = s.aiSlots();
        if (!slots.any() || s.outcome().ended()) {
            return;
        }
        long tick = s.tick();
        for (int f = 0; f < s.config().factions(); f++) {
            ComputerPlayer player = slots.get(f);
            if (player == null) {
                continue;
            }
            boolean woken = slots.takeWake(f);
            if (s.outcome().defeated(f) || (!woken && (tick + offset(s, f)) % EVERY != 0)) {
                continue;
            }
            PlayerSlot issuer = new PlayerSlot(f);
            int[] k = {0};
            AiBudget budget = new AiBudget(AiBudget.CAP);
            player.think(
                    new AiView(queries, f, budget),
                    budget,
                    command -> s.pendingCommands()
                            .addLast(new CommandEnvelope(issuer, (int) ((tick << 7) | k[0]++), tick, command)));
        }
    }
}
