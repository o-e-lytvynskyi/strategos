package dev.strategos.core.sim.persist;

import com.mojang.serialization.Dynamic;

/** Schema 7 to 8: all released page references meant published geometry, regardless of unknown extra fields. */
final class PendingTerrainReferences implements MigrationStep {
    @Override
    public int from() {
        return 7;
    }

    @Override
    public <T> Dynamic<T> apply(Dynamic<T> input) {
        var pages = input.get("pages")
                .result()
                .orElseThrow(() -> new IllegalArgumentException("schema 7 snapshot needs pages"));
        var references = pages.asStreamOpt().getOrThrow();
        return input.set(
                "pages",
                input.createList(references.map(ref -> ref.set(
                        "pending", new Dynamic<>(input.getOps(), input.getOps().createBoolean(false))))));
    }
}
