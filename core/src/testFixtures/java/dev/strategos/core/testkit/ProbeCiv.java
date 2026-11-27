package dev.strategos.core.testkit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.PlaceholderBlueprints;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.RawTech;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TechIdTable;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.TemplateId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The probe civilisation (CIV-05, ADR-0028): {@code strategos:probe}, a test-only civilisation that uses every non-default
 * rule variant, its own start kit, roster and strategy script, so a rule seam that fails to carry a second civilisation
 * fails here. Its data files are the GameTest resources' ({@code mod/src/gametest/resources/probe/{civs,templates,ai}}):
 * the L1 tests read them from there and the GameTest {@code civtests.two_civilisations} loads the same files into the
 * server's template set for its match, outside the data pack so that no other test sees the probe; no file under {@code
 * src/main} names it ({@code ProbeFixtureTest}).
 *
 * <p>The templates the probe adds derive from shipped ones by {@code parent}, as a data pack's do: its infantry, a
 * spearman with the {@code Gatherer} class and gather rates (D-02), and a barracks that trains it (the AI reads a
 * building's own trainee list, and a bonus puts the barracks on the workers' build list).
 */
public final class ProbeCiv {
    public static final CivId ID = new CivId("strategos:probe");

    /** The probe's data files, laid out as a pack's {@code strategos/} folder; {@code core} tests read them by relative path. */
    private static final Path ROOT = Path.of("../mod/src/gametest/resources/probe");

    private static final List<String> TEMPLATES =
            List.of("strategos:unit/probe_infantry", "strategos:building/probe_barracks");

    private ProbeCiv() {}

    /** The built-in templates and the default civilisation, with the probe's templates and the probe civilisation. */
    public static TemplateSet set() {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        List<RawTemplate> parents = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
            JsonObject flat = builtin.flattened(t.id());
            flat.remove("Blueprint"); // joined at compile time
            parents.add(new RawTemplate(t.id(), "builtin/" + t.id().value(), flat));
        }
        List<RawTemplate> raws = new ArrayList<>(parents);
        for (String id : TEMPLATES) {
            raws.add(new RawTemplate(new TemplateId(id), id, json("templates/" + path(id) + ".json")));
        }
        List<RawTech> rawTechs = builtin.techs().stream()
                .map(t -> new RawTech(t.id(), "builtin/" + t.id().value(), t.toJson()))
                .toList();
        TemplateCompiler.Result added = TemplateCompiler.compile(
                raws, rawTechs, List.of(), TemplateIdTable.EMPTY, TechIdTable.EMPTY, PlaceholderBlueprints.byId());
        if (!added.ok()) {
            throw new AssertionError(added.problems().toString());
        }
        int next = builtin.indexCount();
        for (String id : TEMPLATES) {
            TemplateId template = new TemplateId(id);
            flats.add(new TemplateCompiler.Flat(
                    template,
                    next++,
                    java.util.Objects.requireNonNull(added.flattened().get(template))));
        }
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        List<RawCiv> civs = List.of(
                new RawCiv(
                        CivId.DEFAULT,
                        "default.json",
                        builtin.civ(CivId.DEFAULT).toJson()),
                new RawCiv(ID, "probe.json", json("civs/probe.json")));
        TemplateCompiler.Result r = TemplateCompiler.fromFlattened(flats, techs, civs);
        if (!r.problems().isEmpty()) {
            throw new AssertionError(r.problems().toString());
        }
        return r.set();
    }

    /** The probe's strategy script, as the data pack holds it: {@code ai/probe.json}, with its level table. */
    public static String script() {
        return text("ai/probe.json");
    }

    private static String path(String templateId) {
        return templateId.substring(templateId.indexOf(':') + 1);
    }

    private static JsonObject json(String file) {
        return JsonParser.parseString(text(file)).getAsJsonObject();
    }

    private static String text(String file) {
        try {
            return Files.readString(ROOT.resolve(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
