package dev.strategos.mod;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import dev.strategos.core.shared.MatchId;
import dev.strategos.mod.registry.ModTickets;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.persist.RegistryData;
import dev.strategos.mod.testing.EngineSurface;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.RegistryManager;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * QA-17.3, rule B12: the engine surface the code registers equals {@code engine-surface.json}, and every listed id
 * appears in the architecture's inventory (04 §2.5). The comparison is proved on three made-up surfaces.
 */
class EngineSurfaceInventoryTest {
    private static Path repo(String path) {
        return Path.of(System.getProperty("strategos.repoRoot"), path);
    }

    private static EngineSurface inventory() {
        return EngineSurface.read(repo("mod/src/test/resources/engine-surface.json"));
    }

    private static List<String> entityTypes() {
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(ResourceLocation::toString)
                .toList();
    }

    /** The saved-data names, a match's id written as {@code <id>}. */
    private static List<String> savedData() {
        return List.of(RegistryData.NAME, MatchData.name(new MatchId(7)).replace("7", "<id>"));
    }

    private static List<String> commandRoots() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        StrategosCommands.register(dispatcher);
        List<String> roots = new ArrayList<>();
        for (CommandNode<CommandSourceStack> node : dispatcher.getRoot().getChildren()) {
            roots.add(node.getName());
        }
        return roots;
    }

    /** Every registered data map type, on any registry (RegistryManager.java:74). */
    private static List<String> dataMaps() {
        List<String> ids = new ArrayList<>();
        RegistryManager.getDataMaps().values().forEach(types -> types.keySet().forEach(id -> ids.add(id.toString())));
        return ids;
    }

    /**
     * The mod's world generation files and its tags on world generation registries, as {@code <registry dir> <id>}:
     * {@code worldgen/biome strategos:arena}, {@code tags/worldgen/world_preset #minecraft:normal}.
     */
    private static List<String> worldgen() throws IOException {
        Path data = modFile("data");
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.walk(data)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                List<String> parts = new ArrayList<>();
                data.relativize(file).forEach(part -> parts.add(part.toString()));
                boolean tag = parts.size() > 1 && parts.get(1).equals("tags");
                int at = tag ? 2 : 1;
                if (parts.size() < at + 3 || !parts.get(at).equals("worldgen")) {
                    continue;
                }
                String path = String.join("/", parts.subList(at + 2, parts.size()));
                ids.add((tag ? "tags/" : "") + "worldgen/" + parts.get(at + 1) + " " + (tag ? "#" : "") + parts.get(0)
                        + ":" + path.substring(0, path.length() - ".json".length()));
            }
        }
        return ids;
    }

    private static List<String> ticketControllers() {
        return ModTickets.all().stream().map(c -> c.id().toString()).toList();
    }

    private static Path modFile(String... path) {
        return ModList.get().getModFileById(Strategos.MOD_ID).getFile().findResource(path);
    }

    private static String modsToml() throws IOException {
        return Files.readString(modFile("META-INF", "neoforge.mods.toml"), StandardCharsets.UTF_8);
    }

    private static @Nullable String accessTransformers() throws IOException {
        Path cfg = modFile("META-INF", "accesstransformer.cfg");
        return Files.exists(cfg) ? Files.readString(cfg, StandardCharsets.UTF_8) : null;
    }

    private static EngineSurface code(List<String> entityTypes, String modsToml, @Nullable String transformers)
            throws IOException {
        return code(entityTypes, modsToml, transformers, worldgen());
    }

    private static EngineSurface code(
            List<String> entityTypes, String modsToml, @Nullable String transformers, List<String> worldgen) {
        return EngineSurface.ofCode(
                entityTypes,
                savedData(),
                commandRoots(),
                dataMaps(),
                modsToml,
                transformers,
                worldgen,
                ticketControllers(),
                inventory());
    }

    @Test
    void theCodeRegistersExactlyTheInventory() throws IOException {
        assertThat(modsToml()).as("the mod's own metadata").contains("modId=\"strategos\"");
        assertThat(accessTransformers()).as("no access transformer file in M0").isNull();
        assertThat(commandRoots()).containsExactly("strategos");

        assertThat(EngineSurface.diff(inventory(), code(entityTypes(), modsToml(), accessTransformers())))
                .isEmpty();
    }

    @Test
    void everyListedIdIsInTheArchitecturesInventory() throws IOException {
        String doc = Files.readString(repo("docs/04-architecture.md"), StandardCharsets.UTF_8);
        int start = doc.indexOf("### 2.5 Engine-surface inventory");
        String section = doc.substring(start, doc.indexOf("\n## ", start));
        EngineSurface inventory = inventory();

        for (String kind : EngineSurface.KINDS) {
            for (String id : inventory.of(kind)) {
                String written = kind.equals("commands")
                        ? "/" + id
                        : kind.equals("worldgen") ? id.substring(id.indexOf(' ') + 1) : id;
                assertThat(section).as("%s %s in 04 §2.5", kind, id).contains(written);
            }
        }
    }

    @Test
    void anUnlistedEntityTypeIsReported() throws IOException {
        List<String> types = new ArrayList<>(entityTypes());
        types.add("strategos:unit_spy");

        assertThat(EngineSurface.diff(inventory(), code(types, modsToml(), accessTransformers())))
                .containsExactly("entityTypes: strategos:unit_spy is in the code but not in the inventory");
    }

    @Test
    void aMixinsEntryIsReported() throws IOException {
        String toml = modsToml() + "\n[[mixins]]\nconfig=\"strategos.mixins.json\"\n";

        assertThat(EngineSurface.diff(inventory(), code(entityTypes(), toml, accessTransformers())))
                .containsExactly("mixinConfigs: strategos.mixins.json is in the code but not in the inventory");
    }

    @Test
    void aWorldgenFileIsReported() throws IOException {
        List<String> worldgen = new ArrayList<>(worldgen());
        worldgen.add("worldgen/structure strategos:fort");

        assertThat(EngineSurface.diff(inventory(), code(entityTypes(), modsToml(), accessTransformers(), worldgen)))
                .containsExactly("worldgen: worldgen/structure strategos:fort is in the code but not in the inventory");
    }

    @Test
    void anAccessTransformerLineIsReported() throws IOException {
        String cfg = "# a comment\npublic net.minecraft.server.level.ChunkMap entityMap\n";

        assertThat(EngineSurface.diff(inventory(), code(entityTypes(), modsToml(), cfg)))
                .containsExactly("accessTransformers: public net.minecraft.server.level.ChunkMap entityMap"
                        + " is in the code but not in the inventory");
    }
}
