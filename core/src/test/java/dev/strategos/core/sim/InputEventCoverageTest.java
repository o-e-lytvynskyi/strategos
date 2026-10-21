package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InputEventCoverageTest {

    @Test
    void ingestRoutesEveryInputEventTypeExactlyOnce() {
        List<Class<? extends InputEvent>> handled = IngestSystem.handledTypes();
        for (Class<?> type : InputEvent.class.getPermittedSubclasses()) {
            assertThat(handled)
                    .as("routes of %s", type.getSimpleName())
                    .filteredOn(type::equals)
                    .hasSize(1);
        }
        assertThat(handled).hasSameSizeAs(InputEvent.class.getPermittedSubclasses());
    }
}
