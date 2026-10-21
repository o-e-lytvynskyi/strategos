package dev.strategos.mod.server.tick;

import dev.strategos.mod.server.match.FaultPolicy;
import dev.strategos.mod.server.match.MatchRuntime;

/**
 * Runs match code so that a fault stops only its match: the match becomes FAULTED, the fault is logged once, and the
 * server keeps running; under the RETHROW policy the fault is rethrown after it was recorded. T10 adds the fault
 * report and the notice to the players in the match.
 */
public final class FaultBarrier {
    public void run(MatchRuntime rt, String where, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException | StackOverflowError | AssertionError t) {
            if (rt.faultMessage() == null) {
                rt.fault(t, where);
                rt.log().error(rt.tick(), "match fault in " + where + "; the match is now " + rt.lifecycle(), t);
            }
            if (rt.faultPolicy() == FaultPolicy.RETHROW) {
                throw new MatchFaultException(rt.id(), where, t);
            }
        }
    }
}
