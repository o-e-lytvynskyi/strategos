package dev.strategos.gametest;

import dev.strategos.gametest.net.MockViewer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The one GameTest generator: every {@link StrategosTest} of the suites this run selects. */
public final class StrategosGameTests {
    private static final Logger LOG = LoggerFactory.getLogger(StrategosGameTests.class);
    // Gametest code: the static state below lives for one GameTest server JVM.
    private static final Map<String, AtomicInteger> ATTEMPTS = new ConcurrentHashMap<>();
    private static final Map<String, String> RETRY_REASONS = new ConcurrentHashMap<>();

    private static final String SLOW = "slow";

    private StrategosGameTests() {}

    // After each batch: its matches go, then nothing of it may be left (QA-04.6). Vanilla finds these by batch name.

    @AfterBatch(batch = "default")
    public static void afterDefault(ServerLevel level) {
        afterBatch(level, "default");
    }

    @AfterBatch(batch = SLOW)
    public static void afterSlow(ServerLevel level) {
        afterBatch(level, SLOW);
    }

    @AfterBatch(batch = "skeleton")
    public static void afterSkeleton(ServerLevel level) {
        afterBatch(level, "skeleton");
    }

    @AfterBatch(batch = "isolation")
    public static void afterIsolation(ServerLevel level) {
        afterBatch(level, "isolation");
    }

    @AfterBatch(batch = "perf_walk")
    public static void afterPerfWalk(ServerLevel level) {
        afterBatch(level, "perf_walk");
    }

    @AfterBatch(batch = "perf_viewer")
    public static void afterPerfViewer(ServerLevel level) {
        afterBatch(level, "perf_viewer");
    }

    @AfterBatch(batch = "perf_snapshot")
    public static void afterPerfSnapshot(ServerLevel level) {
        afterBatch(level, "perf_snapshot");
    }

    @AfterBatch(batch = "chunks")
    public static void afterChunks(ServerLevel level) {
        afterBatch(level, "chunks");
    }

    @AfterBatch(batch = "content_errors")
    public static void afterContentErrors(ServerLevel level) {
        afterBatch(level, "content_errors");
    }

    @AfterBatch(batch = "content_used")
    public static void afterContentUsed(ServerLevel level) {
        afterBatch(level, "content_used");
    }

    @AfterBatch(batch = "content_speed")
    public static void afterContentSpeed(ServerLevel level) {
        afterBatch(level, "content_speed");
    }

    @AfterBatch(batch = "map_import")
    public static void afterMapImport(ServerLevel level) {
        afterBatch(level, "map_import");
    }

    @AfterBatch(batch = "map_twice")
    public static void afterMapTwice(ServerLevel level) {
        afterBatch(level, "map_twice");
    }

    @AfterBatch(batch = "map_roundtrip")
    public static void afterMapRoundtrip(ServerLevel level) {
        afterBatch(level, "map_roundtrip");
    }

    @AfterBatch(batch = "map_soak")
    public static void afterMapSoak(ServerLevel level) {
        afterBatch(level, "map_soak");
    }

    @AfterBatch(batch = "perf_soak")
    public static void afterPerfSoak(ServerLevel level) {
        afterBatch(level, "perf_soak");
    }

    @AfterBatch(batch = "perf_stress")
    public static void afterPerfStress(ServerLevel level) {
        afterBatch(level, "perf_stress");
    }

    @AfterBatch(batch = "writer")
    public static void afterWriter(ServerLevel level) {
        afterBatch(level, "writer");
    }

    @AfterBatch(batch = "perf_pages")
    public static void afterPerfPages(ServerLevel level) {
        afterBatch(level, "perf_pages");
    }

    @AfterBatch(batch = "perf_prewarm")
    public static void afterPerfPrewarm(ServerLevel level) {
        afterBatch(level, "perf_prewarm");
    }

    @AfterBatch(batch = "perf_writer")
    public static void afterPerfWriter(ServerLevel level) {
        afterBatch(level, "perf_writer");
    }

    @AfterBatch(batch = "selftest")
    public static void afterSelftest(ServerLevel level) {
        afterBatch(level, "selftest");
    }

    /** The batches of {@code runGameTestSlow}: {@code slow} and the map batches ({@code map_…}), which only it runs. */
    private static boolean slow(String batch) {
        return batch.equals(SLOW) || batch.startsWith("map_");
    }

    private static void afterBatch(ServerLevel level, String batch) {
        TickPacer.pace(false);
        MockViewer.closeAll();
        MatchFixture.disposeAll(level);
        BatchEndCheck.verify(level, batch);
    }

