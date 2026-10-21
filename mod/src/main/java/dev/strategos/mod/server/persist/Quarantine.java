package dev.strategos.mod.server.persist;

import dev.strategos.core.shared.MatchId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Loading saved blobs without ever losing one (ADR-0018's guard 2). Vanilla caches {@code null} for a file it cannot
 * read, and {@code computeIfAbsent} would then put an empty object under the name that overwrites the file at the next
 * save. So an unreadable file is copied to {@code <name>.dat.corrupt-<epoch>} and nothing is ever registered under its
 * name.
 */
public final class Quarantine {
    private Quarantine() {}

    /** The blob's file: {@code data/<name>.dat} in the level's storage folder. */
    public static Path dataFile(ServerLevel level, String name) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT);
        return DimensionType.getStorageFolder(level.dimension(), root)
                .resolve("data")
                .resolve(name + ".dat");
    }

    public static LoadResult loadMatch(ServerLevel level, MatchId id, long epoch) {
        String name = MatchData.name(id);
        Path file = dataFile(level, name);
        if (!Files.exists(file)) {
            return new Missing();
        }
        MatchData data = level.getDataStorage().get(MatchData.factory(id), name);
        if (data == null) {
            return new CorruptFile(copy(file, epoch), "vanilla could not read " + file.getFileName());
        }
        return new Loaded(data);
    }

    /** The registry: a fresh one when there is no file, a quarantined one kept out of the storage when it is unreadable. */
    public static RegistryData loadRegistry(ServerLevel overworld, long fallbackEpoch) {
        DimensionDataStorage storage = overworld.getDataStorage();
        Path file = dataFile(overworld, RegistryData.NAME);
        if (!Files.exists(file)) {
            return storage.computeIfAbsent(RegistryData.factory(), RegistryData.NAME);
        }
        RegistryData data = storage.get(RegistryData.factory(), RegistryData.NAME);
        if (data == null) {
            Path copy = copy(file, fallbackEpoch);
            return RegistryData.unreadable("vanilla could not read " + file.getFileName() + "; copied to " + copy);
        }
        return data;
    }

    private static Path copy(Path file, long epoch) {
        Path copy = file.resolveSibling(file.getFileName() + ".corrupt-" + epoch);
        try {
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new IllegalStateException("cannot keep a copy of the unreadable " + file, e);
        }
        return copy;
    }

    public sealed interface LoadResult permits Loaded, CorruptFile, Missing {}

    public record Loaded(MatchData data) implements LoadResult {}

    /** The file is kept as it was; {@code copy} has the same bytes. */
    public record CorruptFile(Path copy, String reason) implements LoadResult {}

    public record Missing() implements LoadResult {}
}
