package dev.strategos.mod.server.persist;

import dev.strategos.core.port.JournalSink;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.mod.StrategosConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The journal setting as a core port (spec §5.10, CORE-11): {@code off} keeps one segment in memory, the least a fault
 * report needs; {@code memory} the last hour; {@code disk} the last hour too, and writes it whole to {@code
 * <world>/strategos/journals/match-<id>.strj} at every segment's end and when the match is disposed or saved. A write
 * replaces the file through a temporary one, so a reader never sees half a journal; a failed write is logged and the
 * match goes on.
 */
public final class DiskJournal implements JournalSink {
    private static final Logger LOG = LoggerFactory.getLogger(DiskJournal.class);

    /** One segment of 1,200 ticks. */
    private static final JournalSink ONE_SEGMENT = new JournalSink() {
        @Override
        public int keepTicks() {
            return 1_200;
        }
    };

    private final Path file;

    private DiskJournal(Path file) {
        this.file = file;
    }

    /** The sink of match {@code id} under {@code mode}. */
    public static JournalSink sink(StrategosConfig.Journal mode, MinecraftServer server, MatchId id) {
        return switch (mode) {
            case OFF -> ONE_SEGMENT;
            case MEMORY -> JournalSink.MEMORY;
            case DISK -> new DiskJournal(file(server, id));
        };
    }

    public static Path file(MinecraftServer server, MatchId id) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("strategos")
                .resolve("journals")
                .resolve("match-" + id.value() + ".strj");
    }

    /** Writes the match's kept journal now, as a segment's end does: at dispose and at save. */
    public static void writeNow(MinecraftServer server, MatchId id, MatchCore core) {
        new DiskJournal(file(server, id))
                .write(
                        core.queries().tick(),
                        JournalFormat.encode(
                                new JournalExport(core.journal(), core.queries().tick(), core.stateHash())));
    }

    @Override
    public boolean writes() {
        return true;
    }

    @Override
    public void write(long tick, byte[] journal) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temp, journal);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.warn("cannot write the journal {} at tick {}: {}", file, tick, e.toString());
        }
    }
}
