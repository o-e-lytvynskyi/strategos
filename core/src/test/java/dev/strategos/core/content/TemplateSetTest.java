package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateSetTest {

    @Test
    void builtinHoldsTheDebugWalkerTheInfantryAndTheWorker() {
        TemplateSet set = TemplateSet.builtin();
        UnitTemplate walker = set.byId(TemplateSet.DEBUG_WALKER);
        UnitTemplate infantry = set.byId(TemplateSet.INFANTRY);
        UnitTemplate worker = set.byId(TemplateSet.WORKER);

        assertThat(walker).isNotNull();
        assertThat(walker.netIndex()).as("ids sort debug_walker first").isZero();
        assertThat(walker.walkSpeed()).isEqualTo(0.25);
        assertThat(walker.bodyType()).isEqualTo("strategos:unit_humanoid");
        assertThat(walker.width()).isEqualTo(0.6f);
        assertThat(walker.height()).isEqualTo(1.8f);
        assertThat(walker.classes()).containsExactly("Unit");
        assertThat(infantry).isNotNull();
        assertThat(infantry.netIndex()).isEqualTo(1);
        assertThat(infantry.walkSpeed()).as("3/16, exact in binary").isEqualTo(0.1875);
        assertThat(infantry.classes()).containsExactly("Infantry", "Organic", "Spearman", "Unit");
        assertThat(set.byIndex(0)).isSameAs(walker);
        assertThat(worker).isNotNull();
        assertThat(worker.netIndex())
                .as("ids sort the worker after the infantry: M2's indices stay")
                .isEqualTo(2);
        assertThat(worker.classes()).containsExactly("Builder", "Gatherer", "Organic", "Unit", "Worker");
        assertThat(set.all()).hasSize(16);
        assertThat(set.all().subList(0, 3)).containsExactly(walker, infantry, worker);
        assertThat(set.all().subList(3, 7))
                .as("the buildings, appended after M2's pinned units, in id order")
                .extracting(t -> t.id().value())
                .containsExactly(
                        "strategos:building/barracks",
                        "strategos:building/civic_centre",
                        "strategos:building/field",
                        "strategos:building/house");
        assertThat(set.all().subList(3, 7)).allMatch(UnitTemplate::isBuilding);
        assertThat(set.blueprint(4)).isEqualTo(PlaceholderBlueprints.civicCentre());
        assertThat(set.blueprint(0)).isNull();
        assertThat(set.byId(new TemplateId("strategos:unit/nobody"))).isNull();
        assertThat(set.tokens().all())
                .contains("Builder", "Gatherer", "Infantry", "Organic", "Spearman", "Unit", "Worker")
                .contains("Barracks", "CivicCentre", "Field", "House", "Structure");
        assertThat(set.valuePaths().byPath("UnitMotion/walkSpeed")).isNotNull();
    }

    @Test
    void theVersionIsTheShaOfTheCanonicalJson() {
        TemplateSet set = TemplateSet.builtin();
        assertThat(set.version()).as("pins the canonical form").isEqualTo(BUILTIN_VERSION);
        assertThat(set.hash()).isEqualTo(Long.parseUnsignedLong(BUILTIN_VERSION.substring(0, 16), 16));
    }

    @Test
    void theBuiltinTemplatesWithoutTechsHaveTheVersionTheyHadBeforeTech01() {
        assertThat(BuiltinTemplates.compile(false).version())
                .as("no sample tech and no Researcher: the pin of 064ac8a^, no $techs key")
                .isEqualTo(BUILTIN_VERSION_BEFORE_TECH_01);
        assertThat(BuiltinTemplates.compile(false).techs()).isEmpty();
    }

    @Test
    void theBuiltinSetCarriesTheSampleTechAndTheStorehouseResearchesIt() {
        TemplateSet set = TemplateSet.builtin();
        TechDef tech = set.tech(new TechId("strategos:gather_wood_1"));

        assertThat(tech).isNotNull();
        assertThat(set.techs()).containsExactly(tech);
        assertThat(set.techByIndex(0)).isSameAs(tech);
        assertThat(tech.cost()).isEqualTo(java.util.Map.of("food", 100.0, "wood", 50.0));
        assertThat(tech.ticks()).isEqualTo(600);
        JsonObject storehouse = set.flattened(new TemplateId("strategos:building/storehouse"));
        assertThat(storehouse.getAsJsonObject("Researcher").get("techs").getAsString())
                .isEqualTo("strategos:gather_wood_1");
        double rate = set.value(
                set.byId(TemplateSet.WORKER).netIndex(),
                set.valuePaths().byPath("ResourceGatherer/rates/wood/tree").id());
        assertThat(rate).as("the path the tech's effect names exists").isEqualTo(0.6);
    }

    @Test
    void techsChangeTheShaOnlyWhenThereAreAny() {
        UnitTemplate walker = TemplateSet.builtin().byIndex(0);
        TemplateSet plain = new TemplateSet(List.of(walker));
        assertThat(TemplateCompiler.compile(List.of()).set().version())
                .as("no techs, no $techs key: the same version as a set from code with no templates")
                .isEqualTo(new TemplateSet(List.of()).version());
        assertThat(plain.techs()).isEmpty();
        TemplateSet one = oneTech("{\"icon\":\"i\",\"time\":5}");
        TemplateSet other = oneTech("{\"icon\":\"i\",\"time\":6}");
        assertThat(one.version()).isNotEqualTo(TemplateSet.builtin().version());
        assertThat(one.version()).isNotEqualTo(other.version());
        assertThat(oneTech("{\"icon\":\"i\",\"time\":5}").version()).isEqualTo(one.version());
        assertThat(TemplateSet.builtin().version())
                .as("the builtin set has the sample tech")
                .isNotEqualTo(BUILTIN_VERSION_BEFORE_TECH_01);
    }

    private static TemplateSet oneTech(String json) {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new java.util.ArrayList<>();
        for (UnitTemplate t : builtin.all()) {
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())));
        }
        return TemplateCompiler.fromFlattened(
                        flats,
                        List.of(new TemplateCompiler.TechFlat(
                                new TechId("t:axes"),
                                0,
                                JsonParser.parseString(json).getAsJsonObject())))
                .set();
    }

    @Test
    void everyFieldChangesTheHash() {
        long base = set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f))
                .hash();
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f))
                        .hash())
                .as("equal content, equal hash")
                .isEqualTo(base);
        assertThat(set(new UnitTemplate(
                                new TemplateId("strategos:unit/other"), 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_mount", 0.25, 0.6f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.3, 0.6f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.7f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.9f))
                        .hash())
                .isNotEqualTo(base);
    }

    @Test
    void rejectsAnInconsistentSet() {
        UnitTemplate walker = TemplateSet.builtin().byIndex(0);
        UnitTemplate infantry = TemplateSet.builtin().byIndex(1);
        assertThatThrownBy(() -> new TemplateSet(List.of(infantry, walker)))
                .as("net indices out of order")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> set(new UnitTemplate(walker.id(), -1, walker.bodyType(), 0.25, 0.6f, 1.8f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new TemplateSet(List.of(walker, new UnitTemplate(walker.id(), 1, "b", 0.25, 0.6f, 1.8f))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> set(new UnitTemplate(walker.id(), 0, walker.bodyType(), 0, 0.6f, 1.8f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemplateSet.builtin().byIndex(16)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final String BUILTIN_VERSION_BEFORE_TECH_01 =
            "b4dc5f372084f6e585991b46f0a5dd964000c22e094206ad5f375e00ac042c1c";

    // M4: the farmstead's reach (ECO-17); M6 T2.1: the storehouse's Researcher and the tech gather_wood_1 (TECH-01)
    private static final String BUILTIN_VERSION = "3f019e6660e60db502c566312c1d7850af933c7c1ba35f1bb2070a9c82d25c71";

    private static TemplateSet set(UnitTemplate template) {
        return new TemplateSet(List.of(template));
    }
}
