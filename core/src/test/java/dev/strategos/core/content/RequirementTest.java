package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.TechId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequirementTest {
    private static final TechId AXES = new TechId("t:axes");
    private static final TechId PLOUGH = new TechId("t:plough");

    private static final Requirement HAS_AXES = new Requirement.Tech(AXES);
    private static final Requirement HAS_PLOUGH = new Requirement.Tech(PLOUGH);
    private static final Requirement TOWN = new Requirement.Phase("town");
    private static final Requirement FIVE_HOUSES = new Requirement.Buildings("House", 5);

    /** Researched techs, reached phases and complete buildings by filter. */
    private record Fake(Set<TechId> techs, Set<String> phases, Map<String, Integer> buildings) implements FactionView {
        @Override
        public boolean researched(TechId tech) {
            return techs.contains(tech);
        }

        @Override
        public boolean reached(String phase) {
            return phases.contains(phase);
        }

        @Override
        public int completeBuildings(String classFilter) {
            return buildings.getOrDefault(classFilter, 0);
        }
    }

    private static Fake faction(Set<TechId> techs, Set<String> phases, int houses) {
        return new Fake(techs, phases, Map.of("House", houses));
    }

    private record Row(String name, Requirement req, Fake view, boolean met) {}

    @Test
    void theTable() {
        Fake none = faction(Set.of(), Set.of(), 0);
        Fake axes = faction(Set.of(AXES), Set.of(), 0);
        Fake both = faction(Set.of(AXES, PLOUGH), Set.of(), 0);
        Fake town = faction(Set.of(), Set.of("town"), 0);
        Fake four = faction(Set.of(), Set.of(), 4);
        Fake five = faction(Set.of(), Set.of(), 5);
        Fake six = faction(Set.of(), Set.of(), 6);
        List<Row> rows = List.of(
                new Row("tech missing", HAS_AXES, none, false),
                new Row("tech researched", HAS_AXES, axes, true),
                new Row("another tech does not count", HAS_PLOUGH, axes, false),
                new Row("phase not reached", TOWN, none, false),
                new Row("phase reached", TOWN, town, true),
                new Row("four of five buildings", FIVE_HOUSES, four, false),
                new Row("exactly five buildings", FIVE_HOUSES, five, true),
                new Row("six buildings", FIVE_HOUSES, six, true),
                new Row("all of two, one held", new Requirement.All(List.of(HAS_AXES, HAS_PLOUGH)), axes, false),
                new Row("all of two, both held", new Requirement.All(List.of(HAS_AXES, HAS_PLOUGH)), both, true),
                new Row("any of two, none held", new Requirement.Any(List.of(HAS_AXES, HAS_PLOUGH)), none, false),
                new Row("any of two, one held", new Requirement.Any(List.of(HAS_AXES, HAS_PLOUGH)), axes, true),
                new Row("all of none is met", new Requirement.All(List.of()), none, true),
                new Row("any of none is not met", new Requirement.Any(List.of()), both, false),
                new Row("a missing requires is met", Requirement.none(), none, true),
                new Row("nested: town or (axes and five houses), neither", nested(), none, false),
                new Row("nested: town", nested(), town, true),
                new Row("nested: axes without houses", nested(), axes, false),
                new Row(
                        "nested: axes and five houses",
                        nested(),
                        new Fake(Set.of(AXES), Set.of(), Map.of("House", 5)),
                        true),
                new Row(
                        "nested all holding an empty any",
                        new Requirement.All(List.of(HAS_AXES, new Requirement.Any(List.of()))),
                        axes,
                        false));
        for (Row row : rows) {
            assertThat(Requirements.evaluate(row.req, row.view)).as(row.name).isEqualTo(row.met);
        }
    }

    private static Requirement nested() {
        return new Requirement.Any(List.of(TOWN, new Requirement.All(List.of(HAS_AXES, FIVE_HOUSES))));
    }

    @Test
    void theChecklistListsLeavesDepthFirstWithHaveAndNeed() {
        Fake view = new Fake(Set.of(AXES), Set.of(), Map.of("House", 3));
        Requirement req =
                new Requirement.All(List.of(HAS_AXES, new Requirement.Any(List.of(TOWN, FIVE_HOUSES)), HAS_PLOUGH));

        assertThat(Requirements.checklist(req, view))
                .containsExactly(
                        new Requirements.Leaf(HAS_AXES, true, 1, 1),
                        new Requirements.Leaf(TOWN, false, 0, 1),
                        new Requirements.Leaf(FIVE_HOUSES, false, 3, 5),
                        new Requirements.Leaf(HAS_PLOUGH, false, 0, 1));
        assertThat(Requirements.checklist(Requirement.none(), view)).isEmpty();
        assertThat(Requirements.checklist(FIVE_HOUSES, new Fake(Set.of(), Set.of(), Map.of("House", 7))))
                .containsExactly(new Requirements.Leaf(FIVE_HOUSES, true, 7, 5));
    }
}
