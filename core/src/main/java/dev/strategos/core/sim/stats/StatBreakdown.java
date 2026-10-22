package dev.strategos.core.sim.stats;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/**
 * How a value comes about (ADR-0013), for tooltips and {@code /strategos stats}: the template's base, the modifiers that
 * take part in the order evaluation applies them (by stage: the winning replacement, then the products and the sums, in
 * source-id order), each with its source and stage, and the result. A template without the path has a NaN base and
 * result, and no modifier takes part.
 */
@CoreApi
public record StatBreakdown(String path, double base, List<Modifier> modifiers, double result) {
    public StatBreakdown {
        modifiers = List.copyOf(modifiers);
    }
}
