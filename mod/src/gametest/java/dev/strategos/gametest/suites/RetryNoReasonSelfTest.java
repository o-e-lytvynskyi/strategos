package dev.strategos.gametest.suites;

import dev.strategos.gametest.StrategosTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** A retry without a reason, run only with {@code -Pstrategos.selftest=retryNoReason}: the generator rejects it. */
public final class RetryNoReasonSelfTest {
    private RetryNoReasonSelfTest() {}

    @StrategosTest(batch = "selftest", attempts = 2)
    public static void noReason(GameTestHelper helper) {
        helper.succeed();
    }
}
