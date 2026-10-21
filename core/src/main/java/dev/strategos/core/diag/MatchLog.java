package dev.strategos.core.diag;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.MatchId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The match's log: every line starts with {@code "[m<id> t<tick>] "}, so a match's story can be grepped (QA-10). */
@CoreApi
public final class MatchLog {
    private final Logger log;
    private final MatchId id;

    public MatchLog(Logger log, MatchId id) {
        this.log = log;
        this.id = id;
    }

    /** The match kernel's log, under the logger name the log scan watches; core.sim logs only through it. */
    public static MatchLog forSim(MatchId id) {
        return new MatchLog(LoggerFactory.getLogger("dev.strategos.core.sim.MatchCore"), id);
    }

    /** Starts a line of match code that concerns no match (a stray unit body, say), which the log scan accepts. */
    public static final String NO_MATCH = "[no match] ";

    /** {@code "[m7 t1234] "}. */
    public static String prefix(MatchId id, long tick) {
        return "[" + id + " t" + tick + "] ";
    }

    public void debug(long tick, String format, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(prefix(id, tick) + format, args);
        }
    }

    public void info(long tick, String format, Object... args) {
        if (log.isInfoEnabled()) {
            log.info(prefix(id, tick) + format, args);
        }
    }

    public void warn(long tick, String format, Object... args) {
        if (log.isWarnEnabled()) {
            log.warn(prefix(id, tick) + format, args);
        }
    }

    public void error(long tick, String message, Throwable t) {
        log.error(prefix(id, tick) + message, t);
    }
}
