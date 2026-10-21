package dev.strategos.core.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.LongStream;

/**
 * The committed seed sets, {@code seeds/<name>.txt} in the test kit's resources: one seed per line, {@code #} starts a
 * comment line. Tests draw seeds only from these sets (QA-17.5); M0's are m0-200 (seeded invariants, replays and the
 * soak), skeleton-20 (0..19), isolation-50 and canary (7).
 */
public final class Seeds {
    /** {@code -Dstrategos.seed=<n>} replaces every set with that seed, to rerun one failure alone (QA-02.2). */
    public static final String OVERRIDE = "strategos.seed";

    private Seeds() {}

    /** The set's seeds in file order, or only the {@link #OVERRIDE} seed when it is given. */
    public static LongStream set(String name) {
        String only = System.getProperty(OVERRIDE);
        if (only != null && !only.isBlank()) {
            return LongStream.of(Long.parseLong(only.trim()));
        }
        return LongStream.of(read(name));
    }

    /** The set's first seed; the override does not apply, so goldens drawn from it stay fixed. */
    public static long first(String name) {
        return read(name)[0];
    }

    private static long[] read(String name) {
        String resource = "/seeds/" + name + ".txt";
        try (InputStream in = Seeds.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("no seed set " + resource + " in the test kit's resources");
            }
            long[] seeds = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .mapToLong(Long::parseLong)
                    .toArray();
            if (seeds.length == 0) {
                throw new IllegalArgumentException("seed set " + resource + " is empty");
            }
            return seeds;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the seed set " + resource, e);
        }
    }
}
