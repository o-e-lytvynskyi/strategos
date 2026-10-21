package dev.strategos.mod.server.tick;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.OutputEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CORE-02.3: the appliers handle every leaf of the sealed output hierarchy, so a new event cannot slip past. */
class OutputAppliersCoverageTest {

    @Test
    void handlesEveryLeafOutputType() {
        List<Class<?>> leaves = new ArrayList<>();
        collectLeaves(OutputEvent.class, leaves);

        assertThat(leaves).isNotEmpty();
        assertThat(leaves).containsExactlyInAnyOrderElementsOf(OutputAppliers.handledTypes());
    }

    private static void collectLeaves(Class<?> type, List<Class<?>> leaves) {
        Class<?>[] permitted = type.getPermittedSubclasses();
        if (permitted == null) {
            leaves.add(type);
            return;
        }
        for (Class<?> sub : permitted) {
            collectLeaves(sub, leaves);
        }
    }
}
