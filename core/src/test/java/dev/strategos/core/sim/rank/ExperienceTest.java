package dev.strategos.core.sim.rank;

import static dev.strategos.core.sim.rank.RankFixtures.F0;
import static dev.strategos.core.sim.rank.RankFixtures.F1;
import static dev.strategos.core.sim.rank.RankFixtures.spawn;
import static dev.strategos.core.sim.rank.RankFixtures.spearman;
import static dev.strategos.core.sim.rank.RankFixtures.state;
import static dev.strategos.core.sim.rank.RankFixtures.use;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.PromotionDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitRanked;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.DamageType;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * CMB-11.1: experience is the damage dealt times the target's {@code Promotion/value} over its most hit points, earned
 * at the damage step by the unit that dealt it (an ability's damage included), in thousandths of a point; the rank
 * rises as the thresholds are crossed; a unit without ranks, a building and a hit on a friend earn nothing.
 */
class ExperienceTest {
    private static MatchState s(SimHarness h) {
        return state(h);
    }

    private static long earned(SimHarness h, int unit) {
        return s(h).play().experience().experience(Handle.index(unit));
    }

    private static int rank(SimHarness h, int unit) {
        return s(h).play().experience().rank(Handle.index(unit));
    }

    private static double value(SimHarness h, int target) {
        MatchState s = s(h);
        return s.stats().read(Handle.index(target), s.stats().path(CorePaths.PROMOTION_VALUE));
    }

    static Stream<Arguments> targets() {
        return Stream.of(
                Arguments.of("a worker, value 60 of 50 hit points", TemplateSet.WORKER, 60.0),
                Arguments.of("a spearman, value 100 of 100", TemplateSet.INFANTRY, 100.0),
                Arguments.of("an archer, value 120 of 70", RankFixtures.ARCHER, 120.0),
                Arguments.of("a horseman, value 150 of 200", RankFixtures.HORSEMAN, 150.0),
                Arguments.of("a ram, value 220 of 500, through its resistance", RankFixtures.RAM, 220.0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("targets")
    void aHitEarnsItsShareOfTheTargetsValue(String name, TemplateId template, double value) {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int target = spawn(h, template, F1, 11, 16);
        assertThat(value(h, target)).isEqualTo(value);
        use(h, a, "hit", target);
        float dealt = h.events(UnitHurt.class).get(0).amount();
        double max = s(h).stats().maxHealth(Handle.index(target));
        assertThat(earned(h, a))
                .as("damage x value / most hit points, in milli-points")
                .isEqualTo(Math.round(dealt * value / max * PromotionDef.SCALE))
                .isPositive();
        assertThat(rank(h, a)).isZero();
    }

    @Test
    void aKillIsWorthTheWholeValueHoweverManyHitsAndNeverMoreThanWhatWasLeft() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int worker = spawn(h, TemplateSet.WORKER, F1, 11, 16);
        use(h, a, "hit", worker); // 25 of 50
        assertThat(earned(h, a)).isEqualTo(30_000);
        use(h, a, "hit", worker); // 25 more: 50 of 50, dead
        assertThat(earned(h, a)).isEqualTo(60_000);
        int second = spawn(h, TemplateSet.WORKER, F1, 11, 17);
        use(h, a, "hit", second);
        use(h, a, "kill", second); // 500 on the 25 left: it earns 25's share, not 500's
        assertThat(earned(h, a)).as("two workers, 60 each").isEqualTo(120_000);
    }

    @Test
    void theThresholdsRaiseTheRankAndEachRiseIsAnEventOnce() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        List<int[]> table = List.of(
                new int[] {3, 0}, // 180 < 200
                new int[] {1, 1}, // 240
                new int[] {4, 1}, // 480 < 500
                new int[] {1, 2}, // 540
                new int[] {7, 2}, // 960 < 1000
                new int[] {1, 3}, // 1020
                new int[] {5, 3}); // the top: no more
        for (int[] row : table) {
            RankFixtures.killWorkers(h, a, row[0]);
            assertThat(rank(h, a))
                    .as("after %d more kills, %d points", row[0], earned(h, a) / 1000)
                    .isEqualTo(row[1]);
        }
        assertThat(h.events(UnitRanked.class))
                .extracting(UnitRanked::handle, UnitRanked::rank)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(a, 1),
                        org.assertj.core.groups.Tuple.tuple(a, 2),
                        org.assertj.core.groups.Tuple.tuple(a, 3));
    }

    @Test
    void aUnitWithoutRanksEarnsNothingAndNeitherDoesAHitOnAFriend() {
        SimHarness h = RankFixtures.match();
        int worker = spawn(h, TemplateSet.WORKER, F0, 10, 16); // a worker has the ability but no Promotion
        int enemy = spearman(h, F1, 11, 16);
        use(h, worker, "kill", enemy);
        assertThat(s(h).play().experience().empty())
                .as("a worker earns no experience")
                .isTrue();
        int a = spearman(h, F0, 10, 18);
        int friend = spawn(h, TemplateSet.WORKER, F0, 11, 18);
        Ranks.credit(s(h), a, friend, 25, 50);
        assertThat(earned(h, a)).as("a friend is worth nothing").isZero();
    }

    @Test
    void aBuildingEarnsNothingAndNeitherDoesAHitFromANoOne() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int target = spawn(h, TemplateSet.WORKER, F1, 11, 16);
        int building = Handle.of(BuildingStore.BASE, 1);
        Ranks.credit(s(h), building, target, 25, 50);
        Ranks.credit(s(h), Handle.NONE, target, 25, 50);
        assertThat(s(h).play().experience().empty()).isTrue();
        Ranks.credit(s(h), a, target, 25, 50);
        assertThat(earned(h, a)).isEqualTo(30_000);
    }

    @Test
    void aUnitWhoseTemplateLacksAValueIsWorthNothing() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int walker = spawn(h, TemplateSet.DEBUG_WALKER, F1, 11, 16);
        use(h, a, "hit", walker);
        assertThat(h.events(UnitHurt.class)).isNotEmpty();
        assertThat(s(h).play().experience().empty()).isTrue();
    }

    @Test
    void theSlotOfADeadUnitStartsClean() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        RankFixtures.killWorkers(h, a, 4);
        assertThat(rank(h, a)).isEqualTo(1);
        int slot = Handle.index(a);
        s(h).removeUnit(a);
        assertThat(s(h).play().experience().empty())
                .as("a unit that leaves takes it with it")
                .isTrue();
        int fresh = spearman(h, F0, 10, 20);
        assertThat(Handle.index(fresh)).as("the slot is reused").isEqualTo(slot);
        assertThat(rank(h, fresh)).isZero();
        assertThat(s(h).stats().maxHealth(slot)).isEqualTo(100);
    }

    @Test
    void aUnitKilledEarlierInTheSameTickEarnsNothingAndDiesOnce() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int b = spearman(h, F1, 11, 16);
        s(h).play().experience().set(Handle.index(a), 950_000, 2); // 100 short of rank 3, as the other
        s(h).play().experience().set(Handle.index(b), 950_000, 2);
        h.submit(RankFixtures.P0, UseAbility.replace(UnitSet.of(a), new AbilityId("kill"), Target.unit(b)));
        h.submit(
                new dev.strategos.core.shared.PlayerSlot(1),
                UseAbility.replace(UnitSet.of(b), new AbilityId("kill"), Target.unit(a)));
        h.step();
        assertThat(h.events(UnitDied.class)).extracting(UnitDied::handle).containsExactlyInAnyOrder(a, b);
        assertThat(h.events(UnitDied.class)).as("each dies once").hasSize(2);
        assertThat(h.events(UnitRanked.class))
                .as("only the one that struck while alive earns")
                .hasSize(1);
    }

    @Test
    void oneHitAcrossTwoThresholdsGivesTheFinalRankOnceWithOneSet() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int i = Handle.index(a);
        s(h).play().experience().set(i, 450_000, 0);
        int horse = spawn(h, RankFixtures.HORSEMAN, F1, 11, 16);
        use(h, a, "kill", horse); // 150: 600, past 200 and 500 at once
        assertThat(rank(h, a)).isEqualTo(2);
        assertThat(h.events(UnitRanked.class))
                .extracting(UnitRanked::handle, UnitRanked::rank)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(a, 2));
        assertThat(s(h).stats().maxHealth(i)).isCloseTo(110, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void friendlyFireInARealPacketEarnsNothing() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int friend = spawn(h, TemplateSet.WORKER, F0, 11, 16);
        s(h).queueDamage(Damage.of(friend, a, DamageType.HACK, 25)); // an ability never hits a friend: a raw packet
        h.step();
        assertThat(h.events(UnitHurt.class)).isNotEmpty();
        assertThat(earned(h, a)).isZero();
    }

    @Test
    void restoreRefusesNegativeExperienceAndKeepsARankAboveTheListShownClamped() {
        SimHarness h = RankFixtures.match();
        int a = spearman(h, F0, 10, 16);
        assertThatThrownBy(() ->
                        Ranks.restore(s(h), new ExperienceStore.Saved(List.of((long) a, -1L), List.of((long) a, 1L))))
                .isInstanceOf(IllegalArgumentException.class);
        Ranks.restore(s(h), new ExperienceStore.Saved(List.of((long) a, 5_000L), List.of((long) a, 5L)));
        assertThat(rank(h, a)).as("the earned rank stays state").isEqualTo(5);
        assertThat(Ranks.effectiveRank(s(h), Handle.index(a)))
                .as("shown clamped")
                .isEqualTo(3);
        assertThat(s(h).stats().maxHealth(Handle.index(a)))
                .isCloseTo(115, org.assertj.core.api.Assertions.within(1e-9));
    }
}
