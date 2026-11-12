package dev.strategos.core.bench;

import dev.strategos.core.sim.Arrived;
import dev.strategos.core.sim.MatchBus;
import dev.strategos.core.sim.OrderReceived;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** 1,000 publishes to two subscribers, every tenth publishing again from inside its handler (CORE-04.3). */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MatchBusBench {
    private static final int MESSAGES = 1000;

    private MatchBus bus;
    private OrderReceived[] messages;
    private Arrived[] nested;
    private long seen;

    @Setup(Level.Trial)
    public void setUp() {
        bus = new MatchBus();
        messages = new OrderReceived[MESSAGES];
        nested = new Arrived[MESSAGES];
        for (int i = 0; i < MESSAGES; i++) {
            messages[i] = new OrderReceived(i);
            nested[i] = new Arrived(i);
        }
        bus.subscribe(OrderReceived.class, m -> seen += m.handle());
        bus.subscribe(OrderReceived.class, m -> {
            if (m.handle() % 10 == 0) {
                bus.publish(nested[m.handle()]);
            }
        });
        bus.subscribe(Arrived.class, m -> seen++);
    }

    @Benchmark
    public long publish1000() {
        for (int i = 0; i < MESSAGES; i++) {
            bus.publish(messages[i]);
        }
        return seen;
    }
}
