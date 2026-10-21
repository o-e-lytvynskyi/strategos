package dev.strategos.gametest;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestReporter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the GameTest run as JUnit XML, which the Gradle guards read. A flaky test reports only its final outcome
 * (ReportGameListener), so its attempts come from the generator's count.
 */
public final class JUnitXmlReporter implements TestReporter {
    private static final Logger LOG = LoggerFactory.getLogger(JUnitXmlReporter.class);

    private final Path report;
    private final long startNanos = System.nanoTime();
    private final String timestamp = // UTC, the same on every machine
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                    LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    private final List<Result> results = new ArrayList<>(); // guarded by this

    /** The run's one reporter, for {@link #recordFailure}; gametest code, so a static holder is fine. */
    private static volatile @Nullable JUnitXmlReporter current;

    public JUnitXmlReporter(Path report) {
        this.report = report.toAbsolutePath();
        current = this;
    }

    /**
     * Adds a failed case that no GameTest reported, such as the batch-end check's {@code batchend.<batch>}: the server
     * exits 0 after it, so only the report carries the failure to the Gradle guard (QA-04.6).
     */
    public static void recordFailure(String suite, String name, String message) {
        JUnitXmlReporter reporter = current;
        if (reporter == null) {
            LOG.error("No GameTest report to record {}.{}: {}", suite, name, message);
            return;
        }
        synchronized (reporter) {
            reporter.results.add(new Result(suite, name, 0, new AssertionError(message), true));
        }
    }

    @Override
    public synchronized void onTestSuccess(GameTestInfo testInfo) {
        results.add(Result.of(testInfo, null));
    }

    @Override
    public synchronized void onTestFailed(GameTestInfo testInfo) {
        Throwable error = testInfo.getError();
        results.add(Result.of(testInfo, error != null ? error : new IllegalStateException("failed without an error")));
    }

    /** Writes the report: the time of the suite is the run's wall time from this reporter's creation (QA-04.3). */
    @Override
    public synchronized void finish() {
        double wallSeconds = (System.nanoTime() - startNanos) / 1e9;
        List<String> retried = StrategosGameTests.retried();
        retried.forEach(r -> LOG.info("GameTest retried: {}", r));
        long failures =
                results.stream().filter(r -> r.error() != null && r.required()).count();
        long skipped =
                results.stream().filter(r -> r.error() != null && !r.required()).count();

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites>\n");
        xml.append("  <testsuite name=\"strategos.gametest\" tests=\"")
                .append(results.size())
                .append("\" failures=\"")
                .append(failures)
                .append("\" skipped=\"")
                .append(skipped)
                .append("\" time=\"")
                .append(seconds(wallSeconds))
                .append("\" timestamp=\"")
                .append(timestamp)
                .append("\">\n");
        if (!retried.isEmpty()) {
            xml.append("    <properties>\n");
            retried.forEach(r -> xml.append("      <property name=\"retried\" value=\"")
                    .append(escape(r))
                    .append("\"/>\n"));
            xml.append("    </properties>\n");
        }
        for (Result r : results) {
            xml.append("    <testcase classname=\"")
                    .append(escape(r.suite()))
                    .append("\" name=\"")
                    .append(escape(r.testCase()))
                    .append("\" time=\"")
                    .append(seconds(r.seconds()))
                    .append('"');
            Throwable error = r.error();
            if (error == null) {
                xml.append("/>\n");
            } else if (r.required()) {
                xml.append(">\n      <failure type=\"")
                        .append(escape(error.getClass().getName()))
                        .append("\" message=\"")
                        .append(escape(String.valueOf(error.getMessage())))
                        .append("\">")
                        .append(escape(stackTrace(error)))
                        .append("</failure>\n    </testcase>\n");
            } else {
                xml.append(">\n      <skipped message=\"optional test failed: ")
                        .append(escape(String.valueOf(error.getMessage())))
                        .append("\"/>\n    </testcase>\n");
            }
        }
        xml.append("  </testsuite>\n</testsuites>\n");
        write(xml.toString());
    }

    private void write(String xml) {
        try {
            Path dir = report.getParent();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, report.getFileName().toString(), ".tmp");
            Files.writeString(temp, xml, StandardCharsets.UTF_8);
            Files.move(temp, report, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the GameTest report " + report, e);
        }
    }

    private static String seconds(double seconds) {
        return String.format(Locale.ROOT, "%.3f", seconds);
    }

    private static String stackTrace(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    /**
     * XML text and attribute escaping. Drops what XML 1.0 cannot hold: control characters other than tab and line
     * breaks, U+FFFE and U+FFFF, and unpaired surrogates (which would also fail the UTF-8 write and lose the report).
     */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (Character.isHighSurrogate(c)) {
                        if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                            out.append(c).append(text.charAt(++i));
                        }
                    } else if (Character.isLowSurrogate(c)) {
                        // an unpaired low surrogate: dropped
                    } else if ((c >= 0x20 && c != 0xFFFE && c != 0xFFFF) || c == '\t' || c == '\n' || c == '\r') {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private record Result(
            String suite,
            String testCase,
            double seconds,
            @Nullable Throwable error,
            boolean required) {
        static Result of(GameTestInfo info, @Nullable Throwable error) {
            String name = info.getTestName();
            int dot = name.indexOf('.');
            String suite = dot < 0 ? "gametest" : name.substring(0, dot);
            String testCase = dot < 0 ? name : name.substring(dot + 1);
            return new Result(suite, testCase, info.getRunTime() / 1000.0, error, info.isRequired());
        }
    }
}
