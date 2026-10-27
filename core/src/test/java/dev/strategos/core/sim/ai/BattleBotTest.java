package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AI-03 in L1: the battle bot on a flat 128 x 128 ground, its civic centre at (8, 8) and the enemy's far at (112, 112).
 * It focuses fire, goes home below its retreat ratio, shifts what it trains toward the counter of what the enemy
 * fields, and stays within the think budget.
 */
class BattleBotTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    static final TemplateId SPEAR = new TemplateId("strategos:unit/infantry");
    static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    static final TemplateId HORSE = new TemplateId("strategos:unit/horseman");

    /** A bot that only fights: a wave of at least {@code min} at 20 s, mustered 24 blocks out, 30 s of warning. */
    static String script(int min, int queue, String battle) {
        return "{\"maxWorkers\":0,\"ratio\":{},\"build\":[],\"housingMargin\":0,\"soldierQueue\":" + queue
                + ",\"attack\":{\"first\":" + (queue > 0 ? 100_000 : 1) + ",\"every\":600,\"min\":" + min
                + ",\"muster\":24,\"announce\":30},\"sites\":[],\"battle\":" + battle + "}";
    }

    static final String TACTICS = "{\"focus\":\"wounded\",\"behind\":4,\"retreat\":0.6,\"replan\":1200,\"reach\":28,"
            + "\"defence\":600,\"launch\":0}";

    static SimHarness ground(TemplateSet templates) {
        MatchRules d = MatchRules.DEFAULTS;
        SimHarness h = SimHarness.builder()
                .size(128, 128)
                .rules(d.withEconomy(new EconomyRules(d.economy().resources(), 300)))
                .yBand(0, 31, 1)
                .templates(templates)
                .build();
        h.publishAll(VoxelFixtures.flat(128, 128, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 8, 8, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 112, 112, 0));
        h.step();
        return h;
    }

    static void spawn(SimHarness h, TemplateId t, int faction, double x, double z, int n) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(t, new FactionSlot(faction), new Vec2d(x, z), n, 3));
        h.step();
    }

    static ComputerPlayer bot(SimHarness h) {
        return Objects.requireNonNull(KernelAccess.state(h.core()).aiSlots().get(0));
    }

    static List<UnitView> units(SimHarness h, int faction) {
        MatchQueries q = h.core().queries();
        List<UnitView> out = new ArrayList<>();
        for (int handle : q.unitHandlesOf(new FactionSlot(faction))) {
            UnitView u = q.unit(handle);
            if (u != null) {
                out.add(u);
            }
        }
        return out;
    }

    static int target(SimHarness h, UnitView u) {
        var orders = h.core().queries().orders(u.handle());
        return !orders.isEmpty() && orders.get(0).kind() == OrderKind.ATTACK
                ? orders.get(0).target()
                : 0;
    }

    /**
     * AI-03.1: ten spearmen against six (strength 1.7) pick one target at a time
     * and share it, and the one they pick after a death is among the most wounded in reach; six against fourteen
     * (strength 0.43) go home at first contact, and no one attacks again until the wave is home.
     */
    @Test
    @Tag("slow")
    void focusesAndRetreats() {
        // focus
        SimHarness h = ground(TemplateSet.builtin());
        spawn(h, SPEAR, 0, 30, 30, 10);
        spawn(h, SPEAR, 1, 62, 62, 6);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "battle", script(4, 0, TACTICS)));
        int shared = 0;
        int changes = 0;
        int last = 0;
        List<String> wrong = new ArrayList<>();
        while (h.tick() < 3_000 && !units(h, 1).isEmpty() && !units(h, 0).isEmpty()) {
            h.step();
            int focus = bot(h).focus();
            if (focus != 0 && focus != last) {
                changes++;
                UnitView f = h.core().queries().unit(focus);
                double min = 1;
                for (UnitView e : units(h, 1)) {
                    min = Math.min(min, fraction(h, e));
                }
                if (f == null || fraction(h, f) > min + 0.15) {
                    wrong.add("tick " + h.tick() + ": focus " + focus + " at " + (f == null ? "dead" : fraction(h, f))
                            + " while the lowest is " + min);
                }
            }
            last = focus;
            if (focus != 0) {
                List<Integer> ordered = new ArrayList<>();
                for (UnitView u : units(h, 0)) {
                    int t = target(h, u);
                    if (t != 0) {
                        ordered.add(t);
                    }
                }
                if (ordered.size() >= 2 && ordered.stream().distinct().count() == 1) {
                    shared++;
                }
            }
        }
        assertThat(wrong).isEmpty();
        assertThat(changes).as("targets picked").isGreaterThanOrEqualTo(2);
        assertThat(shared).as("ticks the line shared one target").isGreaterThan(50);
        assertThat(bot(h).retreats()).as("a fight it leads is not fled").isZero();
        assertThat(units(h, 1)).as("the line wins").isEmpty();

        // retreat
        SimHarness r = ground(TemplateSet.builtin());
        spawn(r, SPEAR, 0, 30, 30, 6);
        spawn(r, SPEAR, 1, 62, 62, 14);
        r.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "battle", script(4, 0, TACTICS)));
        long retreatAt = -1;
        boolean attackedAfter = false;
        while (r.tick() < 3_000 && !units(r, 0).isEmpty()) {
            r.step();
            if (retreatAt < 0 && bot(r).retreats() > 0) {
                retreatAt = r.tick();
            } else if (retreatAt >= 0 && bot(r).fight() == 2) {
                for (UnitView u : units(r, 0)) {
                    attackedAfter |= target(r, u) != 0;
                }
            }
            if (retreatAt >= 0 && bot(r).fight() == 0) {
                break;
            }
        }
        assertThat(retreatAt).as("the tick it retreated").isPositive();
        assertThat(bot(r).retreats()).isEqualTo(1);
        assertThat(attackedAfter).as("no attack order while going home").isFalse();
        assertThat(units(r, 0)).as("some came home").isNotEmpty();
        var footprint = r.core().queries().buildings().stream()
                .filter(b -> b.faction() == 0 && b.template().equals(CENTRE))
                .findFirst()
                .orElseThrow()
                .footprint();
        double cx = footprint.anchorX() + footprint.spanX() / 2.0;
        double cz = footprint.anchorZ() + footprint.spanZ() / 2.0;
        for (UnitView u : units(r, 0)) {
            assertThat(DetMath.length(u.x() - cx, u.z() - cz))
                    .as("home at tick %d", r.tick())
                    .isLessThanOrEqualTo(12);
        }
    }

    /** AI-03's ranged rule: while the line fights, the archers stand nearer the base than the line's middle. */
    @Test
    @Tag("slow")
    void archersStandBehindTheLine() {
        SimHarness h = ground(TemplateSet.builtin());
        spawn(h, SPEAR, 0, 30, 30, 8);
        spawn(h, ARCHER, 0, 30, 30, 4);
        spawn(h, SPEAR, 1, 62, 62, 6);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "battle", script(4, 0, TACTICS)));
        int fighting = 0;
        int behind = 0;
        while (h.tick() < 2_000 && !units(h, 1).isEmpty()) {
            h.step();
            double line = 0;
            double rear = 0;
            int lines = 0;
            int rears = 0;
            for (UnitView u : units(h, 0)) {
                double d = DetMath.length(u.x() - 10, u.z() - 10);
                if (u.template().equals(ARCHER)) {
                    rear += d;
                    rears++;
                } else {
                    line += d;
                    lines++;
                }
            }
            boolean engaged = bot(h).focus() != 0 && lines > 0 && rears > 0;
            if (engaged && h.tick() % 10 == 0) {
                fighting++;
                behind += rear / rears < line / lines ? 1 : 0;
            }
        }
        assertThat(fighting).as("samples in the fight").isGreaterThan(10);
        assertThat(behind).as("samples with the archers nearer the base").isGreaterThanOrEqualTo(fighting * 9 / 10);
    }

    static double fraction(SimHarness h, UnitView u) {
        return h.core().queries().hitPoints(u.handle()) / h.core().queries().maxHitPoints(u.handle());
    }

    /** Own soldiers by role, queued ones too. */
    static int[] roles(SimHarness h, TemplateSet set) {
        int[] have = new int[3];
        for (UnitView u : units(h, 0)) {
            count(set, u.templateIndex(), have);
        }
        for (BuildingView b : h.core().queries().buildings()) {
            if (b.faction() == 0) {
                b.queue().forEach(t -> count(set, t, have));
            }
        }
        return have;
    }

    private static void count(TemplateSet set, int index, int[] have) {
        TemplateId id = set.byIndex(index).id();
        if (id.equals(SPEAR)) {
            have[0]++;
        } else if (id.equals(ARCHER)) {
            have[1]++;
        } else if (id.equals(HORSE)) {
            have[2]++;
        }
    }

    /**
     * AI-03.2: three barracks, as shipped, train what they like. Against spearmen the plan wants archers behind a line of a quarter
     * spearmen; a minute after twenty horsemen join the enemy it wants spearmen, and what it trains in the two minutes
     * after the horsemen come is spearmen.
     */
    @Test
    @Tag("slow")
    void shiftsToSpearmenAgainstHorse() {
        TemplateSet set = TemplateSet.builtin(); // the shipped barracks trains the roster
        SimHarness h = ground(set);
        for (int k = 0; k < 3; k++) {
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(BARRACKS, new FactionSlot(0), 24 + 12 * k, 8, 0));
        }
        for (int k = 0; k < 14; k++) { // room for 90 soldiers
            h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(
                            new TemplateId("strategos:building/house"),
                            new FactionSlot(0),
                            8 + 8 * (k % 7),
                            40 + 8 * (k / 7),
                            0));
        }
        for (String r : new String[] {"food", "wood", "iron", "stone"}) {
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), r, 20_000));
        }
        spawn(h, SPEAR, 1, 70, 70, 12);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "battle", script(1_000, 2, TACTICS)));
        h.runTicks(1_190);
        int[] before = roles(h, set);
        assertThat(bot(h).want()).as("against spearmen").containsExactly(250, 750, 0);
        assertThat(before[1])
                .as("archers trained %s", java.util.Arrays.toString(before))
                .isGreaterThan(before[0]);
        spawn(h, HORSE, 1, 80, 80, 20);
        h.runTicks(1_200 + 20);
        assertThat(bot(h).want()).as("against 12 spearmen and 20 horsemen").containsExactly(719, 281, 0);
        int[] mid = roles(h, set);
        h.runTicks(2_400 - 1_200);
        int[] after = roles(h, set);
        int spears = after[0] - mid[0];
        int others = after[1] + after[2] - mid[1] - mid[2];
        System.out.println("BattleBotTest.shiftsToSpearmenAgainstHorse: before the horsemen "
                + java.util.Arrays.toString(before) + ", at minute 2 " + java.util.Arrays.toString(mid)
                + ", at minute 3 " + java.util.Arrays.toString(after));
        assertThat(spears)
                .as(
                        "trained in the two minutes since the horsemen came: %s then %s",
                        java.util.Arrays.toString(mid), java.util.Arrays.toString(after))
                .isGreaterThan(others);
    }

    /** AI-03.3: with a big armies' fight on, no think spends past the budget; the spend is reported. */
    @Test
    void withinTheBudget() {
        SimHarness h = ground(TemplateSet.builtin());
        spawn(h, SPEAR, 0, 30, 30, 60);
        spawn(h, SPEAR, 1, 50, 50, 60);
        spawn(h, ARCHER, 0, 24, 24, 20);
        spawn(h, HORSE, 1, 56, 56, 20);
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "battle", script(4, 0, TACTICS)));
        int most = 0;
        long thinks = 0;
        long sum = 0;
        long fights = 0;
        while (h.tick() < 2_400) {
            h.step();
            ComputerPlayer p = bot(h);
            if (h.tick() % 20 == 0 || p.lastSpent() > 0) {
                most = Math.max(most, p.lastSpent());
            }
            if (p.fight() != 0) {
                fights++;
            }
            sum += p.lastSpent();
            thinks++;
        }
        assertThat(fights).as("a wave was out").isPositive();
        assertThat(most)
                .as("the most a think spent (of %d)", AiBudget.CAP)
                .isPositive()
                .isLessThan(AiBudget.CAP);
        System.out.println("BattleBotTest.withinTheBudget: most " + most + " of " + AiBudget.CAP + " in " + thinks
                + " ticks, " + units(h, 0).size() + " + " + units(h, 1).size() + " units left");
        assertThat(sum).isPositive();
    }

    @Test
    void theShippedScriptIsBattleTactics() {
        StrategyScript s = StrategyScript.parse(HeadlessMatch.read("ai/battle.json"));
        assertThat(s.battle().retreat()).isEqualTo(0.6);
        assertThat(s.battle().enabled()).isTrue();
        assertThat(StrategyScript.parse(HeadlessMatch.read("ai/economy.json")).battle())
                .as("M4's script plays as it did")
                .isEqualTo(StrategyScript.Battle.NONE);
        assertThat(JsonParser.parseString(HeadlessMatch.read("ai/battle.json"))
                        .getAsJsonObject()
                        .has("battle"))
                .isTrue();
    }
}
