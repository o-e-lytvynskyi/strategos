package dev.strategos.core.sim.persist;

import com.mojang.serialization.Dynamic;
import dev.strategos.core.sim.Outcome;
import java.util.List;

/** Schema 5 to 6: a legacy individual result gains its explicit full winner mask, without changing its hash. */
final class CoalitionOutcomes implements MigrationStep {
    @Override
    public int from() {
        return 5;
    }

    @Override
    public <T> Dynamic<T> apply(Dynamic<T> input) {
        var saved = input.get("play").get("outcome");
        if (saved.result().isEmpty()) return input;
        List<Long> old = saved.asList(value -> value.asNumber().getOrThrow().longValue());
        if (old.isEmpty()) return input;
        if (old.size() != 5) throw new IllegalArgumentException("schema 5 outcome needs five fields");
        Outcome outcome = new Outcome();
        outcome.restore(old);
        return input.set(
                "play",
                input.get("play")
                        .orElseEmptyMap()
                        .set("outcome", input.createList(outcome.save().stream().map(input::createLong))));
    }
}
