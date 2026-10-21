package dev.strategos.mod.server.persist;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.MatchId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CORE-12.3: every corrupted blob of the corpus, in {@code SavedData}'s on-disk form (gzip NBT {@code {DataVersion,
 * data}}), loads QUARANTINED with a reason and saves back unchanged; the deserializer never throws.
 * {@code -Dstrategos.fixtures.update=true} adds missing corpus files from {@code match_v1.nbt} by named mutations, plus
 * two files vanilla cannot read for guard 2; the corpus is append-only, so existing files are never rewritten.
 */
class CorruptCorpusTest {
    /** The canary fixture's match id. */
    private static final MatchId ID = new MatchId(1);

    private static Path repo(String path) {
        return Path.of(System.getProperty("strategos.repoRoot"), path);
    }

    private static Path corpus() {
        return repo("mod/src/gametest/resources/fixtures/corrupt");
    }

    @BeforeAll
    static void addMissingWhenAsked() throws IOException {
        if (!Boolean.getBoolean("strategos.fixtures.update")) {
            return;
        }
        Files.createDirectories(corpus());
        CompoundTag snapshot = NbtIo.readCompressed(
                repo("mod/src/test/resources/fixtures/match_v1.nbt"), NbtAccounter.unlimitedHeap());
        Map<String, Consumer<CompoundTag>> mutations = new LinkedHashMap<>();
        mutations.put("wrong_types", data -> data.getCompound("snapshot").put("tick", StringTag.valueOf("500")));
        mutations.put("missing_snapshot", data -> data.remove("snapshot"));
        mutations.put("version_99", data -> data.getCompound("snapshot").putInt("v", 99));
        mutations.put(
                "negative_size",
                data -> data.getCompound("snapshot")
                        .getCompound("config")
                        .getCompound("bounds")
                        .putInt("sizeX", -5));
        mutations.put("oversized_slots", data -> {
            int[] generations = new int[Handle.MAX_INDEX + 2];
            Arrays.fill(generations, 1);
            data.getCompound("snapshot").getCompound("slots").put("generations", new IntArrayTag(generations));
        });
        for (Map.Entry<String, Consumer<CompoundTag>> mutation : mutations.entrySet()) {
            Path file = corpus().resolve(mutation.getKey() + ".nbt");
            if (Files.exists(file)) {
                continue; // append-only
            }
            CompoundTag data = new CompoundTag();
            data.putInt("v", MatchData.VERSION);
            data.putLong("epoch", 1);
            data.put("snapshot", snapshot.copy());
            mutation.getValue().accept(data);
            CompoundTag onDisk = new CompoundTag();
            onDisk.put("data", data);
            NbtUtils.addCurrentDataVersion(onDisk);
            NbtIo.writeCompressed(onDisk, file);
        }
        Path truncated = corpus().resolve("truncated.dat");
        if (!Files.exists(truncated)) {
            byte[] whole = Files.readAllBytes(corpus().resolve("wrong_types.nbt"));
            Files.write(truncated, Arrays.copyOf(whole, whole.length / 2));
        }
        Path garbage = corpus().resolve("garbage.dat");
        if (!Files.exists(garbage)) {
            Files.writeString(garbage, "this is no NBT file, not even gzip\n", StandardCharsets.UTF_8);
        }
    }

    static Stream<Arguments> blobs() throws IOException {
        List<Path> files;
        try (Stream<Path> listed = Files.list(corpus())) {
            files = listed.filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .toList();
        }
        return files.stream().map(p -> Arguments.of(Named.of(p.getFileName().toString(), p)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blobs")
    void loadsQuarantinedAndSavesBackUnchanged(Path file) throws IOException {
        CompoundTag data =
                NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data");

        MatchData loaded = MatchData.load(ID, data);

        assertThat(loaded.state()).as("%s", file.getFileName()).isEqualTo(MatchData.State.QUARANTINED);
        assertThat(loaded.quarantineReason()).as("%s", file.getFileName()).isNotBlank();
        assertThat(loaded.save(new CompoundTag(), RegistryAccess.EMPTY)).isEqualTo(data);
    }
}
