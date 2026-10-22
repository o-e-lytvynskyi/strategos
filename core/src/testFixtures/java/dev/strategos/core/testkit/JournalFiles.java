package dev.strategos.core.testkit;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Journal exports as binary {@code .strj} files: GameTests and fault reports write them, {@code :core:replayJournals}
 * and the golden journal test read them back (ORD-01.4, CORE-11).
 */
public final class JournalFiles {
    public static final String EXTENSION = ".strj";

    private JournalFiles() {}

    /** Writes the export, creating the parent directories. */
    public static void write(Path file, JournalExport export) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, JournalFormat.encode(export));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the journal " + file, e);
        }
    }

    /** Reads an export; a file that does not decode throws, naming the file and the problem. */
    public static JournalExport read(Path file) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the journal " + file, e);
        }
        try {
            return JournalFormat.decode(bytes);
        } catch (WireException e) {
            throw new IllegalArgumentException(file + ": " + e.getMessage(), e);
        }
    }
}
