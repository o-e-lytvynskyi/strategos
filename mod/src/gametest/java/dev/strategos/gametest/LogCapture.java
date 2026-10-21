package dev.strategos.gametest;

import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.MatchId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * The lines one match logs while a test watches: an appender on the root logger keeps every message that starts with
 * the match's {@code [m<id> t} prefix, so tests running side by side do not see each other's lines.
 */
public final class LogCapture implements AutoCloseable {
    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final LoggerContext context;
    private final AbstractAppender appender;

    private LogCapture(String prefix) {
        context = (LoggerContext) LogManager.getContext(false);
        appender = new AbstractAppender("strategos-capture-" + prefix, null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                if (message.startsWith(prefix)) {
                    lines.add(message);
                }
            }
        };
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, null, null);
        context.updateLoggers();
    }

    /** Starts capturing what match {@code id} logs. */
    public static LogCapture of(MatchId id) {
        String full = MatchLog.prefix(id, 0); // "[m<id> t0] "
        return new LogCapture(full.substring(0, full.indexOf(" t") + 2));
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    @Override
    public void close() {
        context.getConfiguration().getRootLogger().removeAppender(appender.getName());
        context.updateLoggers();
        appender.stop();
    }
}
