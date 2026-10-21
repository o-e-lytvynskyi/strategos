package dev.strategos.gametest;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The one GameTest generator: every {@link StrategosTest} of the suites this run selects. */
public final class StrategosGameTests {
    private static final Logger LOG = LoggerFactory.getLogger(StrategosGameTests.class);
    // Gametest code: the static state below lives for one GameTest server JVM.
    private static final Map<String, AtomicInteger> ATTEMPTS = new ConcurrentHashMap<>();
    private static final Map<String, String> RETRY_REASONS = new ConcurrentHashMap<>();

    private StrategosGameTests() {}

    @GameTestGenerator
    public static Collection<TestFunction> all() {
        boolean perf = Boolean.getBoolean("strategos.gametest.perf");
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
        Pattern pattern = Pattern.compile(filter);
        List<TestFunction> functions = declared.stream()
                .filter(d -> pattern.matcher(d.name()).find())
                .sorted(Comparator.comparing(Declared::name))
                .map(StrategosGameTests::function)
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

    /** {@code <suite simple name in lower case>.<method name in snake_case>}. */
    static String name(Class<?> suite, Method method) {
        StringBuilder name = new StringBuilder(suite.getSimpleName().toLowerCase(Locale.ROOT)).append('.');
        for (char c : method.getName().toCharArray()) {
            if (Character.isUpperCase(c)) {
                name.append('_').append(Character.toLowerCase(c));
            } else {
                name.append(c);
            }
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
}
