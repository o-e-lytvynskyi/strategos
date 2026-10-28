package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.DuelLayout.Kind;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * UNIT-07 and CMB-16 check 2: the roster's counters over equal-cost groups, 200 seeds of placement each (M5 spec 2.7).
 */
@Tag("slow")
class DuelTest {
    private static final String SEEDS = "m0-200";
    private static final int SIZE = 200;
    private static final int COUNTER = 170;
    private static final int WINS = 100;

    @Test
    void eachCounterWins() {
        int spearmen = Duel.wins(Kind.SPEARMAN, Kind.HORSEMAN, SEEDS, 0);
        int horsemen = Duel.wins(Kind.HORSEMAN, Kind.ARCHER, SEEDS, 0);
        int archers = Duel.wins(Kind.ARCHER, Kind.SPEARMAN, SEEDS, 0);
        System.out.printf("counters: spearmen %d, horsemen %d, archers %d of %d%n", spearmen, horsemen, archers, SIZE);
        assertThat(spearmen).as("spearmen over horsemen").isGreaterThanOrEqualTo(COUNTER);
        assertThat(horsemen).as("horsemen over archers").isGreaterThanOrEqualTo(COUNTER);
        assertThat(archers).as("archers over spearmen").isGreaterThanOrEqualTo(COUNTER);
    }

    @Test
    void everyUnitWinsOneAndLosesOne() {
        Map<Kind, Integer> winsOne = new EnumMap<>(Kind.class);
        Map<Kind, Integer> losesOne = new EnumMap<>(Kind.class);
        for (Kind a : Kind.values()) {
            for (Kind b : Kind.values()) {
                if (a == b) continue;
                int wins = Duel.wins(a, b, SEEDS, 0);
                System.out.printf("duel %s over %s: %d of %d%n", a, b, wins, SIZE);
                if (wins >= WINS) winsOne.merge(a, 1, Integer::sum);
                if (wins < SIZE - WINS) losesOne.merge(a, 1, Integer::sum);
            }
        }
        for (Kind k : Kind.values()) {
            assertThat(winsOne.getOrDefault(k, 0)).as("%s wins a matchup", k).isPositive();
            assertThat(losesOne.getOrDefault(k, 0)).as("%s loses a matchup", k).isPositive();
        }
    }

    /** CMB-16.2: archers six blocks up beat equal archers below. */
    @Test
    void archersOnAHillBeatArchersBelow() {
        int wins = Duel.wins(Kind.ARCHER, Kind.ARCHER, SEEDS, DuelLayout.HILL);
        System.out.printf("hill: archers on %s blocks beat archers below %d of %d%n", DuelLayout.HILL, wins, SIZE);
        assertThat(wins).isGreaterThanOrEqualTo(COUNTER);
        int flat = Duel.wins(Kind.ARCHER, Kind.ARCHER, SEEDS, 0);
        System.out.printf("hill control: equal archers on the flat, the first group wins %d of %d%n", flat, SIZE);
        assertThat(flat).as("without a hill the sides are even").isBetween(SIZE * 35 / 100, SIZE * 65 / 100);
    }
}
