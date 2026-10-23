package dev.strategos.mod.server.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DATA-03.1 (spec §6.6): the mod's bundled templates, read by the reload listener from a pack of the mod's resources
 * with their blueprint files (BLD-01), compile and lint against the registries with no problem, into the very set the
 * core builds in; a broken file is
 * reported with its file and JSON pointer.
 */
class BundledTemplatesTest {
    private static final RegistryAccess REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test
    void theBundledTemplatesLoadWithoutAProblemAndEqualTheBuiltInSet() {
        Loaded loaded = load(resourceRoot());

        assertThat(loaded.problems).isEmpty();
        assertThat(loaded.result.set().version())
                .isEqualTo(TemplateSet.builtin().version());
        assertThat(loaded.raws)
                .extracting(r -> r.file())
                .contains("data/strategos/strategos/templates/unit/debug_walker.json");
    }

    @Test
    void aBrokenFileIsReportedWithItsFileAndPointer(@TempDir Path pack) throws IOException {
        Path unit = pack.resolve("data/test/strategos/templates/unit/scout.json");
        Files.createDirectories(unit.getParent());
        Files.writeString(unit, """
                {
                  "Identity": { "name": "scout" },
                  "UnitMotion": { "walkSpeed": "fast" },
                  "Body": { "entityType": "minecraft:no_such_mob", "width": 0.6, "height": 1.8 }
                }
                """);
        Path bad = pack.resolve("data/test/strategos/templates/unit/torn.json");
        Files.writeString(bad, "{ \"Identity\": ");

        Loaded loaded = load(pack);

        assertThat(loaded.problems)
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .contains(
                        org.assertj.core.groups.Tuple.tuple(
                                "data/test/strategos/templates/unit/scout.json", "/UnitMotion/walkSpeed"),
                        org.assertj.core.groups.Tuple.tuple("data/test/strategos/templates/unit/torn.json", ""));
    }

    /** The mod's resources as the test runtime has them: the root holding {@code data/}. */
    private static Path resourceRoot() {
        URL walker =
                BundledTemplatesTest.class.getResource("/data/strategos/strategos/templates/unit/debug_walker.json");
        assertThat(walker).as("the bundled debug walker on the class path").isNotNull();
        try {
            return Path.of(walker.toURI())
                    .getParent()
                    .getParent()
                    .getParent()
                    .getParent()
                    .getParent()
                    .getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Loaded(List<RawTemplate> raws, TemplateCompiler.Result result, List<TemplateProblem> problems) {}

    /** What the listener's prepare and apply make of a pack at {@code root}, linted as the server lints. */
    private static Loaded load(Path root) {
        PathPackResources pack = new PathPackResources(
                new PackLocationInfo("test", Component.literal("test"), PackSource.BUILT_IN, Optional.empty()), root);
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
            TemplateFiles.Read read = new TemplateReloadListener().prepare(resources, null);
            List<TemplateProblem> problems = new ArrayList<>(read.problems());
            List<RawTemplate> raws = TemplateFiles.raws(read, JsonOps.INSTANCE, problems);
            TemplateCompiler.Result result =
                    TemplateCompiler.compile(raws, TemplateIdTable.EMPTY, TemplateFiles.blueprints(read, problems));
            problems.addAll(result.problems());
            problems.addAll(TemplateLint.check(result.set(), REGISTRIES, t -> t.id().value()));
            return new Loaded(raws, result, problems);
        }
    }
}
