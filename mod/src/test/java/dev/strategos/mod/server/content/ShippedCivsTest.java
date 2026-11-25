package dev.strategos.mod.server.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.CanonicalJson;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.RawTech;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TechIdTable;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
 * CIV-01 (M10 §2.1): the mod's shipped civilisation files, read by the real loader from a pack of the mod's resources,
 * compile and lint without a problem, and the default is the built-in definition, so the set keeps its version; a
 * broken file is reported with its file and JSON pointer.
 */
class ShippedCivsTest {
    private static final RegistryAccess REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CivId DEFAULT = new CivId("strategos:default");

    private record Loaded(List<RawCiv> civs, TemplateCompiler.Result result, List<TemplateProblem> problems) {}

    @Test
    void theShippedDefaultLoadsLintCleanAndEqualsTheBuiltInDefinition() {
        Loaded loaded = load(resourceRoot());

        assertThat(loaded.problems).isEmpty();
        assertThat(loaded.civs).extracting(RawCiv::file).containsExactly("data/strategos/strategos/civs/default.json");
        CivDef shipped = loaded.result.set().civ(DEFAULT);
        CivDef builtin = TemplateSet.builtin().civ(DEFAULT);
        assertThat(shipped).isNotNull().isEqualTo(builtin);
        assertThat(CanonicalJson.write(shipped.toJson())).isEqualTo(CanonicalJson.write(builtin.toJson()));
        assertThat(loaded.result.set().civs()).containsExactly(builtin);
    }

    @Test
    void theShippedFilesLeaveTheSetsVersionAsItWas() {
        TemplateSet set = load(resourceRoot()).result.set();

        assertThat(set.hasImpliedCivs())
                .as("the default is implied, not hashed")
                .isTrue();
        assertThat(set.version()).isEqualTo(TemplateSet.builtin().version());
    }

    @Test
    void theDefaultsLanguageKeysAreInTheLangFile() throws IOException {
        CivDef def = load(resourceRoot()).result.set().civ(DEFAULT);
        JsonObject lang;
        try (InputStream in = ShippedCivsTest.class.getResourceAsStream("/assets/strategos/lang/en_us.json");
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }

        assertThat(def).isNotNull();
        assertThat(lang.keySet()).contains(def.name()).containsAll(def.phases());
        assertThat(lang.get(def.name()).getAsString()).isEqualTo("Settlers");
    }

    @Test
    void aCivilisationFileOfADataPackIsReadAndAnotherOneChangesTheVersion(@TempDir Path pack) throws IOException {
        Path file = pack.resolve("data/test/strategos/civs/tribe.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ \"roles\": { \"house\": \"strategos:building/house\" } }");

        Loaded loaded = load(resourceRoot(), pack);

        assertThat(loaded.problems).isEmpty();
        assertThat(loaded.result.set().civs())
                .extracting(c -> c.id().value())
                .containsExactly("strategos:default", "test:tribe");
        assertThat(loaded.result.set().version())
                .isNotEqualTo(TemplateSet.builtin().version());
    }

    @Test
    void aBrokenCivilisationFileIsReportedWithItsFileAndPointer(@TempDir Path pack) throws IOException {
        Path bad = pack.resolve("data/test/strategos/civs/bad.json");
        Files.createDirectories(bad.getParent());
        Files.writeString(bad, "{ \"roles\": { \"house\": \"test:ghost\" }, \"colour\": 3 }");
        Path torn = pack.resolve("data/test/strategos/civs/torn.json");
        Files.writeString(torn, "{ \"roles\": ");

        Loaded loaded = load(pack);

        assertThat(loaded.problems)
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .contains(
                        Tuple.tuple("data/test/strategos/civs/bad.json", "/colour"),
                        Tuple.tuple("data/test/strategos/civs/torn.json", ""));
    }

    @Test
    void aCivilisationWithAnUnknownRuleIdOrBadParametersIsReportedWithItsFileAndPointer(@TempDir Path pack)
            throws IOException {
        Path file = pack.resolve("data/test/strategos/civs/odd.json");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                "{ \"roles\": { \"house\": \"strategos:building/house\" }, \"rules\": {"
                        + " \"phase\": { \"id\": \"by_magic\" },"
                        + " \"upkeep\": { \"id\": \"default\", \"params\": { \"x\": 1 } },"
                        + " \"morale\": { \"id\": \"default\" } } }");

