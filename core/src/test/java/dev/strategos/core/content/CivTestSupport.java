package dev.strategos.core.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.CivId;
import java.util.ArrayList;
import java.util.List;

/** Civilisations over the built-in templates and techs, as a client's copy of a set holds them. */
final class CivTestSupport {
    /** The smallest civilisation that is lint-clean: it has a house. */
    static final String MINIMAL = "{\"roles\":{\"house\":\"strategos:building/house\"}}";

    private CivTestSupport() {}

    static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    static RawCiv civ(String id, String json) {
        return new RawCiv(
                new CivId(id), "data/t/strategos/civs/" + id.substring(id.indexOf(':') + 1) + ".json", obj(json));
    }

    /** The built-in templates and techs with {@code civs}, compiled the way a client's copy is. */
    static TemplateCompiler.Result compile(RawCiv... civs) {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        return TemplateCompiler.fromFlattened(flats, techs, List.of(civs));
    }

    static List<TemplateProblem> problems(String json) {
        return compile(civ("t:probe", json)).problems();
    }
}
