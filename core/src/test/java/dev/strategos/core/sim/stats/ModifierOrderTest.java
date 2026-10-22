package dev.strategos.core.sim.stats;

import static dev.strategos.core.sim.stats.StatsFixture.HACK;
import static dev.strategos.core.sim.stats.StatsFixture.HEALTH;
import static dev.strategos.core.sim.stats.StatsFixture.SET;
import static dev.strategos.core.sim.stats.StatsFixture.UNITS;
import static dev.strategos.core.sim.stats.StatsFixture.template;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * DATA-02.2 (spec §7.2): over 200 seeded insertion orders, of twenty random sets of faction and unit modifiers with
 * values whose sums and products round differently in different orders, every value and token list is the same, to the
 * bit; and removing a source and adding it back changes nothing.
 */
class ModifierOrderTest {
    private static final String[] PATHS = {HEALTH, HACK, CorePaths.WALK_SPEED, CorePaths.TURN_RATE};
    private static final String[] FILTERS = {
        "", "", "Infantry", "Melee", "Infantry+Melee", "Cavalry", "Infantry+!Ranged"
    };
    private static final String[] TOKENS = {"strategos:unit/spearman", "strategos:unit/archer", "a", "b"};
    private static final int UNIT_COUNT = 3;

    /** A modifier and where it goes: a faction, or a unit when {@code unit} is not -1. */
    private record Placed(int faction, int unit, Modifier modifier) {}

    @Test
    void insertionOrderNeverChangesAResult() {
        for (int seed = 1; seed <= 200; seed++) {
            List<Placed> placed = modifiers(new Random(seed / 10));
            List<String> expected = results(placed);
            List<Placed> shuffled = new ArrayList<>(placed);
            Collections.shuffle(shuffled, new Random(seed));

            assertThat(results(shuffled)).as("seed %d", seed).isEqualTo(expected);
        }
    }

    @Test
    void removingASourceAndAddingItBackChangesNothing() {
        for (int seed = 1; seed <= 20; seed++) {
            List<Placed> placed = modifiers(new Random(seed));
            List<String> expected = results(placed);
            StatService stats = service();
            for (Placed p : placed) {
                add(stats, p);
            }
            Placed some = placed.get(seed % placed.size());
            List<Placed> again = new ArrayList<>();
            for (Placed p : placed) {
                boolean sameSource = p.modifier().sourceId() == some.modifier().sourceId();
                boolean sameScope =
                        some.unit() < 0 ? p.unit() < 0 && p.faction() == some.faction() : p.unit() == some.unit();
                if (sameSource && sameScope) {
                    again.add(p);
                }
            }
            if (some.unit() < 0) {
                stats.remove(new FactionSlot(some.faction()), some.modifier().sourceId());
            } else {
                stats.removePersonal(Handle.of(some.unit(), 1), some.modifier().sourceId());
            }
            Collections.reverse(again);
            for (Placed p : again) {
                add(stats, p);
            }

            assertThat(read(stats)).as("seed %d", seed).isEqualTo(expected);
        }
    }

    private static List<Placed> modifiers(Random r) {
        List<Placed> list = new ArrayList<>();
        int count = 20 + r.nextInt(20);
        for (int k = 0; k < count; k++) {
            int faction = r.nextInt(2);
            int unit = r.nextInt(4) == 0 ? r.nextInt(UNIT_COUNT) : -1;
            String filter = FILTERS[r.nextInt(FILTERS.length)];
            int stage = r.nextInt(3);
            int priority = r.nextInt(3);
            int source = 1 + r.nextInt(8);
            Modifier m;
            if (r.nextInt(6) == 0) {
                m = Modifier.tokens(UNITS, (r.nextBoolean() ? "+" : "-") + TOKENS[r.nextInt(TOKENS.length)], source);
            } else {
                String path = PATHS[r.nextInt(PATHS.length)];
                double value = awkward(r);
                m = switch (r.nextInt(3)) {
                    case 0 -> Modifier.replace(path, value * 100, source);
                    case 1 -> Modifier.mul(path, 0.5 + r.nextDouble(), source);
                    default -> Modifier.add(path, value, source);
                };
            }
            list.add(new Placed(
                    faction, unit, m.withFilter(filter).atStage(stage).withPriority(priority)));
        }
        return list;
    }

    /** Values such as 0.1 and 1e-9 next to 3.7e2, whose sums depend on their order. */
    private static double awkward(Random r) {
        double[] scales = {1e-9, 0.1, 0.3, 1, 3.7e2};
        return (r.nextDouble() - 0.4) * scales[r.nextInt(scales.length)];
    }

    private static List<String> results(List<Placed> placed) {
        StatService stats = service();
        for (Placed p : placed) {
            add(stats, p);
        }
        return read(stats);
    }

    private static StatService service() {
        IdentityStore identity = new IdentityStore();
        identity.ensureCapacity(UNIT_COUNT);
        identity.set(0, template("spearman"), 0);
        identity.set(1, template("champion"), 1);
        identity.set(2, template("captain"), 0);
        return StatsFixture.service(new MatchBus(), identity);
    }

    private static void add(StatService stats, Placed p) {
        if (p.unit() < 0) {
            stats.add(new FactionSlot(p.faction()), p.modifier());
        } else {
            stats.addPersonal(Handle.of(p.unit(), 1), p.modifier());
        }
    }

    /** Every block's and unit's numbers as bits, and their token lists. */
    private static List<String> read(StatService stats) {
        List<String> out = new ArrayList<>();
        for (int t = 0; t < SET.all().size(); t++) {
            for (int f = 0; f < 2; f++) {
                for (ValuePath p : SET.valuePaths().all()) {
                    out.add(t + "/" + f + " " + p.path() + " "
                            + Long.toHexString(Double.doubleToLongBits(stats.blockValue(t, f, p.id()))));
                }
                out.add(t + "/" + f + " " + UNITS + " " + stats.blockTokens(t, f, stats.tokenPath(UNITS)));
            }
        }
        for (int i = 0; i < UNIT_COUNT; i++) {
            for (ValuePath p : SET.valuePaths().all()) {
                out.add("unit " + i + " " + p.path() + " "
                        + Long.toHexString(Double.doubleToLongBits(stats.read(i, p.id()))));
            }
            out.add("unit " + i + " " + UNITS + " " + stats.tokens(i, stats.tokenPath(UNITS)));
        }
        return out;
    }
}
