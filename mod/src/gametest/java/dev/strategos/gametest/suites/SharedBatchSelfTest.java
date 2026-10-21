package dev.strategos.gametest.suites;

import dev.strategos.gametest.StrategosTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** A solo test sharing its batch, run only with {@code -Pstrategos.selftest=sharedBatch}: the generator rejects it. */
public final class SharedBatchSelfTest {
    private SharedBatchSelfTest() {}

    @StrategosTest(batch = "selftest", solo = true)
    public static void solo(GameTestHelper helper) {
        helper.succeed();
    }

    @StrategosTest(batch = "selftest")
    public static void plain(GameTestHelper helper) {
        helper.succeed();
    }
}
