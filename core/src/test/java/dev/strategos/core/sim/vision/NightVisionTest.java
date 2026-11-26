package dev.strategos.core.sim.vision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DayClock;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.NightFactor;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * MATCH-16.1: with the rule on, vision follows the time of day exactly: units and buildings see {@code Vision/night}
 * of their range from dusk's end to dawn, the factions' stats move only at those two boundaries, and with the rule off
 * (or a tick before the night) nothing is scaled.
 */
class NightVisionTest {
    private static final int NIGHT = DayClock.NIGHT_FROM - DayClock.START;
    private static final int DAWN = DayClock.DAWN_FROM - DayClock.START;
    private static final PlayRules ON = PlayRules.DEFAULTS.withDayNight(true);

    private static SimHarness harness(PlayRules play) {
        return SimHarness.builder()
                .size(96, 96)
                .factions(2)
                .rules(MatchRules.DEFAULTS.withPlay(play))
                .build();
    }

    @Test
    void aUnitsRadiusFollowsTheClockExactly() {
        SimHarness h = harness(ON);
        MatchState s = KernelAccess.state(h.core());
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        h.step();
        double day = s.stats().vision(0);
        assertThat(day).as("a unit sees 12 by day").isEqualTo(12);
        h.runTicks(NIGHT - 2 - (int) s.tick());
        assertThat(s.tick()).isEqualTo(NIGHT - 2);
        assertThat(s.stats().vision(0)).isEqualTo(day);
        h.step();
        assertThat(s.tick()).isEqualTo(NIGHT - 1);
        assertThat(s.stats().vision(0)).as("the last tick of dusk").isEqualTo(day);
        h.step();
        assertThat(s.tick()).isEqualTo(NIGHT);
        assertThat(s.stats().vision(0))
                .as("the first night tick: scaled by 0.6")
                .isEqualTo(day * 0.6);
        h.runTicks(DAWN - 1 - NIGHT);
        assertThat(s.stats().vision(0)).as("the last night tick").isEqualTo(day * 0.6);
        h.step();
        assertThat(s.stats().vision(0)).as("dawn").isEqualTo(day);
    }

    @Test
    void theGridShrinksWithTheRadius() {
        SimHarness h = harness(ON);
        MatchState s = KernelAccess.state(h.core());
        h.spawn(new FactionSlot(0), new Vec2d(8, 40), 1, 0);
        h.step();
        VisionGrid grid = s.visionSystem().grid(0);
        int cx = VisionGrid.cellOf(8);
        int cz = VisionGrid.cellOf(40);
        assertThat(grid.count(cx + 3, cz))
                .as("by day 12 blocks reach three cells")
                .isPositive();
        h.runTicks(NIGHT - (int) s.tick());
        assertThat(grid.count(cx + 2, cz))
                .as("at night 7.2 blocks reach one cell")
                .isZero();
        assertThat(grid.count(cx + 1, cz)).isPositive();
        h.runTicks(DAWN - NIGHT);
        assertThat(grid.count(cx + 3, cz)).as("dawn restores it").isPositive();
    }

    @Test
    void aBuildingsRadiusIsScaledToo() {
        SimHarness h = harness(ON);
        MatchState s = KernelAccess.state(h.core());
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(new TemplateId("strategos:building/field"), new FactionSlot(0), 40, 40, 0));
        h.step();
        int field = s.buildings().handles()[0];
        int template = s.buildings().template(field);
        int path = s.stats().path("Vision/range");
        assertThat(s.stats().blockValue(template, 0, path)).isEqualTo(12);
        h.runTicks(NIGHT - (int) s.tick());
        assertThat(s.stats().blockValue(template, 0, path)).isEqualTo(12 * 0.6);
    }

    @Test
    void theStatsMoveOnlyAtTheBoundaries() {
        SimHarness h = harness(ON);
        MatchState s = KernelAccess.state(h.core());
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        h.step();
        long v0 = s.stats().version(0);
        h.runTicks(NIGHT - 1 - (int) s.tick());
        assertThat(s.stats().version(0)).as("no change all day").isEqualTo(v0);
        h.step();
        long atNight = s.stats().version(0);
        assertThat(atNight).isGreaterThan(v0);
        h.runTicks(DAWN - 1 - NIGHT);
        assertThat(s.stats().version(0)).as("no change all night").isEqualTo(atNight);
        h.step();
        assertThat(s.stats().version(0)).isGreaterThan(atNight);
        assertThat(s.stats().version(1))
                .as("every faction is scaled, not only the viewer")
                .isGreaterThan(0);
    }

    @Test
    void withTheRuleOffNothingChanges() {
        SimHarness h = harness(PlayRules.DEFAULTS);
        MatchState s = KernelAccess.state(h.core());
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        h.step();
        long v0 = s.stats().version(0);
        h.runTicks(NIGHT + 5 - (int) s.tick());
        assertThat(NightFactor.active(s)).isFalse();
        assertThat(s.stats().vision(0)).isEqualTo(12);
        assertThat(s.stats().version(0)).isEqualTo(v0);
    }

    @Test
    void theRuleSwitchedMidMatchTakesEffectAtOnce() {
        SimHarness h = harness(PlayRules.DEFAULTS);
        MatchState s = KernelAccess.state(h.core());
        h.spawn(new FactionSlot(0), new Vec2d(8, 8), 1, 0);
        h.runTicks(NIGHT + 10);
        assertThat(s.stats().vision(0)).isEqualTo(12);
        h.submit(PlayerSlot.ADMIN, new RuleChange("dayNight", "true"));
        h.step();
        assertThat(s.stats().vision(0)).as("on in the middle of the night").isEqualTo(12 * 0.6);
        h.submit(PlayerSlot.ADMIN, new RuleChange("dayNight", "false"));
        h.step();
        assertThat(s.stats().vision(0)).isEqualTo(12);
    }
}
