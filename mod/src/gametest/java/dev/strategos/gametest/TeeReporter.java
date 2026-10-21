package dev.strategos.gametest;

import java.util.List;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestReporter;

/** Forwards every result, and {@link #finish()}, to each delegate in order. */
public final class TeeReporter implements TestReporter {
    private final List<TestReporter> delegates;

    public TeeReporter(TestReporter... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public void onTestFailed(GameTestInfo testInfo) {
        delegates.forEach(d -> d.onTestFailed(testInfo));
    }

    @Override
    public void onTestSuccess(GameTestInfo testInfo) {
        delegates.forEach(d -> d.onTestSuccess(testInfo));
    }

    @Override
    public void finish() {
        delegates.forEach(TestReporter::finish);
    }
}
