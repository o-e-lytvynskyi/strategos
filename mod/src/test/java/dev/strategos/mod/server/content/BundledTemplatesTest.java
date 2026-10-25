package dev.strategos.mod.server.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.RawTech;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TechIdTable;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TechId;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.assertj.core.groups.Tuple;
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
                        Tuple.tuple("data/test/strategos/templates/unit/scout.json", "/UnitMotion/walkSpeed"),
                        Tuple.tuple("data/test/strategos/templates/unit/torn.json", ""));
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

    private record Loaded(
            List<RawTemplate> raws,
            List<RawTech> techs,
            Map<String, Blueprint> blueprints,
            TemplateCompiler.Result result,
            List<TemplateProblem> problems) {}

    @Test
    void theBundledTechsAreReadFromTheTechsDirectoryAndAnUnknownRequirementIsReported(@TempDir Path pack)
            throws IOException {
        Loaded bundled = load(resourceRoot());
        assertThat(bundled.result.set().techs())
                .extracting(t -> t.id().value())
                .containsExactly("strategos:gather_wood_1");

        Path tech = pack.resolve("data/test/strategos/techs/forge/anvils.json");
        Files.createDirectories(tech.getParent());
        Files.writeString(
                tech, "{ \"icon\": \"minecraft:anvil\", \"time\": 10, \"requires\": { \"tech\": \"test:nothing\" } }");

        Loaded loaded = load(pack);

        assertThat(loaded.result.set().tech(new TechId("test:forge/anvils"))).isNull();
        assertThat(loaded.problems)
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .contains(Tuple.tuple("data/test/strategos/techs/forge/anvils.json", "/requires/tech"));
    }

    /**
     * The session's tech table starts as the builtin set's (row 20): a refused first load changes nothing, and a reload
     * that adds a tech sorting before {@code gather_wood_1} gives it the next index, not index 0.
     */
    @Test
    void theTechTableStartsFromTheBuiltinSetAndKeepsIndicesAcrossARefusedLoad() {
        Loaded bundled = load(resourceRoot());
        ServerTemplates server = new ServerTemplates();
        assertThat(server.techTable().indexOf(new TechId("strategos:gather_wood_1")))
                .as("seeded from the starting set")
                .isZero();
        RawTech broken = new RawTech(
                new TechId("aaa:broken"),
                "aaa/broken.json",
                JsonParser.parseString(
                                "{\"icon\":\"minecraft:anvil\",\"time\":1,\"requires\":{\"tech\":\"aaa:nothing\"}}")
                        .getAsJsonObject());
        List<RawTech> withBroken = new ArrayList<>(bundled.techs());
        withBroken.add(broken);
        assertThat(server.load(bundled.raws(), withBroken, List.of(), REGISTRIES, List.of(), bundled.blueprints())
                        .accepted())
                .isFalse();
        RawTech fine = new RawTech(
                new TechId("aaa:early"),
                "aaa/early.json",
                JsonParser.parseString("{\"icon\":\"minecraft:anvil\",\"time\":1}")
                        .getAsJsonObject());
        List<RawTech> withFine = new ArrayList<>(bundled.techs());
        withFine.add(fine);
        assertThat(server.load(bundled.raws(), withFine, List.of(), REGISTRIES, List.of(), bundled.blueprints())
                        .accepted())
                .isTrue();
        assertThat(server.techTable().indexOf(new TechId("strategos:gather_wood_1")))
                .isZero();
        assertThat(server.techTable().indexOf(new TechId("aaa:early"))).isEqualTo(1);
        assertThat(server.set().tech(new TechId("strategos:gather_wood_1")).netIndex())
                .isZero();
    }

    private static String techFile(List<RawTech> techs, TechDef tech) {
        return techs.stream()
                .filter(raw -> raw.id().equals(tech.id()))
                .findFirst()
                .orElseThrow()
                .file();
    }

    @Test
    void aTechIconThatIsNoItemIsALintErrorAtTheTechsFile(@TempDir Path pack) throws IOException {
        Path tech = pack.resolve("data/test/strategos/techs/forge/anvils.json");
        Files.createDirectories(tech.getParent());
        Files.writeString(tech, "{ \"icon\": \"minecraft:no_such_item\", \"time\": 10 }");

        Loaded loaded = load(pack);

        assertThat(loaded.problems)
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .containsExactly(Tuple.tuple("data/test/strategos/techs/forge/anvils.json", "/icon"));
        assertThat(load(resourceRoot()).problems)
                .as("the bundled icon is an item")
                .isEmpty();
    }

    @Test
    void aRefusedReloadLeavesTheTechTableAndTheSetUnchanged() {
        Loaded bundled = load(resourceRoot());
        ServerTemplates server = new ServerTemplates();
        var tableBefore = server.techTable();
        var setBefore = server.set();
        RawTech badIcon = new RawTech(
                new TechId("aaa:early"),
                "aaa/early.json",
                JsonParser.parseString("{\"icon\":\"minecraft:no_such_item\",\"time\":1}")
                        .getAsJsonObject());
        List<RawTech> techs = new ArrayList<>(bundled.techs());
        techs.add(badIcon);

        ServerTemplates.Outcome outcome =
                server.load(bundled.raws(), techs, List.of(), REGISTRIES, List.of(), bundled.blueprints());

        assertThat(outcome.accepted()).isFalse();
        assertThat(outcome.problems())
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .contains(Tuple.tuple("aaa/early.json", "/icon"));
        assertThat(server.techTable()).isEqualTo(tableBefore);
        assertThat(server.set()).isSameAs(setBefore);
    }

    /** What the listener's prepare and apply make of a pack at {@code root}, linted as the server lints. */
    private static Loaded load(Path root) {
        PathPackResources pack = new PathPackResources(
                new PackLocationInfo("test", Component.literal("test"), PackSource.BUILT_IN, Optional.empty()), root);
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
            TemplateFiles.Read read = new TemplateReloadListener().prepare(resources, null);
            List<TemplateProblem> problems = new ArrayList<>(read.problems());
            List<RawTemplate> raws = TemplateFiles.raws(read, JsonOps.INSTANCE, problems);
            List<RawTech> techs = TemplateFiles.rawTechs(read, JsonOps.INSTANCE, problems);
            var blueprints = TemplateFiles.blueprints(read, problems);
            TemplateCompiler.Result result =
                    TemplateCompiler.compile(raws, techs, TemplateIdTable.EMPTY, TechIdTable.EMPTY, blueprints);
            problems.addAll(result.problems());
            problems.addAll(TemplateLint.check(result.set(), REGISTRIES, t -> t.id().value(), t -> techFile(techs, t)));
            return new Loaded(raws, techs, blueprints, result, problems);
        }
    }
}
