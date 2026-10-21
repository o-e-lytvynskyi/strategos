package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchBusTest {

    @Test
    void handlersRunInRegistrationOrderForTheExactClass() {
        MatchBus bus = new MatchBus();
        List<String> log = new ArrayList<>();
        bus.subscribe(OrderReceived.class, m -> log.add("first " + m.handle()));
        bus.subscribe(Arrived.class, m -> log.add("arrived " + m.handle()));
        bus.subscribe(OrderReceived.class, m -> log.add("second " + m.handle()));

        bus.publish(new OrderReceived(7));

        assertThat(log).containsExactly("first 7", "second 7");
        assertThat(bus.idle()).isTrue();
    }

    @Test
    void aMessagePublishedInsideAHandlerIsDeliveredAfterItFifo() {
        MatchBus bus = new MatchBus();
        List<String> log = new ArrayList<>();
        bus.subscribe(OrderReceived.class, m -> {
            log.add("order " + m.handle() + " begins");
            bus.publish(new Arrived(m.handle()));
            bus.publish(new StopOrdered(m.handle()));
            assertThat(bus.idle()).isFalse();
            log.add("order " + m.handle() + " ends");
        });
        bus.subscribe(OrderReceived.class, m -> log.add("order " + m.handle() + " second handler"));
        bus.subscribe(Arrived.class, m -> log.add("arrived " + m.handle()));
        bus.subscribe(StopOrdered.class, m -> log.add("stopped " + m.handle()));

        bus.publish(new OrderReceived(1));

        assertThat(log)
                .containsExactly("order 1 begins", "order 1 ends", "order 1 second handler", "arrived 1", "stopped 1");
        assertThat(bus.idle()).isTrue();
    }

    @Test
    void aTenThousandDeepChainCompletesWithoutGrowingTheStack() {
        MatchBus bus = new MatchBus();
        int[] last = {0};
        bus.subscribe(OrderReceived.class, m -> {
            last[0] = m.handle();
            if (m.handle() < 10_000) {
                bus.publish(new OrderReceived(m.handle() + 1));
            }
        });

        bus.publish(new OrderReceived(1));

        assertThat(last[0]).isEqualTo(10_000);
        assertThat(bus.idle()).isTrue();
    }

    @Test
    void twoRunsDispatchInTheSameOrder() {
        assertThat(dispatchLog()).isEqualTo(dispatchLog()).isNotEmpty();
    }

    private static List<String> dispatchLog() {
        MatchBus bus = new MatchBus();
        List<String> log = new ArrayList<>();
        bus.subscribe(OrderReceived.class, m -> {
            log.add("order " + m.handle());
            if (m.handle() % 3 == 0) {
                bus.publish(new Arrived(m.handle()));
            }
        });
        bus.subscribe(Arrived.class, m -> {
            log.add("arrived " + m.handle());
            bus.publish(new StopOrdered(m.handle()));
        });
        bus.subscribe(StopOrdered.class, m -> log.add("stopped " + m.handle()));
        for (int handle = 1; handle <= 20; handle++) {
            bus.publish(new OrderReceived(handle));
        }
        return log;
    }
}
