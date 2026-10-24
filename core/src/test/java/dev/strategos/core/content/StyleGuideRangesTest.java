package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * ART-01.2: every building with a blueprint keeps the style guide's ranges for its class ({@code docs/art/style-guide.md}
 * §4): the footprint's longer side, the height and the block count, and the rate it rises at with one builder, so it
 * reads at camera heights of 24–64 blocks and grows visibly. A class the guide does not name fails.
 */
class StyleGuideRangesTest {
    /** One class's ranges: footprint side, height, blocks, and blocks a second with one builder. */
    record Range(String token, int sideMin, int sideMax, int heightMin, int heightMax, int blocksMin, int blocksMax) {}

    static final List<Range> RANGES = List.of(
            new Range("Field", 4, 8, 1, 2, 16, 128),
            new Range("House", 4, 7, 4, 7, 60, 200),
            new Range("Barracks", 7, 11, 5, 9, 150, 400),
            new Range("Storehouse", 5, 8, 4, 6, 80, 200),
            new Range("Farmstead", 5, 8, 3, 6, 60, 160),
            new Range("CivicCentre", 9, 15, 6, 10, 300, 800),
            new Range("Deposit", 3, 6, 2, 4, 18, 90));

    static final double RISE_MIN = 1.5;
    static final double RISE_MAX = 6;

    @Test
    void placeholderBlueprintsKeepTheirClassRanges() {
        TemplateSet t = TemplateSet.builtin();
        ValuePath time = Objects.requireNonNull(t.valuePaths().byPath("Cost/time"));
        int checked = 0;
        for (UnitTemplate u : t.all()) {
            Blueprint b = t.blueprint(u.netIndex());
            if (b == null) {
                continue;
            }
            Range r = RANGES.stream()
                    .filter(x -> u.classes().contains(x.token()))
                    .findFirst()
                    .orElse(null);
            assertThat(r)
                    .as("%s has a class the style guide names", u.id().value())
                    .isNotNull();
            int side = Math.max(b.sizeX(), b.sizeZ());
            String what = u.id().value() + " " + b.sizeX() + " x " + b.sizeY() + " x " + b.sizeZ() + ", " + b.count();
            assertThat(side).as(what).isBetween(r.sideMin(), r.sideMax());
            assertThat(b.sizeY()).as(what).isBetween(r.heightMin(), r.heightMax());
            assertThat(b.count()).as(what).isBetween(r.blocksMin(), r.blocksMax());
            if (!u.classes().contains("Deposit")) { // a deposit is spawned, never built
                double rise = b.count() / t.value(u.netIndex(), time.id());
                assertThat(rise).as(what + " blocks a second").isBetween(RISE_MIN, RISE_MAX);
            }
            checked++;
        }
        assertThat(checked).isEqualTo(10);
    }
}
