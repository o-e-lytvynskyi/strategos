package dev.strategos.core.content.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.PlaceholderBlueprints;
import dev.strategos.core.content.RawTech;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TechIdTable;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.testkit.BalanceData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * DATA-04: relationships derive values through the template chain, overrides need a marker and a reason and are
 * listed, every number has a provenance note, every gathered resource is spent twice, and the committed sheet is the
 * one the mod's data gives.
 */
class BalanceSheetTest {
    private static final Path REPO = Path.of(System.getProperty("strategos.repoRoot", ".."));

    private static RawTemplate raw(String id, String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return new RawTemplate(new TemplateId(id), id + ".json", o);
    }

    private static double value(TemplateCompiler.Result r, String id, String path) {
        TemplateSet set = r.set();
        int index = Objects.requireNonNull(set.byId(new TemplateId(id))).netIndex();
        return set.value(
                index, Objects.requireNonNull(set.valuePaths().byPath(path)).id());
    }

    /** A worker, and a soldier whose cost and pierce derive from it and from itself; {@code factor} the cost's. */
    private static List<RawTemplate> chain(String factor) {
        return List.of(
                raw("t:template/base", """
                        { "abstract": true, "Identity": { "name": "t" }, "UnitMotion": { "walkSpeed": 0.2, "turnRate": 45 },
                          "Body": { "entityType": "e:t", "width": 0.6, "height": 1.8 } }"""),
                raw("t:unit/worker", """
                        { "parent": "t:template/base", "Cost": { "resources": { "food": 50 } } }"""),
                raw("t:template/soldier", """
                        { "abstract": true, "parent": "t:template/base",
                          "Cost": { "resources": { "food": { "$derive": { "from": "t:unit/worker",
                            "path": "Cost/resources/food", "factor": %s, "round": 5 }, "note": "dearer than a worker" } } },
                          "Attack": { "Melee": { "hack": 3, "pierce": { "$derive": { "from": "self",
                            "path": "Attack/Melee/hack", "offset": 1 }, "note": "a spear thrusts harder" } } } }""".formatted(factor)),
                raw("t:unit/spearman", """
                        { "parent": "t:template/soldier" }"""),
                raw("t:unit/axeman", """
                        { "parent": "t:template/soldier", "Attack": { "Melee": { "hack": 5 } } }"""));
    }

    @Test
    void aRelationshipChangesEveryDerivedValue() {
        TemplateCompiler.Result r = TemplateCompiler.compile(chain("1.5"));

        assertThat(r.problems()).isEmpty();
        assertThat(value(r, "t:unit/spearman", "Cost/resources/food")).isEqualTo(75);
        assertThat(value(r, "t:unit/axeman", "Cost/resources/food")).isEqualTo(75);
        assertThat(value(r, "t:unit/spearman", "Attack/Melee/pierce")).isEqualTo(4); // its own hack, plus one
        assertThat(value(r, "t:unit/axeman", "Attack/Melee/pierce")).isEqualTo(6);

        TemplateCompiler.Result changed = TemplateCompiler.compile(chain("1.62"));

        // 81 rounds to the nearest 5, half up: 80 for both, the one relationship changing every value it derives
        assertThat(value(changed, "t:unit/spearman", "Cost/resources/food")).isEqualTo(80);
        assertThat(value(changed, "t:unit/axeman", "Cost/resources/food")).isEqualTo(80);
        assertThat(changed.derivations())
                .filteredOn(d -> d.path().equals("Cost/resources/food"))
                .extracting(d -> d.template().value())
                .containsExactly("t:unit/axeman", "t:unit/spearman");
    }

    @Test
    void derivationsChainAndACycleIsAProblem() {
        List<RawTemplate> raws = new ArrayList<>(chain("1.5"));
        raws.add(raw("t:unit/captain", """
                { "parent": "t:template/base", "Cost": { "resources": { "food": { "$derive": {
                  "from": "t:unit/spearman", "path": "Cost/resources/food", "factor": 2 }, "note": "twice a spearman" } } } }"""));
        assertThat(value(TemplateCompiler.compile(raws), "t:unit/captain", "Cost/resources/food"))
                .isEqualTo(150);

        TemplateCompiler.Result cycle = TemplateCompiler.compile(
                List.of(raw("t:template/base", """
                        { "abstract": true, "Identity": { "name": "t" }, "UnitMotion": { "walkSpeed": 0.2, "turnRate": 45 },
                          "Body": { "entityType": "e:t", "width": 0.6, "height": 1.8 } }"""), raw("t:unit/a", """
                        { "parent": "t:template/base", "Health": { "max": { "$derive": { "from": "t:unit/b",
                          "path": "Health/max" }, "note": "as b" } } }"""), raw("t:unit/b", """
                        { "parent": "t:template/base", "Health": { "max": { "$derive": { "from": "t:unit/a",
                          "path": "Health/max" }, "note": "as a" } } }""")));
        assertThat(cycle.problems())
                .extracting(TemplateProblem::message)
                .anyMatch(m -> m.startsWith("a derivation cycle: t:unit/a#Health/max -> t:unit/b#Health/max"));
    }

