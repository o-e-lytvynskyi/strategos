package dev.strategos.core.bench;

import dev.strategos.core.client.hud.MinimapImage;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * UI-20.4: one minimap tile's extraction, 16 × 16 columns of rolling terrain under half-explored fog (budget: 0.5 ms);
 * the client rebuilds a few tiles a tick.
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MinimapBench {
    private final MinimapImage.Columns columns = new MinimapImage.Columns() {
        @Override
        public int colour(int x, int z) {
            return 0x336699 + ((x * 31 + z * 17) & 0xFF);
        }

        @Override
        public int height(int x, int z) {
            return 64 + ((x / 3 + z / 5) & 7);
        }
    };

    private final MinimapImage.Fog fog = new MinimapImage.Fog() {
        @Override
        public boolean visible(int x, int z) {
            return (x & 32) == 0;
        }

        @Override
        public boolean explored(int x, int z) {
            return x < 200;
        }
    };

    @Benchmark
    public int[] tile() {
        return MinimapImage.tile(columns, fog, 7, 9, 384, 384);
    }
}
