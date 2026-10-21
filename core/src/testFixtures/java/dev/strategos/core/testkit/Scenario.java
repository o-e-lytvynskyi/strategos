package dev.strategos.core.testkit;

/**
 * A scripted match, run alike by L1 ({@link SimHarness#run}) and as a GameTest (M0 spec 7.4). Both runners stop at the
 * first tick where {@link #isDone} holds and report that tick's state hash, so the two runs must agree.
 */
public interface Scenario {
    /** Lower snake case, unique; GameTest names follow it. */
    String id();

    int sizeX();

    int sizeZ();

    long seed();

    int timeoutTicks();

    /** At match tick 0; what it submits applies at tick 1. */
    void setup(ScenarioContext ctx);

    /**
     * After every step. L3 may also call it at tick 0 and more than once per tick: act on state ("Move not sent yet and
     * the unit exists"), never on call counts.
     */
    default void onTick(ScenarioContext ctx) {}

    boolean isDone(ScenarioContext ctx);

    /** At the tick the run stopped; throws {@link ScenarioFailure}. */
    void verify(ScenarioContext ctx);
}
