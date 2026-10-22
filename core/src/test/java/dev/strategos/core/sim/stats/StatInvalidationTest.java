package dev.strategos.core.sim.stats;

import static dev.strategos.core.sim.stats.StatsFixture.HACK;
import static dev.strategos.core.sim.stats.StatsFixture.HEALTH;
import static dev.strategos.core.sim.stats.StatsFixture.SET;
import static dev.strategos.core.sim.stats.StatsFixture.template;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchBus;
import dev.strategos.core.sim.StatsChanged;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DATA-02.4 (spec §7.2): adding or removing one modifier evaluates again exactly the blocks it reaches (its faction's,
 * for the templates its filter matches that have its path, or one unit's overlay) and posts {@link StatsChanged} with
 * its scope and paths; a modifier that reaches nothing changes nothing and posts nothing.
 */
class StatInvalidationTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);

    private final List<StatsChanged> posted = new ArrayList<>();
    private final IdentityStore identity = new IdentityStore();
    private StatService stats = StatsFixture.service();

    @BeforeEach
    void setUp() {
        MatchBus bus = new MatchBus();
        bus.subscribe(StatsChanged.class, posted::add);
        identity.ensureCapacity(2);
        identity.set(0, template("spearman"), 0);
        identity.set(1, template("spearman"), 0);
        stats = StatsFixture.service(bus, identity);
        readAll();
    }

    @Test
    void aFactionModifierEvaluatesOnlyTheBlocksItReaches() {
        stats.add(F0, Modifier.mul(HEALTH, 2, 7).withFilter("Infantry"));

        assertThat(readAll())
                .as("archer, captain, champion and spearman of faction 0")
                .isEqualTo(4);
        assertThat(posted).containsExactly(new StatsChanged(StatScope.FACTION, 0, List.of(HEALTH)));
        assertThat(stats.blockValue(template("horseman"), 0, stats.path(HEALTH)))
                .isEqualTo(160);
        assertThat(stats.blockValue(template("spearman"), 1, stats.path(HEALTH)))
                .isEqualTo(100);

        stats.add(F1, Modifier.add(HACK, 1, 8));
        assertThat(readAll())
                .as("the two templates with a hack damage, of faction 1")
                .isEqualTo(2);

        stats.remove(F0, 7);
        assertThat(readAll()).as("the same four again").isEqualTo(4);
        assertThat(posted)
                .containsExactly(
                        new StatsChanged(StatScope.FACTION, 0, List.of(HEALTH)),
                        new StatsChanged(StatScope.FACTION, 1, List.of(HACK)),
                        new StatsChanged(StatScope.FACTION, 0, List.of(HEALTH)));
    }

    @Test
    void aModifierThatReachesNothingChangesNothing() {
        stats.add(F0, Modifier.add(HACK, 1, 1).withFilter("Ranged"));
        stats.add(F0, Modifier.add("Nothing/here", 1, 2));
        stats.remove(F1, 3);

        assertThat(readAll()).isZero();
        assertThat(posted).isEmpty();
    }

    @Test
    void aUnitsOwnModifierEvaluatesOnlyItsOverlay() {
        int handle = Handle.of(0, 1);

        stats.addPersonal(handle, Modifier.add(HEALTH, 50, 3));

        assertThat(readAll()).as("unit 0's overlay").isEqualTo(1);
        assertThat(stats.read(0, stats.path(HEALTH))).isEqualTo(150);
        assertThat(stats.read(1, stats.path(HEALTH))).isEqualTo(100);
        assertThat(posted).containsExactly(new StatsChanged(StatScope.UNIT, handle, List.of(HEALTH)));

        stats.add(F0, Modifier.mul(HEALTH, 2, 4).withFilter("Melee"));
        assertThat(readAll())
                .as("the overlay, and the spearman, champion and horseman blocks")
                .isEqualTo(4);
        assertThat(stats.read(0, stats.path(HEALTH))).isEqualTo(250);

        stats.removePersonal(handle, 3);
        assertThat(readAll()).as("unit 0 is back on its template's block").isZero();
        assertThat(stats.read(0, stats.path(HEALTH))).isEqualTo(200);
        assertThat(posted).hasSize(3).last().isEqualTo(new StatsChanged(StatScope.UNIT, handle, List.of(HEALTH)));
    }

    /** Reads every block and unit; returns how many were evaluated again. */
    private long readAll() {
        long before = stats.evaluations();
        for (int t = 0; t < SET.all().size(); t++) {
            for (int f = 0; f < 2; f++) {
                stats.blockValue(t, f, stats.path(HEALTH));
            }
        }
        for (int i = 0; i < 2; i++) {
            stats.read(i, stats.path(HEALTH));
        }
        return stats.evaluations() - before;
    }
}
