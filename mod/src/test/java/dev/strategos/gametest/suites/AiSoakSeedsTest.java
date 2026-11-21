package dev.strategos.gametest.suites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIndexOutOfBoundsException;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AiSoakSeedsTest {
    @Test
    void defaultsStillRunAllTwentySeeds() {
        var seeds = new AiSoakSeeds(1, 20);
        assertThat(IntStream.range(0, seeds.count()).mapToLong(seeds::seedAt).toArray())
                .containsExactly(IntStream.rangeClosed(1, 20).mapToLong(i -> i).toArray());
    }

    @Test
    void twentySingleSeedShardsCoverTheSameSetExactlyOnce() {
        assertThat(IntStream.rangeClosed(1, 20)
                        .mapToLong(first -> new AiSoakSeeds(first, 1).seedAt(0))
                        .toArray())
                .containsExactly(IntStream.range(0, 20)
                        .mapToLong(new AiSoakSeeds(1, 20)::seedAt)
                        .toArray());
    }

    @Test
    void invalidRangesCannotSilentlySkipOrInventSeeds() {
        for (int[] range : new int[][] {{0, 1}, {1, 0}, {20, 2}, {21, 1}, {Integer.MAX_VALUE, 2}, {1, -1}}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new AiSoakSeeds(range[0], range[1]));
        }
        assertThatIndexOutOfBoundsException().isThrownBy(() -> new AiSoakSeeds(7, 1).seedAt(1));
        assertThatIndexOutOfBoundsException().isThrownBy(() -> new AiSoakSeeds(7, 1).seedAt(-1));
    }
}
