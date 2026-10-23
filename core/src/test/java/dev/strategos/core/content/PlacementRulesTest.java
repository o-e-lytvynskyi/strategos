package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.PlacementRules.Centre;
import dev.strategos.core.content.PlacementRules.Params;
import dev.strategos.core.content.PlacementRules.Rule;
import dev.strategos.core.content.PlacementRules.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * BLD-02.1 in L1: the placement rules, table-driven over a 128² world of flat ground at 10, with their numeric limits:
 * a floor spread of exactly 2 passes and a sixteenth more fails, civic centres 64 apart pass and less fail, a house
 * 48 from its centre's footprint passes and a block more fails; the first rule that fails is told, and per cell the
 * column rules.
 */
class PlacementRulesTest {
    static final Blueprint HOUSE = PlaceholderBlueprints.house(); // 5 x 5
    static final Blueprint CENTRE = PlaceholderBlueprints.civicCentre(); // 11 x 11
    static final Params HOUSE_PARAMS = new Params(false, 0, 48, true);
    static final Params CENTRE_PARAMS = new Params(true, 64, 0, true);

    /** A test world: its floors, taken cells, explored cells and civic centres. */
    record World(
            ToDoubleBiFunction<Integer, Integer> floors,
            BiPredicate<Integer, Integer> taken,
            BiPredicate<Integer, Integer> seen,
            List<Centre> centres)
            implements PlacementRules.World {
        @Override
        public int sizeX() {
            return 128;
        }

        @Override
        public int sizeZ() {
            return 128;
        }

        @Override
        public double floor(int x, int z) {
            return floors.applyAsDouble(x, z);
        }

        @Override
        public boolean occupied(int x, int z) {
            return taken.test(x, z);
        }

        @Override
        public boolean explored(int x, int z) {
            return seen.test(x, z);
        }

        @Override
        public List<Centre> civicCentres() {
            return centres;
        }
    }

    static World flat(Centre... centres) {
        return new World((x, z) -> 10, (x, z) -> false, (x, z) -> true, List.of(centres));
    }

    /** The faction's centre at (10, 10): its footprint spans 10..20. */
    static final Centre OWN = new Centre(Footprint.of(CENTRE, 10, 10, 0), 0);

    static Arguments row(
            String name, PlacementRules.World w, Blueprint b, int x, int z, Params p, @Nullable Rule rule) {
        return Arguments.of(name, w, b, x, z, p, rule);
    }

