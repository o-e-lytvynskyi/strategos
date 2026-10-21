package dev.strategos.core.sim.archfixture;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Rule B4's violating fixtures, one per banned API (spec 7.1); {@code LinkedCollections} is allowed. */
public final class DeterminismFixtures {
    private DeterminismFixtures() {}

    public static final class CurrentTimeMillis {
        long now() {
            return System.currentTimeMillis();
        }
    }

    public static final class NanoTime {
        long now() {
            return System.nanoTime();
        }
    }

    public static final class InstantNow {
        Instant now() {
            return Instant.now();
        }
    }

    public static final class MathRandom {
        double next() {
            return Math.random();
        }
    }

    public static final class UtilRandom {
        int next() {
            return new Random(1).nextInt();
        }
    }

    public static final class ThreadLocalRandomUse {
        int next() {
            return ThreadLocalRandom.current().nextInt();
        }
    }

    public static final class SplittableRandomUse {
        int next() {
            return new SplittableRandom(1).nextInt();
        }
    }

    public static final class SecureRandomUse {
        int next() {
            return new SecureRandom().nextInt();
        }
    }

    public static final class MathSin {
        double sin(double x) {
            return Math.sin(x);
        }
    }

    public static final class HashMapUse {
        Map<String, String> map() {
            return new HashMap<>();
        }
    }

    public static final class HashSetUse {
        Set<String> set() {
            return new HashSet<>();
        }
    }

    public static final class IdentityHashMapUse {
        Map<String, String> map() {
            return new IdentityHashMap<>();
        }
    }

    public static final class SetOf {
        Set<String> set() {
            return Set.of("a");
        }
    }

    public static final class MapOf {
        Map<String, String> map() {
            return Map.of("a", "b");
        }
    }

    public static final class MapOfEntries {
        Map<String, String> map() {
            return Map.ofEntries(Map.entry("a", "b"));
        }
    }

    public static final class CollectorsToSet {
        Set<String> set() {
            return Stream.of("a").collect(Collectors.toSet());
        }
    }

    public static final class CollectorsToMap {
        Map<String, String> map() {
            return Stream.of("a").collect(Collectors.toMap(s -> s, s -> s));
        }
    }

    public static final class GroupingBy {
        Map<String, List<String>> map() {
            return Stream.of("a").collect(Collectors.groupingBy(s -> s));
        }
    }

    public static final class ObjectOpenHashMap {
        Object2IntOpenHashMap<String> map() {
            return new Object2IntOpenHashMap<>();
        }
    }

    public static final class LinkedCollections {
        Map<String, String> map() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("a", "b");
            return map;
        }

        Set<String> set() {
            Set<String> set = new LinkedHashSet<>();
            set.add("a");
            return set;
        }
    }

    public static final class ThreadUse {
        String name() {
            return Thread.currentThread().getName();
        }
    }

    public static final class ExecutorsUse {
        ExecutorService executor() {
            return Executors.newSingleThreadExecutor();
        }
    }

    public static final class ParallelStream {
        long count(List<String> items) {
            return items.parallelStream().count();
        }
    }
}
