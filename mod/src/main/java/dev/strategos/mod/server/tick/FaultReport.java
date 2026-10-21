package dev.strategos.mod.server.tick;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.journal.JournalCodec;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.mod.Strategos;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * A faulted match's record, written once to {@code <faultDir>/<match>-<tick>/}: {@code fault.txt} (where, tick,
 * lifecycle, versions, stack trace), {@code snapshot.json} and {@code journal.json}, from which the fault replays.
 * Each file is written on its own: one that fails becomes {@code <name>-error.txt}, and writing never throws.
 */
final class FaultReport {
    private FaultReport() {}

    /** The report's directory, or null when not even that could be made. */
    static @Nullable Path write(Path faultDir, MatchRuntime rt, Throwable fault, String where) {
        Path dir = faultDir.resolve(rt.id() + "-" + rt.tick());
        try {
            Files.createDirectories(dir);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        MatchCore core = rt.core();
        writeGuarded(dir, "fault.txt", () -> faultText(rt, fault, where));
        writeGuarded(dir, "snapshot.json", () -> {
            if (core == null) {
                throw new IllegalStateException("the match has no core");
            }
            return pretty(MatchSnapshotCodec.encode(JsonOps.INSTANCE, core.snapshot()));
        });
        writeGuarded(dir, "journal.json", () -> {
            if (core == null) {
                throw new IllegalStateException("the match has no core");
            }
            JournalExport export =
                    new JournalExport(core.journal(), core.queries().tick(), core.stateHash());
            return pretty(JournalCodec.CODEC
                    .encodeStart(JsonOps.INSTANCE, export)
                    .getOrThrow(message -> new IllegalStateException("cannot encode the journal: " + message)));
        });
        return dir;
    }

    private static String faultText(MatchRuntime rt, Throwable fault, String where) {
        StringWriter trace = new StringWriter();
        fault.printStackTrace(new PrintWriter(trace));
        return "match " + rt.id() + "\n"
                + "where " + where + "\n"
                + "tick " + rt.tick() + "\n"
                + "lifecycle " + rt.lifecycle() + "\n"
                + "java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "minecraft " + SharedConstants.getCurrentVersion().getName() + "\n"
                + "neoforge " + modVersion("neoforge") + "\n"
                + "strategos " + modVersion(Strategos.MOD_ID) + "\n"
                + "\n" + trace;
    }

    private static String modVersion(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static String pretty(JsonElement json) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n";
    }

    private static void writeGuarded(Path dir, String name, Supplier<String> content) {
        try {
            Files.writeString(dir.resolve(name), content.get(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException | StackOverflowError e) {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            String error = name.substring(0, name.lastIndexOf('.')) + "-error.txt";
            try {
                Files.writeString(dir.resolve(error), "cannot write " + name + ":\n" + trace, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException ignored) {
                // the report is best effort: the fault itself is logged by the barrier
            }
        }
    }
}
