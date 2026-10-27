package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Attacked;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * CMB-05 in L1 (M5 §2.4): the five stances on the bundled spearman (reach 1.2, sight 12) against the debug walker,
 * which never fights back and so stays where it is put; the preferred classes of an attack; and a chase that ends
 * when its target walks out of sight.
 */
class StanceTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final double X = 24.5;
    private static final double Z = 16.5;

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    private static int spawn(SimHarness h, TemplateId template, FactionSlot faction, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private static int spearman(SimHarness h, @Nullable Stance stance) {
        int unit = spawn(h, TemplateSet.INFANTRY, F0, X, Z);
        if (stance != null) {
            h.submit(P0, new SetStance(UnitSet.of(unit), stance));
            h.step();
        }
        return unit;
    }

    private static void put(SimHarness h, int unit, double x, double z) {
        state(h).motion().set(Handle.index(unit), x, state(h).motion().y(Handle.index(unit)), z, 0);
    }

    private static double distance(SimHarness h, int unit, double x, double z) {
        int i = Handle.index(unit);
        return DetMath.length(state(h).motion().x(i) - x, state(h).motion().z(i) - z);
    }

    private static int orderKind(SimHarness h, int unit) {
        return state(h).orders().kind(Handle.index(unit));
    }

    private static boolean engaged(SimHarness h, int unit) {
        return state(h).orders().size(Handle.index(unit)) > 0 && orderKind(h, unit) == OrderKind.ENGAGE;
    }

    private static boolean idleAt(SimHarness h, int unit, double x, double z) {
        return state(h).orders().size(Handle.index(unit)) == 0 && distance(h, unit, x, z) < 1.5;
    }

    @ParameterizedTest
    @EnumSource(Stance.class)
    void theTable(Stance stance) {
        switch (stance) {
            case VIOLENT, AGGRESSIVE -> violentAndAggressiveEngageWhatTheySeeAndHitBack(stance);
            case DEFENSIVE -> defensiveGuardsItsSpotAndReturnsToIt();
            case STAND_GROUND -> standGroundNeverMoves();
            case PASSIVE -> passiveNeverEngagesAndRuns();
        }
    }

    @Test
    void aTemplateThatSaysNothingIsAggressive() {
        violentAndAggressiveEngageWhatTheySeeAndHitBack(null);
    }

    private void violentAndAggressiveEngageWhatTheySeeAndHitBack(@Nullable Stance stance) {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int unit = spearman(h, stance);
        int far = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 11, Z);
        assertThat(h.runUntil(() -> engaged(h, unit), 40))
                .as(stance + " engages what it sees")
                .isTrue();
        assertThat(state(h).orders().target(Handle.index(unit))).isEqualTo(far);
        state(h).orders().clear(Handle.index(unit));
        state(h).ai().set(Handle.index(unit), UnitAi.IDLE);
        put(h, far, X + 40, Z - 14); // out of sight again
        put(h, unit, X, Z);
        h.runTicks(2);
        int striker = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 30, Z);
        state(h).bus().publish(new Attacked(unit, striker));
        assertThat(orderKind(h, unit)).as(stance + " hits back, however far").isEqualTo(OrderKind.ENGAGE);
    }

    private void defensiveGuardsItsSpotAndReturnsToIt() {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int unit = spearman(h, Stance.DEFENSIVE);
        int enemy = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 10, Z); // in sight, beyond 1.2 + 12 / 2
        h.runTicks(40);
        assertThat(state(h).orders().size(Handle.index(unit)))
                .as("defensive holds back")
                .isZero();
        assertThat(distance(h, unit, X, Z)).isLessThan(0.01);
        state(h).bus().publish(new Attacked(unit, enemy));
        assertThat(state(h).orders().size(Handle.index(unit)))
                .as("it does not hit back at one beyond its radius")
                .isZero();
        put(h, enemy, X + 5, Z);
        assertThat(h.runUntil(() -> engaged(h, unit), 40))
                .as("within the radius it engages")
                .isTrue();
        assertThat(h.runUntil(() -> distance(h, unit, X, Z) > 2, 80)).isTrue();
        put(h, enemy, X + 12, Z); // sight keeps it, the radius lets it go
        assertThat(h.runUntil(() -> idleAt(h, unit, X, Z), 200))
                .as("it gives the chase up and walks back to its spot")
                .isTrue();
        h.runTicks(40);
        assertThat(idleAt(h, unit, X, Z)).as("and stays there").isTrue();
        int near = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 4, Z);
        state(h).bus().publish(new Attacked(unit, near));
        assertThat(orderKind(h, unit))
                .as("it hits back at one within its radius")
                .isEqualTo(OrderKind.ENGAGE);
    }

    private void standGroundNeverMoves() {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int unit = spearman(h, Stance.STAND_GROUND);
        int enemy = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 4, Z);
        h.runTicks(60);
        assertThat(state(h).orders().size(Handle.index(unit)))
                .as("out of reach: no engagement")
                .isZero();
        state(h).bus().publish(new Attacked(unit, enemy));
        assertThat(state(h).orders().size(Handle.index(unit)))
                .as("no answer to a blow out of reach")
                .isZero();
        put(h, enemy, X + 1.5, Z); // edges 0.9 apart: in reach
        assertThat(h.runUntil(
                        () -> UnitAi.ATTACKING.equals(UnitAi.leaf(state(h).ai().code(Handle.index(unit)))), 40))
                .as("in reach it fights")
                .isTrue();
        put(h, enemy, X + 4, Z);
        assertThat(h.runUntil(() -> state(h).orders().size(Handle.index(unit)) == 0, 20))
                .as("the target steps away and is let go, not followed")
                .isTrue();
        h.runTicks(40);
        assertThat(distance(h, unit, X, Z)).as("it never moved").isLessThan(0.01);
        h.submit(P0, Attack.replace(UnitSet.of(unit), enemy));
        h.runTicks(30);
        assertThat(distance(h, unit, X, Z))
                .as("an order of the player's is obeyed")
                .isGreaterThan(1);
    }

    private void passiveNeverEngagesAndRuns() {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int unit = spearman(h, Stance.PASSIVE);
        int enemy = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 2, Z);
        h.runTicks(40);
        assertThat(state(h).orders().size(Handle.index(unit)))
                .as("passive never engages")
                .isZero();
        state(h).bus().publish(new Attacked(unit, enemy));
        assertThat(orderKind(h, unit)).isEqualTo(OrderKind.FLEE);
        h.runTicks(160);
        assertThat(distance(h, unit, X, Z)).as("twelve blocks from the blow").isBetween(11.0, 13.0);
        assertThat(state(h).stances().explicit(Handle.index(unit))).isEqualTo(Stance.PASSIVE);
    }

    private static final TemplateId BRUTE = new TemplateId("strategos:unit/a_brute");
    private static final TemplateId PIKE = new TemplateId("strategos:unit/b_pike");
    private static final TemplateId ARCHER = new TemplateId("strategos:unit/c_archer");
    private static final TemplateId RIDER = new TemplateId("strategos:unit/d_rider");

    private static TemplateSet preferring(String preferred) {
        String body = """
                {"Identity":{"classes":"%s"},"UnitMotion":{"walkSpeed":0.25},
                 "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}}
                """;
        JsonObject brute = JsonParser.parseString(body.formatted("Infantry")).getAsJsonObject();
        JsonObject melee = new JsonObject();
        melee.addProperty("hack", 1);
        if (!preferred.isEmpty()) {
            melee.addProperty("preferred", preferred);
        }
        JsonObject attack = new JsonObject();
        attack.add("Melee", melee);
        brute.add("Attack", attack);
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(
                new RawTemplate(BRUTE, "brute.json", brute),
                new RawTemplate(
                        PIKE,
                        "pike.json",
                        JsonParser.parseString(body.formatted("Infantry")).getAsJsonObject()),
                new RawTemplate(
                        ARCHER,
                        "archer.json",
                        JsonParser.parseString(body.formatted("Archer Infantry"))
                                .getAsJsonObject()),
                new RawTemplate(
                        RIDER,
                        "rider.json",
                        JsonParser.parseString(body.formatted("Cavalry")).getAsJsonObject())));
        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        return result.set();
    }

    private static int chosen(String preferred, boolean archer, boolean rider) {
        SimHarness h = SimHarness.builder()
                .size(96, 32)
                .templates(preferring(preferred))
                .build();
        int brute = spawn(h, BRUTE, F0, X, Z);
        int pike = spawn(h, PIKE, F1, X + 3, Z);
        int far = archer ? spawn(h, ARCHER, F1, X, Z + 9) : 0;
        int horse = rider ? spawn(h, RIDER, F1, X - 6, Z) : 0;
        assertThat(h.runUntil(() -> engaged(h, brute), 40)).isTrue();
        int target = state(h).orders().target(Handle.index(brute));
        assertThat(target).isIn(pike, far, horse);
        return target == pike ? 0 : target == far ? 1 : 2;
    }

    @Test
    void targetPreferences() {
        assertThat(chosen("", true, true)).as("no preference: the nearest").isZero();
        assertThat(chosen("Archer", true, false))
                .as("a preferred class beats a nearer one")
                .isEqualTo(1);
        assertThat(chosen("Archer Cavalry", true, true))
                .as("the first class of the list that anyone belongs to")
                .isEqualTo(1);
        assertThat(chosen("Cavalry Archer", true, true)).isEqualTo(2);
        assertThat(chosen("Cavalry", true, false))
                .as("no candidate of the class: the nearest")
                .isZero();
    }

    @Test
    void aChaseEndsOutOfVision() {
        // under its own engagement
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int unit = spearman(h, null);
        int enemy = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 8, Z);
        assertThat(h.runUntil(() -> engaged(h, unit), 40)).isTrue();
        put(h, enemy, X + 40, Z);
        assertThat(h.runUntil(() -> state(h).orders().size(Handle.index(unit)) == 0, 30))
                .as("an engagement ends when its target leaves sight")
                .isTrue();
        // and under the player's attack
        SimHarness a = SimHarness.builder().size(96, 32).build();
        int hunter = spearman(a, null);
        int prey = spawn(a, TemplateSet.DEBUG_WALKER, F1, X + 8, Z);
        a.submit(P0, Attack.replace(UnitSet.of(hunter), prey));
        assertThat(a.runUntil(
                        () -> UnitAi.ATTACKING.equals(UnitAi.leaf(state(a).ai().code(Handle.index(hunter)))), 80))
                .isTrue();
        put(a, prey, X + 40, Z);
        assertThat(a.runUntil(() -> state(a).orders().size(Handle.index(hunter)) == 0, 30))
                .as("an attack ordered ends when its target walks out of sight")
                .isTrue();
        assertThat(a.stateChanges()).isNotEmpty();
        // a far target ordered is walked to, not given up before the chase begins
        SimHarness b = SimHarness.builder().size(96, 48).build();
        int walker = spearman(b, null);
        int distant = spawn(b, TemplateSet.DEBUG_WALKER, F1, X + 25, Z);
        b.submit(P0, Attack.replace(UnitSet.of(walker), distant));
        b.runTicks(30);
        assertThat(orderKind(b, walker)).isEqualTo(OrderKind.ATTACK);
        assertThat(distance(b, walker, X, Z)).isGreaterThan(3);
    }

    private static final TemplateId BOWMAN = new TemplateId("strategos:unit/e_bowman");

    /** An archer: range 20 (reach 19.5 as it takes its place), sight 32, so reach + sight / 2 is past sight. */
    private static TemplateSet bowmen() {
        JsonObject bow = JsonParser.parseString("""
                {"Identity":{},"UnitMotion":{"walkSpeed":0.25},"Vision":{"range":32},
                 "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8},
                 "Attack":{"Ranged":{"pierce":0.01,"range":20,"prepare":2,"repeat":10,"speed":2,"spread":0}}}
                """).getAsJsonObject();
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(
                new RawTemplate(BOWMAN, "bowman.json", bow),
                new RawTemplate(
                        PIKE,
                        "pike.json",
                        JsonParser.parseString(
                                        "{\"Identity\":{},\"UnitMotion\":{\"walkSpeed\":0.25},\"Body\":{\"entityType\":\"strategos:unit_humanoid\",\"width\":0.6,\"height\":1.8}}")
                                .getAsJsonObject())));
        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        return result.set();
    }

    /** How many engagements the unit started over {@code ticks}: an order appearing where there was none. */
    private static int engagementsStarted(SimHarness h, int unit, int ticks) {
        int started = 0;
        boolean had = state(h).orders().size(Handle.index(unit)) > 0;
        for (int k = 0; k < ticks; k++) {
            h.step();
            boolean has = state(h).orders().size(Handle.index(unit)) > 0;
            if (has && !had) started++;
            had = has;
        }
        return started;
    }

    @Test
    void anArchersNoticeStopsAtItsSightSoItNeverEngagesAndDropsInTurn() {
        SimHarness h = SimHarness.builder().size(128, 64).templates(bowmen()).build();
        int archer = spawn(h, BOWMAN, F0, X, Z);
        h.submit(P0, new SetStance(UnitSet.of(archer), Stance.DEFENSIVE));
        int target = spawn(h, PIKE, F1, X + 25, Z);
        assertThat(h.runUntil(() -> engaged(h, archer), 30))
                .as("a target inside its radius")
                .isTrue();
        put(h, target, X + 34, Z); // past sight 32, inside reach + sight / 2
        assertThat(engagementsStarted(h, archer, 80))
                .as("no engage-and-drop every think")
                .isZero();
        assertThat(state(h).orders().size(Handle.index(archer))).isZero();
        SimHarness far = SimHarness.builder().size(128, 64).templates(bowmen()).build();
        int guard = spawn(far, BOWMAN, F0, X, Z);
        far.submit(P0, new SetStance(UnitSet.of(guard), Stance.DEFENSIVE));
        spawn(far, PIKE, F1, X + 34, Z);
        assertThat(engagementsStarted(far, guard, 80))
                .as("outside sight it holds")
                .isZero();
    }

    @Test
    void aStandGroundArcherFiresInsideItsReachAndIgnoresWhatIsOutside() {
        SimHarness h = SimHarness.builder().size(128, 64).templates(bowmen()).build();
        int archer = spawn(h, BOWMAN, F0, X, Z);
        h.submit(P0, new SetStance(UnitSet.of(archer), Stance.STAND_GROUND));
        int target = spawn(h, PIKE, F1, X + 25, Z);
        assertThat(engagementsStarted(h, archer, 60))
                .as("in sight, beyond its reach")
                .isZero();
        put(h, target, X + 15, Z);
        assertThat(h.runUntil(() -> engaged(h, archer), 30))
                .as("inside its reach")
                .isTrue();
        put(h, target, X + 34, Z);
        assertThat(h.runUntil(() -> state(h).orders().size(Handle.index(archer)) == 0, 20))
                .isTrue();
        assertThat(engagementsStarted(h, archer, 80)).isZero();
        assertThat(distance(h, archer, X, Z)).as("it never moved").isLessThan(0.01);
    }

    @Test
    void aDefensiveUnitDoesNotChainKillsPastItsLeash() {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int unit = spearman(h, Stance.DEFENSIVE);
        int first = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 6, Z); // inside 1.2 + 12 / 2 of the spot
        int second = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 12, Z); // outside it, near where the first stands
        assertThat(h.runUntil(() -> engaged(h, unit) && distance(h, unit, X, Z) > 3, 80))
                .isTrue();
        state(h).removeUnit(first);
        boolean tookSecond = false;
        for (int k = 0; k < 120; k++) {
            h.step();
            tookSecond |= engaged(h, unit) && state(h).orders().target(Handle.index(unit)) == second;
        }
        assertThat(tookSecond)
                .as("the next target is looked for round the spot")
                .isFalse();
        assertThat(idleAt(h, unit, X, Z)).as("and it walks home").isTrue();
    }

    @Test
    void aPassiveWorkerRunsItsTemplatesEightBlocks() {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int worker = spawn(h, TemplateSet.WORKER, F0, X, Z);
        int enemy = spawn(h, TemplateSet.DEBUG_WALKER, F1, X + 2, Z);
        assertThat(state(h).stats().stance(Handle.index(worker))).isEqualTo(Stance.PASSIVE);
        state(h).bus().publish(new Attacked(worker, enemy));
        assertThat(orderKind(h, worker)).isEqualTo(OrderKind.FLEE);
        h.runTicks(120);
        assertThat(distance(h, worker, X, Z)).isBetween(7.0, 9.0);
    }
}