    @GameTestGenerator
    public static Collection<TestFunction> all() {
        boolean perf = Boolean.getBoolean("strategos.gametest.perf");
        boolean slow = Boolean.getBoolean("strategos.gametest.slow"); // runGameTestSlow keeps batch slow, alone
        String selftest = System.getProperty("strategos.selftest", "");
        String filter = System.getProperty("strategos.gametest.filter", "");
        List<Declared> declared = new ArrayList<>();
        for (Class<?> suite : Suites.forRun(perf, selftest)) {
            for (Method method : suite.getDeclaredMethods()) {
                StrategosTest test = method.getAnnotation(StrategosTest.class);
                if (test != null) {
                    declared.add(new Declared(name(suite, method), method, test));
                }
            }
        }
        reject(declared);
        List<Candidate> candidates = new ArrayList<>();
        for (Declared d : declared) {
            candidates.add(new Candidate(d.name(), d.test().batch(), () -> function(d)));
        }
        if (!perf && selftest.isEmpty()) { // the shared scenarios join the functional runs only
            for (ScenarioGameTests.ScenarioTest test : ScenarioGameTests.tests()) {
                candidates.add(new Candidate(test.name(), test.batch(), () -> ScenarioGameTests.function(test)));
            }
        }
        Pattern pattern = Pattern.compile(filter);
        List<TestFunction> functions = candidates.stream()
                .filter(c -> slow(c.batch()) == slow)
                .filter(c -> pattern.matcher(c.name()).find())
                .sorted(Comparator.comparing(Candidate::name))
                .map(c -> c.function().get())
                .toList();
        if (functions.isEmpty()) {
            LOG.error("filter '{}' matched no GameTest", filter);
        }
        return functions;
    }

    /** {@code "<test>: <n> attempts; <retryReason>"} for each test that ran more than once, by name (QA-04.4). */
    static List<String> retried() {
        return ATTEMPTS.entrySet().stream()
                .filter(e -> e.getValue().get() > 1)
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ": " + e.getValue().get() + " attempts; "
                        + RETRY_REASONS.getOrDefault(e.getKey(), ""))
                .toList();
    }

    /**
     * {@code <suite simple name in lower case>.<method name in snake_case>}, a digit run being a word of its own:
     * {@code rootRequiresPermission2} gives {@code root_requires_permission_2}.
     */
    static String name(Class<?> suite, Method method) {
        StringBuilder name = new StringBuilder(suite.getSimpleName().toLowerCase(Locale.ROOT)).append('.');
        char previous = '_';
        for (char c : method.getName().toCharArray()) {
            boolean wordStart = Character.isUpperCase(c) || (Character.isDigit(c) && !Character.isDigit(previous));
            if (wordStart && previous != '_') {
                name.append('_');
            }
            name.append(Character.toLowerCase(c));
            previous = c;
        }
        return name.toString();
    }

    /** Fails the run before any server starts: a retry without a reason, or a solo test that shares its batch. */
    private static void reject(List<Declared> declared) {
        List<String> problems = new ArrayList<>();
        for (Declared d : declared) {
            if (d.test().attempts() > 1 && d.test().retryReason().isBlank()) {
                problems.add(d.name() + ": " + d.test().attempts() + " attempts without a retryReason (QA-04.4)");
            }
        }
        Map<String, List<Declared>> byBatch = declared.stream()
                .collect(Collectors.groupingBy(d -> d.test().batch(), TreeMap::new, Collectors.toList()));
        byBatch.forEach((batch, tests) -> {
            boolean alone = batch.startsWith("perf_")
                    || tests.stream().anyMatch(d -> d.test().solo());
            if (alone && tests.size() > 1) {
                List<String> names = tests.stream().map(Declared::name).sorted().toList();
                problems.add("batch '" + batch + "' must hold one test but holds " + names + " (QA-04.5)");
            }
        });
        if (!problems.isEmpty()) {
            problems.forEach(p -> LOG.error("GameTest rejected: {}", p));
            throw new IllegalStateException("GameTests rejected: " + problems);
        }
    }

    private static TestFunction function(Declared d) {
        StrategosTest t = d.test();
        if (t.attempts() > 1) {
            RETRY_REASONS.put(d.name(), t.retryReason());
        }
        return new TestFunction(
                t.batch(),
                d.name(),
                "strategos:" + t.arena(),
                Rotation.NONE,
                t.timeoutTicks(),
                t.setupTicks(),
                t.required(),
                false,
                t.attempts(),
                t.successes(),
                false,
                helper -> invoke(d, helper));
    }

    /** Counts the attempt, then runs the test; the cause of a failure is rethrown, so assertions keep their type. */
    private static void invoke(Declared d, GameTestHelper helper) {
        ATTEMPTS.computeIfAbsent(d.name(), n -> new AtomicInteger()).incrementAndGet();
        try {
            d.method().invoke(null, helper);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("GameTest " + d.name() + " threw " + cause, cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("GameTest " + d.name() + " must be public and static", e);
        }
    }

    private record Declared(String name, Method method, StrategosTest test) {}

    /** A test this run may hold, before the slow rule and the filter. */
    private record Candidate(String name, String batch, Supplier<TestFunction> function) {}
}
