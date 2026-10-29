package dev.strategos.core.sim.persist;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import dev.strategos.core.shared.CoreApi;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A chain of {@link MigrationStep}s, each from one version to the next, applied before decoding. */
@CoreApi
public final class Migrations {
    private final List<MigrationStep> steps;

    /** Tests build their own chains. */
    public Migrations(List<MigrationStep> steps) {
        for (int i = 0; i < steps.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (steps.get(i).from() == steps.get(j).from()) {
                    throw new IllegalArgumentException(
                            "two migrations from version " + steps.get(i).from());
                }
            }
        }
        this.steps = List.copyOf(steps);
    }

    /** 1 to 2 adds terrain pages; 2 to 3 binds only the original numeric building/queue table. */
    public static Migrations production() {
        return new Migrations(List.of(new AddTerrainPages(), new StableTemplateReferences()));
    }

    /** Schema 1 to 2: M0's snapshots had no terrain, so their page list is empty. */
    private static final class AddTerrainPages implements MigrationStep {
        @Override
        public int from() {
            return 1;
        }

        @Override
        public <T> Dynamic<T> apply(Dynamic<T> input) {
            return input.set("pages", input.emptyList());
        }
    }

    /**
     * Numeric building references first shipped in M3. Only indices 0..4 kept the same ids:
     * the house initially occupied 5, which later became the field. Subsequent builtins may also
     * occupy an earlier custom index, so never infer them from the current builtin table.
     */
    private static final class StableTemplateReferences implements MigrationStep {
        @Override
        public int from() {
            return 2;
        }

        @Override
        public <T> Dynamic<T> apply(Dynamic<T> input) {
            if (input.get("buildings").asStream().findAny().isEmpty()) {
                return input;
            }
            List<String> original = List.of(
                    "strategos:unit/debug_walker",
                    "strategos:unit/infantry",
                    "strategos:unit/worker",
                    "strategos:building/barracks",
                    "strategos:building/civic_centre");
            return input.set(
                    "templateBindings",
                    input.createList(java.util.stream.IntStream.range(0, original.size())
                            .mapToObj(index -> input.emptyMap()
                                    .set("index", input.createInt(index))
                                    .set("id", input.createString(original.get(index))))));
        }
    }

    /**
     * Brings {@code input} to version {@code target}: reads {@code "v"}, applies every step in order and rewrites
     * {@code "v"} after each. A missing version, one above the target, or a gap in the chain is an error naming the
     * versions.
     */
    public <T> DataResult<Dynamic<T>> migrate(Dynamic<T> input, int target) {
        DataResult<Number> read = input.get("v").asNumber();
        if (read.error().isPresent()) {
            return DataResult.error(() -> "the snapshot has no schema version \"v\"");
        }
        int version = read.getOrThrow().intValue();
        if (version > target) {
            return DataResult.error(
                    () -> "the snapshot has schema version " + version + ", newer than this build's " + target);
        }
        Dynamic<T> current = input;
        for (int v = version; v < target; v++) {
            MigrationStep step = stepFrom(v);
            if (step == null) {
                int missing = v;
                return DataResult.error(() -> "no migration from schema version " + missing + " to " + (missing + 1));
            }
            current = step.apply(current);
            current = current.set(
                    "v", new Dynamic<>(current.getOps(), current.getOps().createInt(v + 1)));
        }
        return DataResult.success(current);
    }

    private @Nullable MigrationStep stepFrom(int version) {
        for (MigrationStep step : steps) {
            if (step.from() == version) {
                return step;
            }
        }
        return null;
    }
}