        Pack read = read(resourceRoot(), pack);
        ServerTemplates server = new ServerTemplates();
        ServerTemplates.Outcome outcome =
                server.load(read.raws, read.techs, read.civs, read.problems, REGISTRIES, List.of(), read.blueprints);

        assertThat(outcome.accepted())
                .as("a civilisation with an unknown rule never loads")
                .isFalse();
        assertThat(server.set().civ(new CivId("test:odd"))).isNull();
        assertThat(outcome.problems())
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .containsExactlyInAnyOrder(
                        Tuple.tuple("data/test/strategos/civs/odd.json", "/rules/phase/id"),
                        Tuple.tuple("data/test/strategos/civs/odd.json", "/rules/upkeep/params"),
                        Tuple.tuple("data/test/strategos/civs/odd.json", "/rules/morale"));
    }

    private static Path resourceRoot() {
        URL walker = ShippedCivsTest.class.getResource("/data/strategos/strategos/civs/default.json");
        assertThat(walker)
                .as("the shipped default civilisation on the class path")
                .isNotNull();
        try {
            return Path.of(walker.toURI())
                    .getParent()
                    .getParent()
                    .getParent()
                    .getParent()
                    .getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Pack(
            List<RawTemplate> raws,
            List<RawTech> techs,
            List<RawCiv> civs,
            Map<String, Blueprint> blueprints,
            List<TemplateProblem> problems) {}

    private static Pack read(Path... roots) {
        List<PathPackResources> packs = new ArrayList<>();
        for (Path root : roots) {
            packs.add(new PathPackResources(
                    new PackLocationInfo("test", Component.literal("test"), PackSource.BUILT_IN, Optional.empty()),
                    root));
        }
        try (MultiPackResourceManager resources =
                new MultiPackResourceManager(PackType.SERVER_DATA, List.copyOf(packs))) {
            TemplateFiles.Read read = new TemplateReloadListener().prepare(resources, null);
            List<TemplateProblem> problems = new ArrayList<>(read.problems());
            List<RawTemplate> raws = TemplateFiles.raws(read, JsonOps.INSTANCE, problems);
            List<RawTech> techs = TemplateFiles.rawTechs(read, JsonOps.INSTANCE, problems);
            List<RawCiv> civs = TemplateFiles.rawCivs(read, JsonOps.INSTANCE, problems);
            return new Pack(raws, techs, civs, TemplateFiles.blueprints(read, problems), problems);
        }
    }

    private static Loaded load(Path... roots) {
        List<PathPackResources> packs = new ArrayList<>();
        for (Path root : roots) {
            packs.add(new PathPackResources(
                    new PackLocationInfo("test", Component.literal("test"), PackSource.BUILT_IN, Optional.empty()),
                    root));
        }
        try (MultiPackResourceManager resources =
                new MultiPackResourceManager(PackType.SERVER_DATA, List.copyOf(packs))) {
            TemplateFiles.Read read = new TemplateReloadListener().prepare(resources, null);
            List<TemplateProblem> problems = new ArrayList<>(read.problems());
            List<RawTemplate> raws = TemplateFiles.raws(read, JsonOps.INSTANCE, problems);
            List<RawTech> techs = TemplateFiles.rawTechs(read, JsonOps.INSTANCE, problems);
            List<RawCiv> civs = TemplateFiles.rawCivs(read, JsonOps.INSTANCE, problems);
            Map<String, Blueprint> blueprints = TemplateFiles.blueprints(read, problems);
            TemplateCompiler.Result result =
                    TemplateCompiler.compile(raws, techs, civs, TemplateIdTable.EMPTY, TechIdTable.EMPTY, blueprints);
            problems.addAll(result.problems());
            problems.addAll(TemplateLint.check(result.set(), REGISTRIES, t -> t.id().value(), t -> t.id().value()));
            return new Loaded(civs, result, problems);
        }
    }
}
