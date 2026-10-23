package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.FactionState;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The resource bar's view-model (UI-07): per resource of the match, in its order, the stock in whole units, the income
 * per minute in whole units and the workers gathering it; the population used and its cap, capped when no more can be
 * trained; and the idle workers. All from the last {@code faction_state}; empty before the first.
 */
public record ResourceBar(List<Entry> resources, int populationUsed, int populationCap, int idleWorkers) {
    public static final ResourceBar EMPTY = new ResourceBar(List.of(), 0, 0, 0);

    /** One resource: its id, stock, income per minute and gatherers. */
    public record Entry(String id, long stock, long perMinute, int workers) {}

    public ResourceBar {
        resources = List.copyOf(resources);
    }

    /** Whether the population is at its cap: training waits for housing. */
    public boolean capped() {
        return populationCap >= 0 && populationUsed >= populationCap;
    }

    /** From the faction's last state, the match's resources and the income seen so far. */
    public static ResourceBar of(@Nullable FactionState state, List<String> ids, Income income) {
        if (state == null) {
            return EMPTY;
        }
        List<Entry> out = new ArrayList<>(state.stock().size());
        for (int r = 0; r < state.stock().size(); r++) {
            out.add(new Entry(
                    r < ids.size() ? ids.get(r) : "?",
                    Math.floorDiv(state.stock().get(r), 1000),
                    Math.floorDiv(income.perMinute(r), 1000),
                    state.workers().get(r)));
        }
        return new ResourceBar(out, state.populationUsed(), state.populationCap(), state.idleWorkers());
    }
}
