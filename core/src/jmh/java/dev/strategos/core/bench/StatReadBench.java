package dev.strategos.core.bench;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchBus;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.StatService;
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

/**
 * DATA-02.6: a cached read of a unit's walk speed (budget: 50 ns; a {@code @HotPath} kernel: no allocation), round 600
 * units of both builtin templates in two factions, under faction modifiers and with every tenth unit on an overlay of
 * its own.
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StatReadBench {
    private static final int UNITS = 600;

    private StatService stats;
    private int path;
    private int next;

    @Setup(Level.Trial)
    public void setUp() {
        TemplateSet set = TemplateSet.builtin();
        IdentityStore identity = new IdentityStore();
        identity.ensureCapacity(UNITS);
        for (int i = 0; i < UNITS; i++) {
            identity.set(i, i % set.all().size(), i % 2);
        }
        stats = new StatService(set, 2, identity, new MatchBus());
        stats.add(new FactionSlot(0), Modifier.mul(CorePaths.WALK_SPEED, 1.1, 1).withFilter("Infantry"));
        stats.add(new FactionSlot(1), Modifier.add(CorePaths.WALK_SPEED, 0.01, 2));
        for (int i = 0; i < UNITS; i += 10) {
            stats.addPersonal(Handle.of(i, 1), Modifier.mul(CorePaths.WALK_SPEED, 0.9, 3));
        }
        path = stats.path(CorePaths.WALK_SPEED);
        for (int i = 0; i < UNITS; i++) {
            stats.read(i, path); // every block and overlay evaluated once: what follows are cached reads
        }
    }

    @Benchmark
    public double cachedRead() {
        int i = next;
        next = i + 1 == UNITS ? 0 : i + 1;
        return stats.read(i, path);
    }
}
