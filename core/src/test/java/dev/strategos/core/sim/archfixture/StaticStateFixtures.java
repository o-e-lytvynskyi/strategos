package dev.strategos.core.sim.archfixture;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Rule B8's violating fixtures; {@code Constants} and {@code Mode} are allowed. */
public final class StaticStateFixtures {
    private StaticStateFixtures() {}

    public static final class NonFinal {
        static int counter;
    }

    public static final class FinalArray {
        static final int[] TABLE = {1, 2};
    }

    public static final class FinalList {
        static final List<String> NAMES = new ArrayList<>();
    }

    public static final class FinalMap {
        static final Map<String, String> BY_NAME = new TreeMap<>();
    }

    public static final class FinalAtomic {
        static final AtomicInteger NEXT = new AtomicInteger();
    }

    public static final class FinalFastutil {
        static final IntArrayList HANDLES = new IntArrayList();
    }

    public static final class Constants {
        static final int MAX = 3;
        static final String NAME = "fixture";
    }

    /** An enum's synthetic {@code $VALUES} array is not state the code wrote. */
    public enum Mode {
        A,
        B
    }
}
