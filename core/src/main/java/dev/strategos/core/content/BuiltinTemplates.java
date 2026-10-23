package dev.strategos.core.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.List;

/**
 * The set the core falls back to before data packs load, and the one L1 tests use: the bundled templates of the mod's
 * {@code data/strategos/strategos/templates/}, here as text, compiled by the same compiler. DATA-03's bundled-template
 * test keeps the two equal.
 */
final class BuiltinTemplates {
    static final String UNIT_BASE = """
            {
              "abstract": true,
              "Identity": { "classes": "Unit" },
              "UnitMotion": { "walkSpeed": 0.1875, "turnRate": 45, "mobility": "infantry" },
              "Body": { "entityType": "strategos:unit_humanoid", "model": "humanoid", "width": 0.6, "height": 1.8, "radius": 0.35 }
            }
            """;
    static final String UNIT_INFANTRY = """
            {
              "parent": "strategos:template/unit_base",
              "abstract": true,
              "Identity": { "classes": "+Infantry +Organic" }
            }
            """;
    static final String DEBUG_WALKER = """
            {
              "parent": "strategos:template/unit_base",
              "Identity": { "name": "strategos.unit.debug_walker" },
              "UnitMotion": { "walkSpeed": 0.25 },
              "Body": { "skin": "strategos:units/debug_walker" }
            }
            """;
    static final String INFANTRY = """
            {
              "parent": "strategos:template/unit_infantry",
              "Identity": { "name": "strategos.unit.infantry", "classes": "+Spearman" },
              "Attack": { "Melee": { "hack": 3, "pierce": 3, "reach": 1.2, "prepare": 10, "repeat": 20 } },
              "Resistance": { "hack": 5, "pierce": 5, "crush": 2 },
              "Body": { "skin": "strategos:units/infantry" }
            }
            """;

    private BuiltinTemplates() {}

    static TemplateSet compile() {
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(
                raw("strategos:template/unit_base", UNIT_BASE),
                raw("strategos:template/unit_infantry", UNIT_INFANTRY),
                raw("strategos:unit/debug_walker", DEBUG_WALKER),
                raw("strategos:unit/infantry", INFANTRY)));
        if (!result.ok()) {
            throw new IllegalStateException("the builtin templates do not compile: " + result.problems());
        }
        return result.set();
    }

    private static RawTemplate raw(String id, String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        String path = id.substring(id.indexOf(':') + 1);
        return new RawTemplate(new TemplateId(id), "builtin/" + path + ".json", object);
    }
}