    static Stream<Arguments> rows() {
        List<Arguments> rows = new ArrayList<>();
        rows.add(row("a house beside its centre", flat(OWN), HOUSE, 30, 10, HOUSE_PARAMS, null));
        rows.add(row("past the space's edge", flat(OWN), HOUSE, 125, 10, HOUSE_PARAMS, Rule.INSIDE));
        rows.add(row("a negative corner", flat(OWN), HOUSE, -1, 10, HOUSE_PARAMS, Rule.INSIDE));
        rows.add(row(
                "on a footprint",
                new World((x, z) -> 10, (x, z) -> x == 32 && z == 12, (x, z) -> true, List.of(OWN)),
                HOUSE,
                30,
                10,
                HOUSE_PARAMS,
                Rule.FREE));
        rows.add(row(
                "over water",
                new World(
                        (x, z) -> x == 31 ? PlacementRules.NO_LAND : 10, (x, z) -> false, (x, z) -> true, List.of(OWN)),
                HOUSE,
                30,
                10,
                HOUSE_PARAMS,
                Rule.LAND));
        rows.add(row(
                "unexplored",
                new World((x, z) -> 10, (x, z) -> false, (x, z) -> x < 34, List.of(OWN)),
                HOUSE,
                30,
                10,
                HOUSE_PARAMS,
                Rule.EXPLORED));
        rows.add(row(
                "a spread of exactly 2",
                new World((x, z) -> x == 34 ? 12 : 10, (x, z) -> false, (x, z) -> true, List.of(OWN)),
                HOUSE,
                30,
                10,
                HOUSE_PARAMS,
                null));
        rows.add(row(
                "a sixteenth over 2",
                new World((x, z) -> x == 34 ? 12.0625 : 10, (x, z) -> false, (x, z) -> true, List.of(OWN)),
                HOUSE,
                30,
                10,
                HOUSE_PARAMS,
                Rule.FLAT));
        // the house's footprint 69..73 is 48 from the centre's edge at 21; one block on, 49
        rows.add(row("48 from its centre", flat(OWN), HOUSE, 69, 10, HOUSE_PARAMS, null));
        rows.add(row("49 from its centre", flat(OWN), HOUSE, 70, 10, HOUSE_PARAMS, Rule.REACH));
        rows.add(row(
                "near only an enemy's centre", flat(new Centre(OWN.at(), 1)), HOUSE, 30, 10, HOUSE_PARAMS, Rule.REACH));
        rows.add(row("with no reach rule", flat(), HOUSE, 30, 10, new Params(false, 0, 0, true), null));
        // centres' centres: (15.5, 15.5) and (x + 5.5, 15.5)
        rows.add(row("a centre 64 from another", flat(OWN), CENTRE, 74, 10, CENTRE_PARAMS, null));
        rows.add(row("a centre 63 from another", flat(OWN), CENTRE, 73, 10, CENTRE_PARAMS, Rule.SPACING));
        rows.add(row("a centre needs no reach", flat(), CENTRE, 74, 74, CENTRE_PARAMS, null));
        rows.add(row("unaffordable", flat(OWN), HOUSE, 30, 10, new Params(false, 0, 48, false), Rule.AFFORDABLE));
        rows.add(row(
                "taken and steep: taken is told",
                new World((x, z) -> x == 34 ? 20 : 10, (x, z) -> x == 30, (x, z) -> true, List.of(OWN)),
                HOUSE,
                30,
                10,
                HOUSE_PARAMS,
                Rule.FREE));
        rows.add(row(
                "steep and too far: steep is told",
                new World((x, z) -> x == 74 ? 20 : 10, (x, z) -> false, (x, z) -> true, List.of(OWN)),
                HOUSE,
                70,
                10,
                HOUSE_PARAMS,
                Rule.FLAT));
        return rows.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void theRules(String name, PlacementRules.World w, Blueprint b, int x, int z, Params p, @Nullable Rule expected) {
        Verdict v = PlacementRules.check(w, b, Footprint.of(b, x, z, 0), 0, p);
        assertThat(v.failed()).isEqualTo(expected);
        assertThat(v.ok()).isEqualTo(expected == null);
    }

    @Test
    void columnRulesAreToldPerCellAndTheOthersOnEvery() {
        World taken = new World((x, z) -> 10, (x, z) -> x == 30 && z == 10, (x, z) -> true, List.of(OWN));
        Verdict v = PlacementRules.check(taken, HOUSE, Footprint.of(HOUSE, 30, 10, 0), 0, HOUSE_PARAMS);
        assertThat(v.cells()).hasSize(25);
        for (int k = 0; k < v.cells().size(); k++) {
            int cell = v.cells().get(k);
            boolean corner = cell == ((30 << 16) | 10);
            assertThat(v.perCell().get(k)).as("cell %d", k).isEqualTo(corner ? Rule.FREE : null);
        }
        Verdict far = PlacementRules.check(flat(OWN), HOUSE, Footprint.of(HOUSE, 70, 10, 0), 0, HOUSE_PARAMS);
        assertThat(far.perCell()).containsOnly(Rule.REACH);
    }

    @Test
    void aTurnedFootprintIsCheckedWhereItLies() {
        Blueprint barracks = PlaceholderBlueprints.barracks(); // 9 x 7
        World eastTaken = new World((x, z) -> 10, (x, z) -> x == 37 && z == 10, (x, z) -> true, List.of(OWN));
        assertThat(PlacementRules.check(eastTaken, barracks, Footprint.of(barracks, 30, 10, 0), 0, HOUSE_PARAMS)
                        .failed())
                .as("unturned, 9 along X: it reaches x 38")
                .isEqualTo(Rule.FREE);
        assertThat(PlacementRules.check(eastTaken, barracks, Footprint.of(barracks, 30, 10, 1), 0, HOUSE_PARAMS)
                        .failed())
                .as("turned, 7 along X: it stops at x 36")
                .isNull();
    }
}
