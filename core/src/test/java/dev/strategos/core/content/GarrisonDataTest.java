package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** BLD-16's data: a {@code GarrisonHolder} on the civic centre, validated, with the capacity and classes as paths. */
class GarrisonDataTest {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    /** The built-in templates with the civic centre's holder replaced by {@code holder} (JSON), or removed for null. */
    private static TemplateCompiler.Result compile(String holder) {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            JsonObject json = Objects.requireNonNull(TemplateSet.builtin().flattened(t.id()));
            if (t.id().equals(CENTRE)) {
                json.remove("GarrisonHolder");
                if (holder != null) {
                    json.add("GarrisonHolder", JsonParser.parseString(holder).getAsJsonObject());
                }
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(
                flats,
                TemplateSet.builtin().techs().stream()
                        .map(TemplateCompiler.TechFlat::of)
                        .toList());
    }

    @Test
    void theCivicCentreHoldsTwentyWorkersAndInfantry() {
        TemplateSet set = TemplateSet.builtin();
        int centre = Objects.requireNonNull(set.byId(CENTRE)).netIndex();
        assertThat(set.value(
                        centre,
                        set.valuePaths().byPath(CorePaths.GARRISON_CAPACITY).id()))
                .isEqualTo(20);
        assertThat(set.text(
                        centre,
                        set.tokenPaths().byPath(CorePaths.GARRISON_CLASSES).id()))
                .as("the tokens, sorted as the canonical form keeps them")
                .isEqualTo("Infantry Worker");
    }

    @Test
    void aHolderWithoutAnyRulesIsNotNeeded() {
        TemplateCompiler.Result result = compile(null);
        assertThat(result.problems()).isEmpty();
        int centre = Objects.requireNonNull(result.set().byId(CENTRE)).netIndex();
        assertThat(result.set()
                        .value(
                                centre,
                                result.set()
                                        .valuePaths()
                                        .byPath(CorePaths.GARRISON_CAPACITY)
                                        .id()))
                .as("the houses and towers still hold some, the centre none")
                .isNaN();
    }

    @Test
    void whatIsNotAWholeCapacityOfAtLeastOneIsAnErrorAtItsPointer() {
        for (String capacity : List.of("0", "2.5", "-3", "201", "\"many\"")) {
            TemplateCompiler.Result result = compile("{\"capacity\": " + capacity + ", \"classes\": \"Worker\"}");
            assertThat(result.ok()).as(capacity).isFalse();
            assertThat(result.problems())
                    .as(capacity)
                    .anyMatch(p -> p.pointer().equals("/" + CorePaths.GARRISON_CAPACITY));
        }
        assertThat(compile("{\"classes\": \"Worker\"}").problems())
                .anyMatch(p -> p.pointer().equals("/" + CorePaths.GARRISON_CAPACITY));
    }

    @Test
    void classesAreRequiredAndKeysKnown() {
        assertThat(compile("{\"capacity\": 5}").problems())
                .anyMatch(p -> p.pointer().equals("/" + CorePaths.GARRISON_CLASSES));
        assertThat(compile("{\"capacity\": 5, \"classes\": \" \"}").problems())
                .anyMatch(p -> p.pointer().equals("/" + CorePaths.GARRISON_CLASSES));
        assertThat(compile("{\"capacity\": 5, \"classes\": \"Worker\", \"bogus\": 1}")
                        .problems())
                .anyMatch(p -> p.pointer().equals("/GarrisonHolder/bogus"));
        assertThat(compile("{\"capacity\": 5, \"classes\": \"Worker Infantry\"}")
                        .problems())
                .isEmpty();
    }
}
