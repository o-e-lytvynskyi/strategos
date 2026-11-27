package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.order.OrderKind;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ORD-06.1 (M10 spec §2.13, §5): a formation of forty keeps every member within a block of its place while it marches
 * on flat ground, with terrain (paths, avoidance) and without (M0's walk); and the places themselves: by class, one
 * each, the same from the same start, chosen once for the same members.
 */
class FormationSlotTest {
    /** The march of forty, from a loose block, to a target 60 blocks off: the slot errors of every marching tick. */
    private record Errors(double max, double mean, int ticks, int assembledAt, int arrivedAt) {}

    private static Errors march(boolean terrain, FormationShape shape) {
        FormationFixtures f = FormationFixtures.flat(terrain);
        int[] units = f.block(TemplateSet.INFANTRY, 40, 8, 1.5, 20, 50);
        FormationController c = f.march(units, 90, 56, shape);
        double max = 0;
        double sum = 0;
        long samples = 0;
        int ticks = 0;
        int assembledAt = -1;
        for (int t = 1; t <= 1200 && !c.holding(); t++) {
            double before = c.along();
            f.h.step();
            if (c.along() > before) { // the formation walked this tick
                if (assembledAt < 0) {
                    assembledAt = t;
                }
                ticks++;
                for (int k = 0; k < c.size(); k++) {
                    double e = f.error(c, k);
                    max = Math.max(max, e);
                    sum += e;
                    samples++;
                }
            }
        }
        assertThat(c.holding()).as("the formation arrived").isTrue();
        return new Errors(max, sum / samples, ticks, assembledAt, (int) f.h.tick());
    }

    @ParameterizedTest(name = "terrain {0}")
    @ValueSource(booleans = {false, true})
    void fortyKeepSlotErrorUnderABlockWhileMoving(boolean terrain) {
        for (FormationShape shape : FormationShape.values()) {
            Errors e = march(terrain, shape);
            System.out.printf(
                    "FORMATION-SLOT-ERROR terrain=%s shape=%s max=%.3f mean=%.3f marching-ticks=%d first-step=%d arrived-at=%d%n",
                    terrain, shape, e.max(), e.mean(), e.ticks(), e.assembledAt(), e.arrivedAt());
            assertThat(e.max())
                    .as("the largest slot error, %s on terrain %s", shape, terrain)
                    .isLessThan(1.0);
        }
    }

    @Test
    void classesTakeRowsMeleeInFrontRangedBehindTheRestBetween() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] spears = f.block(TemplateSet.INFANTRY, 12, 6, 1.5, 20, 40);
        int[] workers = f.block(TemplateSet.WORKER, 6, 6, 1.5, 20, 46);
        int[] archers = f.block(FormationFixtures.ARCHER, 12, 6, 1.5, 20, 52);
        FormationController c = f.march(
                FormationFixtures.both(FormationFixtures.both(archers, workers), spears), 100, 46, FormationShape.BOX);

        assertThat(c.size()).isEqualTo(30);
        double frontOfSpears = Double.MAX_VALUE;
        double backOfSpears = -Double.MAX_VALUE;
        double frontOfWorkers = Double.MAX_VALUE;
        double backOfWorkers = -Double.MAX_VALUE;
        double frontOfArchers = Double.MAX_VALUE;
        double backOfArchers = -Double.MAX_VALUE;
        for (int k = 0; k < c.size(); k++) {
            int handle = c.member(k);
            double ahead = c.forward(k);
            if (contains(spears, handle)) {
                frontOfSpears = Math.min(frontOfSpears, ahead);
                backOfSpears = Math.max(backOfSpears, ahead);
            } else if (contains(workers, handle)) {
                frontOfWorkers = Math.min(frontOfWorkers, ahead);
                backOfWorkers = Math.max(backOfWorkers, ahead);
            } else {
                frontOfArchers = Math.min(frontOfArchers, ahead);
                backOfArchers = Math.max(backOfArchers, ahead);
            }
        }
        assertThat(frontOfSpears)
                .as("the spears' rearmost row is ahead of the workers' front")
                .isGreaterThan(backOfWorkers);
        assertThat(frontOfWorkers)
                .as("the workers' rearmost row is ahead of the archers' front")
                .isGreaterThan(backOfArchers);
        assertThat(backOfSpears).isGreaterThan(frontOfSpears - 1e-9);
    }

    @Test
    void everyMemberHasOnePlaceAndItsOrderNamesIt() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 23, 6, 1.5, 20, 40);
        FormationController c = f.march(units, 100, 46, FormationShape.BOX);

        Set<Integer> seen = new HashSet<>();
        for (int k = 0; k < c.size(); k++) {
            int handle = c.member(k);
            assertThat(seen.add(handle))
                    .as("unit %s has one place", Handle.toString(handle))
                    .isTrue();
            int i = Handle.index(handle);
            assertThat(f.s.orders().kind(i)).isEqualTo(OrderKind.FORMATION);
            assertThat(f.s.orders().target(i)).isEqualTo(c.id());
            assertThat(f.s.orders().slot(i)).isEqualTo(k);
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(boxed(units));
    }

    @Test
    void aFormationFromTheSameStartHasTheSamePlaces() {
        List<Integer> first = places();
        assertThat(places()).isEqualTo(first);
    }

    @Test
    void placesAreChosenOnceForTheSameMembers() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 30, 6, 1.5, 20, 50);
        FormationController c = f.march(units, 90, 56, FormationShape.BOX);
        List<Integer> chosen = FormationFixtures.members(c);
        double[] lateral = lateral(c);

        f.h.runTicks(200);

        assertThat(FormationFixtures.members(c)).isEqualTo(chosen);
        assertThat(lateral(c)).isEqualTo(lateral);
    }

    @Test
    void aLineIsThreeRanksWide() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 30, 6, 1.5, 20, 40);
        FormationController c = f.march(units, 100, 46, FormationShape.LINE);

        Set<Double> ranks = new HashSet<>();
        for (int k = 0; k < c.size(); k++) {
            ranks.add(c.forward(k));
        }
        assertThat(ranks).as("a line of thirty spearmen: three ranks of ten").hasSize(3);
        double widest = 0;
        for (int k = 0; k < c.size(); k++) {
            widest = Math.max(widest, Math.abs(c.lateral(k)));
        }
        assertThat(widest).as("half the line's width, ten places wide").isGreaterThan(4);
    }

    private static List<Integer> places() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 30, 6, 1.5, 20, 50);
        return FormationFixtures.members(f.march(units, 90, 56, FormationShape.BOX));
    }

    private static double[] lateral(FormationController c) {
        double[] out = new double[c.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = c.lateral(k);
        }
        return out;
    }

    private static boolean contains(int[] units, int handle) {
        for (int u : units) {
            if (u == handle) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> boxed(int[] units) {
        return java.util.Arrays.stream(units).boxed().toList();
    }
}
