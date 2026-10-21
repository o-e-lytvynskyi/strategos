package dev.strategos.mod.archfixture;

import dev.strategos.core.shared.HotPath;
import java.util.List;

/** B9's violating fixtures in the mod. */
public final class HotPathFixtures {
    private HotPathFixtures() {}

    @HotPath
    public static final class Streaming {
        long count(List<String> items) {
            return items.stream().filter(s -> !s.isEmpty()).count();
        }
    }

    @HotPath
    public static final class Unboxing {
        int unbox(Integer value) {
            return value;
        }
    }
}
