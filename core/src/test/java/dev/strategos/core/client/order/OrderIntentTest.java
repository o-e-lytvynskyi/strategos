package dev.strategos.core.client.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-05.3–4: one right click makes exactly one Move of the selection; Shift queues it; no selection, no order. */
class OrderIntentTest {
    @Test
    void aClickMakesOneMoveAndShiftQueuesIt() {
        Move plain = OrderIntents.move(List.of(3, 1, 2), 10.5, 20.5, false);
        Move queued = OrderIntents.move(List.of(3, 1, 2), 10.5, 20.5, true);

        assertThat(plain).isNotNull();
        assertThat(plain.units().toArray()).containsExactly(1, 2, 3);
        assertThat(plain.target().x()).isEqualTo(10.5);
        assertThat(plain.mode()).isEqualTo(QueueMode.REPLACE);
        assertThat(queued).isNotNull();
        assertThat(queued.mode()).isEqualTo(QueueMode.QUEUE);
        assertThat(OrderIntents.move(List.of(), 1, 1, false)).isNull();
    }
}
