package dev.strategos.mod.archfixture;

import java.util.ArrayList;
import java.util.List;

/** B8's violating fixtures in the mod. */
public final class StaticStateFixtures {
    private StaticStateFixtures() {}

    public static final class NonFinal {
        static Object current;
    }

    public static final class FinalList {
        static final List<String> SEEN = new ArrayList<>();
    }
}
