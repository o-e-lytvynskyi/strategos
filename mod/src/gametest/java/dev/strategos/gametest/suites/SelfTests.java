package dev.strategos.gametest.suites;

import dev.strategos.gametest.StrategosTest;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;

/** The retry semantics, run only with {@code -Pstrategos.selftest=retry} (QA-04.4). */
public final class SelfTests {
    private static final AtomicInteger TWO_OF_THREE_RUNS = new AtomicInteger();
    private static final AtomicInteger EXHAUSTED_RUNS = new AtomicInteger();

    private SelfTests() {}

    /** Fails once, then passes twice: reported passed, and listed as retried with its reason. */
    @StrategosTest(
            batch = "selftest",
            attempts = 3,
            successes = 2,
            retryReason = "self-test of the retry semantics (QA-04.4)")
    public static void twoOfThree(GameTestHelper helper) {
        if (TWO_OF_THREE_RUNS.incrementAndGet() == 1) {
            helper.fail("the first attempt fails by design");
        }
        helper.succeed();
    }

    /** Fails once, which leaves too few attempts for two successes: reported failed. */
    @StrategosTest(
            batch = "selftest",
            attempts = 2,
            successes = 2,
            retryReason = "self-test of exhausted attempts (QA-04.4)")
    public static void exhausted(GameTestHelper helper) {
        if (EXHAUSTED_RUNS.incrementAndGet() == 1) {
            helper.fail("the first attempt fails by design");
        }
        helper.succeed();
    }
}
