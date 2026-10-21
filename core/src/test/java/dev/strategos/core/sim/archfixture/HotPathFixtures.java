package dev.strategos.core.sim.archfixture;

import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/** Rule B9's violating fixtures. */
public final class HotPathFixtures {
    private HotPathFixtures() {}

    @HotPath
    public static final class Streaming {
        int sum(int[] values) {
            return Arrays.stream(values).sum();
        }
    }

    @HotPath
    public static final class Boxing {
        Integer box(int value) {
            return value;
        }
    }
}
