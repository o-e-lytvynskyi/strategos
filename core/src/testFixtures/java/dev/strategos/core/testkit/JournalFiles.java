package dev.strategos.core.testkit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.journal.JournalCodec;
import dev.strategos.core.sim.journal.JournalExport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Journal exports as JSON files: GameTests write them, {@code :core:replayJournals} reads them back (ORD-01.4). */
public final class JournalFiles {
    private static final Gson GSON = new Gson();

    private JournalFiles() {}

    /** Writes the export, creating the parent directories. */
    public static void write(Path file, JournalExport export) {
        JsonElement json = JournalCodec.CODEC
                .encodeStart(JsonOps.INSTANCE, export)
                .getOrThrow(message -> new IllegalStateException("cannot encode a journal: " + message));
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the journal " + file, e);
        }
    }

    /** Reads an export; a file that does not decode throws, naming the file and the problem. */
    public static JournalExport read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the journal " + file, e);
        }
        return JournalCodec.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(text))
                .getOrThrow(message -> new IllegalArgumentException(file + ": " + message));
    }
}
