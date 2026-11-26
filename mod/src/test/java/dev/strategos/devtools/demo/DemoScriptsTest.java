package dev.strategos.devtools.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The demo scripts at L1 (M6 row 72): each chapter runs to its end on {@link FakeStage}, the core's harness, and the
 * state it ends in is the state it set out to show. A chapter that grants too little, orders the wrong target or
 * waits on what never happens fails here, not in the window.
 */
class DemoScriptsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap(); // the towers chapter names blocks
    }

    private static boolean has(FakeStage st, int faction, dev.strategos.core.shared.TemplateId t) {
        return st.buildings(faction, t).stream().anyMatch(b -> b.state() == BuildingStore.COMPLETE);
    }

    @Test
    void theFakeRunsAnOldChapterTheTowerShootsItsVisitors() {
        FakeStage st = FakeStage.run(new TowerChapter());
        assertThat(has(st, 0, Ids.TOWER)).isTrue();
        assertThat(Fights.hurt(st, 1, Ids.INFANTRY) + (4 - Fights.alive(st, 1, Ids.INFANTRY)))
                .as("the tower's arrows landed")
                .isPositive();
        assertThat(st.commands()).anyMatch(c -> c.startsWith("title @a subtitle"));
    }

    @Test
    void theFakeRunsAnotherOldChapterTheBellsGarrisonIsLeftEmpty() {
        FakeStage st = FakeStage.run(new GarrisonChapter());
        assertThat(Chapters.garrisoned(st, 0)).as("all clear let everyone out").isZero();
        assertThat(Fights.alive(st, 1, Ids.HORSEMAN)).isZero();
    }

    @Test
    void theTerritoryChapterRefusesTheHouseOutsideAndBuildsTheSecondCentre() {
        FakeStage st = FakeStage.run(new TerritoryChapter());
        var refusals = st.harness().events(CommandResult.class).stream()
                .filter(r -> r.outcome() == CommandOutcome.REJECTED)
                .toList();
        assertThat(refusals).hasSize(1);
        assertThat(refusals.getFirst().firstReason()).isEqualTo(RejectReason.OUTSIDE_TERRITORY);
        assertThat(st.buildings(0, Ids.CIVIC_CENTRE)).hasSize(2).allMatch(b -> b.state() == BuildingStore.COMPLETE);
        assertThat(st.buildings(0, Ids.HOUSE)).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void theCaptureChapterEndsWithTheBarracksTheAttackersAndAFreshRecruit() {
        FakeStage st = FakeStage.run(new CaptureChapter());
        assertThat(st.buildings(0, Ids.BARRACKS)).hasSize(1);
        assertThat(st.buildings(1, Ids.BARRACKS)).isEmpty();
        assertThat(Fights.alive(st, 0, Ids.INFANTRY)).isGreaterThan(CaptureChapter.ATTACKERS);
    }

    @Test
    void theGarrisonHealChapterHealsEveryoneAndGarrisonsTheRecruitsInTheTower() {
        FakeStage st = FakeStage.run(new GarrisonHealChapter());
        assertThat(Fights.hurt(st, 0, Ids.INFANTRY)).as("nobody still hurt").isZero();
        BuildingView tower = Chapters.finished(st, 0, Ids.SENTRY_TOWER);
        assertThat(tower).isNotNull();
        assertThat(Chapters.garrisoned(st, 0)).isGreaterThanOrEqualTo(GarrisonHealChapter.RECRUITS);
    }

    @Test
    void theTowersChapterUpgradesTheSentryInPlaceAndTheGarrisonStays() {
        FakeStage st = FakeStage.run(new TowersChapter());
        assertThat(st.buildings(0, Ids.SENTRY_TOWER)).isEmpty();
        assertThat(has(st, 0, Ids.TOWER)).isTrue();
        assertThat(Chapters.garrisoned(st, 0)).isGreaterThanOrEqualTo(TowersChapter.GARRISON);
        assertThat(st.blocks()).as("the chokepoint's walls").isPositive();
    }

    @Test
    void thePalisadeChapterBuildsTheWallWithItsGateAndTheRaidIsPlayedOut() {
        FakeStage st = FakeStage.run(new PalisadeChapter());
        assertThat(PalisadeChapter.wallDone(st))
                .as("every segment and the gate complete")
                .isTrue();
        assertThat(st.q().gateDoors()).as("the gate is registered").hasSize(1);
        assertThat(st.blocks()).as("the gap's rocks").isPositive();
    }

    @Test
    void theCityChapterRefusesTheFarHouseUntilTheCityPhaseWidensTheBorder() {
        FakeStage st = FakeStage.run(new CityChapter());
        var refusals = st.harness().events(CommandResult.class).stream()
                .filter(r -> r.outcome() == CommandOutcome.REJECTED)
                .toList();
        assertThat(refusals).hasSize(1);
        assertThat(refusals.getFirst().firstReason()).isEqualTo(RejectReason.OUTSIDE_TERRITORY);
        assertThat(st.q().phase(new dev.strategos.core.shared.FactionSlot(0))).isEqualTo(2);
        assertThat(st.buildings(0, Ids.HOUSE)).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void theTreeChapterTakesOneOfThePairAndRefusesTheOther() {
        FakeStage st = FakeStage.run(new TreeChapter());
        var researched = st.q().researched(new dev.strategos.core.shared.FactionSlot(0));
        assertThat(researched).contains(Ids.PHALANX).doesNotContain(Ids.SKIRMISH_DRILL);
        assertThat(st.harness().events(CommandResult.class)).anyMatch(r -> r.outcome() == CommandOutcome.REJECTED);
    }

    @Test
    void theHealersChapterEndsWithTheTroopsWholeAndAHealerTrained() {
        FakeStage st = FakeStage.run(new HealersChapter());
        assertThat(Fights.hurt(st, 0, Ids.INFANTRY)).isZero();
        assertThat(Fights.alive(st, 0, Ids.HEALER)).isPositive();
        assertThat(Chapters.garrisoned(st, 0)).isPositive();
    }

    @Test
    void theVeteransChapterEndsWithARankedSurvivor() {
        FakeStage st = FakeStage.run(new VeteransChapter());
        assertThat(java.util.Arrays.stream(st.handles(0, Ids.HORSEMAN))
                        .anyMatch(h -> st.q().rank(h) > 0))
                .as("a survivor with a rank")
                .isTrue();
        assertThat(Fights.alive(st, 1, Ids.ARCHER)).isZero();
    }

    @Test
    void theFormationsChapterLeavesOneMemberOutAndTheRestInLine() {
        FakeStage st = FakeStage.run(new FormationsChapter());
        int[] army = st.handles(0, Ids.INFANTRY);
        assertThat(army).hasSize(FormationsChapter.ARMY);
        assertThat(java.util.Arrays.stream(army).filter(h -> st.q().formationOf(h) != 0))
                .hasSize(FormationsChapter.ARMY - 1);
    }

    @Test
    void thePatrolChapterKillsTheIntruderAndTheRaiderAndKeepsThePatrolAndItsGuards() {
        FakeStage st = FakeStage.run(new PatrolChapter());
        assertThat(Fights.alive(st, 1, Ids.INFANTRY)).isZero();
        assertThat(Fights.alive(st, 0, Ids.INFANTRY)).isEqualTo(1 + PatrolChapter.GUARDS);
        assertThat(Fights.alive(st, 0, Ids.WORKER)).isEqualTo(1);
    }

    @Test
    void theHuntChapterBringsMeatHomeAndPaysLootForTheKill() {
        FakeStage st = FakeStage.run(new HuntChapter());
        assertThat(st.gathered(0, "food")).isGreaterThanOrEqualTo(20);
        assertThat(Chapters.looted(st, 0, "wood")).isPositive();
    }

    @Test
    void theChargeChapterPutsTheAbilityOnCooldownAndWinsTheFight() {
        FakeStage st = FakeStage.run(new ChargeChapter());
        assertThat(Fights.alive(st, 1, Ids.INFANTRY)).isZero();
        assertThat(st.q().abilityCooldown(st.handles(0, Ids.HORSEMAN)[0], "charge"))
                .isPositive();
    }

    @Test
    void theNightChapterRunsTheClockToNight() {
        FakeStage st = FakeStage.run(new NightChapter());
        assertThat(dev.strategos.core.sim.DayClock.night(st.q().tick())).isTrue();
        assertThat(st.commands()).contains("tick sprint stop");
    }
}