    @Test
    void anOverrideNeedsAMarkerAndAReasonAndIsListed() {
        List<RawTemplate> plain = new ArrayList<>(chain("1.5"));
        plain.add(raw("t:unit/slinger", """
                { "parent": "t:template/soldier", "Cost": { "resources": { "food": 40 } } }"""));
        assertThat(TemplateCompiler.compile(plain).problems())
                .extracting(TemplateProblem::message)
                .anyMatch(m -> m.startsWith("the value is derived"));

        List<RawTemplate> noReason = new ArrayList<>(chain("1.5"));
        noReason.add(raw("t:unit/slinger", """
                { "parent": "t:template/soldier", "Cost": { "resources": { "food": { "$override": 40 } } } }"""));
        assertThat(TemplateCompiler.compile(noReason).problems())
                .extracting(TemplateProblem::message)
                .anyMatch(m -> m.contains("$override (a number) and a reason"));

        List<RawTemplate> nothing = new ArrayList<>(chain("1.5"));
        nothing.add(raw("t:unit/slinger", """
                { "parent": "t:template/soldier", "Health": { "max": { "$override": 90, "reason": "frail" } } }"""));
        assertThat(TemplateCompiler.compile(nothing).problems())
                .extracting(TemplateProblem::message)
                .contains("an $override of a value nothing derives");

        List<RawTemplate> marked = new ArrayList<>(chain("1.5"));
        marked.add(raw("t:unit/slinger", """
                { "parent": "t:template/soldier",
                  "Cost": { "resources": { "food": { "$override": 40, "reason": "a sling is cheap" } } } }"""));
        TemplateCompiler.Result r = TemplateCompiler.compile(marked);
        assertThat(r.problems()).isEmpty();
        assertThat(value(r, "t:unit/slinger", "Cost/resources/food")).isEqualTo(40);
        BalanceSheet sheet = BalanceSheet.of(r, marked, Map.of(), List.of());
        assertThat(sheet.overrides()).singleElement().satisfies(d -> {
            assertThat(d.template().value()).isEqualTo("t:unit/slinger");
            assertThat(d.note()).isEqualTo("a sling is cheap");
            assertThat(d.derived()).isEqualByComparingTo("75");
        });
        assertThat(sheet.markdown()).contains("| t:unit/slinger | Cost/resources/food | 40 | 75 | a sling is cheap |");
    }

    @Test
    void everyValueHasProvenance() {
        List<RawTemplate> raws = chain("1.5");
        TemplateCompiler.Result r = TemplateCompiler.compile(raws);
        BalanceSheet bare = BalanceSheet.of(r, raws, Map.of(), List.of());
        assertThat(bare.problems())
                .contains("t:unit/axeman Attack/Melee/hack (set in t:unit/axeman): no provenance note")
                .contains("t:unit/worker UnitMotion/walkSpeed (set in t:template/base): no provenance note")
                .contains("t:unit/worker Health/max (the core's default): no provenance note")
                // a derived value's provenance is its relationship's note
                .noneMatch(p -> p.startsWith("t:unit/spearman Attack/Melee/pierce")
                        || p.startsWith("t:unit/axeman Attack/Melee/pierce"));

        BalanceSheet mod =
                BalanceData.read(REPO.resolve("mod/src/main/resources/data")).sheet();
        assertThat(mod.problems()).isEmpty();
        assertThat(mod.rows()).allSatisfy(row -> assertThat(row.provenance()).isNotBlank());
    }

    @Test
    void aTechsNumbersNeedProvenanceNotesAndAStaleTechKeyFails() {
        List<RawTemplate> raws = chain("1.5");
        RawTech tech = new RawTech(
                new TechId("t:axes"), "axes.json", JsonParser.parseString("""
                        { "icon": "i", "cost": { "food": 10 }, "time": 5,
                          "requires": { "all": [ { "buildings": { "filter": "House", "count": 2 } } ] },
                          "effects": [ { "path": "Cost/resources/food", "op": "mul", "value": 0.9 } ] }""").getAsJsonObject());
        TemplateCompiler.Result r = TemplateCompiler.compile(
                raws, List.of(tech), TemplateIdTable.EMPTY, TechIdTable.EMPTY, PlaceholderBlueprints.byId());
        assertThat(r.problems()).isEmpty();

        assertThat(BalanceSheet.of(r, raws, Map.of(), List.of()).problems())
                .contains(
                        "tech t:axes cost/food: no provenance note",
                        "tech t:axes time: no provenance note",
                        "tech t:axes requires/all/0/buildings/count: no provenance note",
                        "tech t:axes effects/0/value: no provenance note");

        Map<String, String> notes = new java.util.TreeMap<>(Map.of(
                "cost/food", "n",
                "time", "n",
                "requires/all/0/buildings/count", "n",
                "effects/0/value", "n"));
        assertThat(BalanceSheet.of(r, raws, Map.of("t:axes", notes), List.of()).problems())
                .noneMatch(p -> p.startsWith("tech t:axes"));
        notes.put("cost/wood", "stale");
        assertThat(BalanceSheet.of(r, raws, Map.of("t:axes", notes), List.of()).problems())
                .contains("provenance names t:axes cost/wood, which sets no number there");
    }

    @Test
    void everyGatherableResourceIsSpentTwice() {
        List<RawTemplate> raws = new ArrayList<>(chain("1.5"));
        raws.add(raw("t:unit/forester", """
                { "parent": "t:template/base", "ResourceGatherer": { "rates": { "wood": { "tree": 0.6 } } },
                  "Cost": { "resources": { "wood": 20 } } }"""));
        TemplateCompiler.Result r = TemplateCompiler.compile(raws);
        assertThat(BalanceSheet.of(r, raws, Map.of(), List.of()).problems())
                .contains(
                        "resource wood is gathered (t:unit/forester) but spent by 1 template; at least two must cost it");

        raws.add(raw("t:unit/carpenter", """
                { "parent": "t:template/base", "Cost": { "resources": { "wood": 30 } } }"""));
        TemplateCompiler.Result twice = TemplateCompiler.compile(raws);
        assertThat(BalanceSheet.of(twice, raws, Map.of(), List.of()).problems())
                .noneMatch(p -> p.startsWith("resource"));
    }

    @Test
    void theCommittedSheetIsTheOneTheModsDataGivesAndADiffShowsTheChangedNumber() throws IOException {
        BalanceData data = BalanceData.read(REPO.resolve("mod/src/main/resources/data"));
        BalanceSheet sheet = data.sheet();
        Path docs = REPO.resolve("docs/balance");
        assertThat(Files.readString(docs.resolve("sheet.md"), StandardCharsets.UTF_8))
                .as("docs/balance/sheet.md is stale: run ./gradlew :core:balanceSheet")
                .isEqualTo(sheet.markdown());
        assertThat(Files.readString(docs.resolve("sheet.csv"), StandardCharsets.UTF_8))
                .as("docs/balance/sheet.csv is stale: run ./gradlew :core:balanceSheet")
                .isEqualTo(sheet.csv());
        assertThat(data.compile().set().version())
                .isEqualTo(TemplateSet.builtin().version());

        List<RawTemplate> changed = new ArrayList<>();
        for (RawTemplate raw : data.raws()) {
            JsonObject json = raw.json();
            if (raw.id().value().equals("strategos:unit/infantry")) {
                json.getAsJsonObject("Attack").getAsJsonObject("Melee").addProperty("hack", 4);
            }
            changed.add(new RawTemplate(raw.id(), raw.file(), json));
        }
        String after = BalanceSheet.of(
                        TemplateCompiler.compile(changed), changed, data.provenance(), data.targets(), data.yields())
                .csv();
        List<String> before = sheet.csv().lines().toList();
        List<String> diff = after.lines().filter(l -> !before.contains(l)).toList();
        assertThat(diff)
                .containsExactly(
                        "strategos:unit/infantry,Attack/Melee/hack,4,\"set in strategos:unit/infantry\",\"With 100 hit"
                                + " points and resistance 5, one spearman kills another in 29 blows, about half a minute"
                                + " at a blow a second (M2, CMB-02)\"",
                        "strategos:unit/infantry,Attack/Melee/pierce,4,\"derived: self Attack/Melee/hack\",\"A spear"
                                + " thrusts as hard as it cuts\"");
    }
}
