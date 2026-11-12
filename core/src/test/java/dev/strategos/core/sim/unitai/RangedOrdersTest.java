package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

class RangedOrdersTest {
    static final TemplateId RANGER = new TemplateId("strategos:unit/a_ranger");
    static final TemplateId DUMMY = new TemplateId("strategos:unit/z_dummy");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);

    @Test
    void rangedOnlyAttackerApproachesWithoutMeleeSlotThenFiresOnWindupAndPeriod() {
        SimHarness h = harness(12, 2, 4, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 20.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(ranger), dummy));
        h.step();
        assertThat(s.orders().kind(Handle.index(ranger))).isEqualTo(OrderKind.ATTACK);
        assertThat(s.orders().slot(Handle.index(ranger))).isEqualTo(-1);
        assertThat(s.motion().x(Handle.index(ranger))).isGreaterThan(5.5);
        assertThat(s.shots().count()).isZero();
        assertThat(h.runUntil(() -> UnitAi.leaf(s.ai().code(Handle.index(ranger))) == UnitAi.ATTACKING, 80))
                .isTrue();
        long entered = h.tick();
        h.step();
        assertThat(h.events(UnitStruck.class)).isEmpty();
        h.step();
        assertThat(h.events(UnitStruck.class)).hasSize(1).contains(new UnitStruck(ranger, dummy));
        assertThat(h.tick()).isEqualTo(entered + 2);
        long first = h.tick();
        while (h.tick() < first + 4) h.step();
        assertThat(h.events(UnitStruck.class)).hasSize(2);
    }

    @Test
    void rangedAttackerChasesARecedingTargetWithoutTakingARingSlot() {
        SimHarness h = harness(8, 1, 20, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 11.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(ranger), dummy));
        h.runTicks(3);
        assertThat(UnitAi.leaf(s.ai().code(Handle.index(ranger)))).isEqualTo(UnitAi.ATTACKING);
        s.motion().set(Handle.index(dummy), 16.5, 1, 10.5, 0); // out of range, still in sight (a chase ends beyond it)
        h.runTicks(6);
        assertThat(s.orders().slot(Handle.index(ranger))).isEqualTo(-1);
        assertThat(s.motion().x(Handle.index(ranger))).isGreaterThan(5.5);
        assertThat(UnitAi.leaf(s.ai().code(Handle.index(ranger)))).isIn(UnitAi.CHASING, UnitAi.APPROACHING);
    }

    @Test
    void rangedUnitEngagesFromAttackMoveAndRetaliatesWhenIdle() {
        SimHarness h = harness(12, 1, 20, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 12.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        h.submit(new PlayerSlot(0), AttackMove.replace(UnitSet.of(ranger), new Vec2d(25.5, 10.5)));
        h.runTicks(7);
        assertThat(s.orders().kind(Handle.index(ranger))).isEqualTo(OrderKind.ENGAGE);
        assertThat(s.orders().slot(Handle.index(ranger))).isEqualTo(-1);
        s.orders().clear(Handle.index(ranger));
        s.ai().set(Handle.index(ranger), UnitAi.IDLE);
        s.bus().publish(new dev.strategos.core.sim.Attacked(ranger, dummy));
        assertThat(s.orders().kind(Handle.index(ranger))).isEqualTo(OrderKind.ENGAGE);
    }

    @Test
    void automaticEngagementEndsOutsideVisionEvenWhileWithinWeaponRange() {
        SimHarness h = harness(20, 2, 4, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 13.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        assertThat(h.runUntil(() -> s.orders().kind(Handle.index(ranger)) == OrderKind.ENGAGE, 20))
                .isTrue();
        assertThat(UnitAi.leaf(s.ai().code(Handle.index(ranger)))).isEqualTo(UnitAi.ATTACKING);
        s.motion().set(Handle.index(dummy), 19.5, 1, 10.5, 0); // gap 13.3: past vision 12, inside range 20
        long nextShot = s.shots().nextId();
        h.step();
        assertThat(s.orders().kind(Handle.index(ranger))).isEqualTo(OrderKind.NONE);
        assertThat(s.shots().nextId()).isEqualTo(nextShot);

        s.orders().replace(Handle.index(ranger), OrderKind.ENGAGE, 19.5, 10.5, 0, -1, dummy);
        s.ai().set(Handle.index(ranger), UnitAi.CHASING);
        h.runTicks(5);
        assertThat(s.orders().kind(Handle.index(ranger))).isEqualTo(OrderKind.NONE);
        assertThat(s.shots().nextId()).isEqualTo(nextShot);
    }

    @Test
    void rangedChaseRegoalsAfterSubBlockTargetMovement() {
        SimHarness h = harness(8, 1, 20, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 15.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        int i = Handle.index(ranger);
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(ranger), dummy));
        assertThat(h.runUntil(() -> UnitAi.leaf(s.ai().code(i)) == UnitAi.ATTACKING, 80))
                .isTrue();
        double oldGoal = s.orders().targetX(i);
        double oldX = s.motion().x(i);
        s.motion().set(Handle.index(dummy), 16.4, 1, 10.5, 0); // moved 0.9, outside reach
        assertThat(h.runUntil(() -> UnitAi.leaf(s.ai().code(i)) == UnitAi.CHASING, 4))
                .isTrue();
        assertThat(h.runUntil(() -> s.orders().targetX(i) > oldGoal + 0.5, 12)).isTrue();
        assertThat(h.runUntil(() -> UnitAi.leaf(s.ai().code(i)) == UnitAi.ATTACKING, 30))
                .isTrue();
        assertThat(s.motion().x(i)).isGreaterThan(oldX);
    }

    @Test
    void switchingFromMeleeToRangedRetakesSlotAndFullRangedWindup() {
        SimHarness h = harness(12, 6, 20, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 7.0, 10.5);
        MatchState s = KernelAccess.state(h.core());
        int i = Handle.index(ranger);
        s.stats().add(F0, Modifier.replace(CorePaths.RANGED_RANGE, 0, 21));
        s.stats().add(F0, Modifier.add(CorePaths.MELEE_HACK, 1, 22));
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(ranger), dummy));
        h.step();
        assertThat(UnitAi.leaf(s.ai().code(i))).isEqualTo(UnitAi.ATTACKING);
        assertThat(s.ai().data1(i)).isZero();
        assertThat(s.orders().slot(i)).isGreaterThanOrEqualTo(0);
        s.stats().remove(F0, 21);
        s.stats().remove(F0, 22);
        h.step();
        assertThat(s.ai().data1(i)).isEqualTo(1);
        assertThat(s.ai().data0(i)).isEqualTo((int) h.tick() + 6);
        assertThat(s.orders().slot(i)).isEqualTo(-1);
        assertThat(s.shots().pristine()).isTrue();
        h.runTicks(5);
        assertThat(s.shots().pristine()).isTrue();
        h.step();
        assertThat(s.shots().nextId()).isEqualTo(2);
    }

    @Test
    void switchingFromRangedToMeleeUsesMeleePlaceAndFreshWindup() {
        SimHarness h = harness(12, 2, 20, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 7.0, 10.5);
        MatchState s = KernelAccess.state(h.core());
        int i = Handle.index(ranger);
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(ranger), dummy));
        h.step();
        assertThat(s.ai().data1(i)).isEqualTo(1);
        s.stats().add(F0, Modifier.replace(CorePaths.RANGED_RANGE, 0, 23));
        s.stats().add(F0, Modifier.add(CorePaths.MELEE_HACK, 1, 24));
        h.step();
        assertThat(s.ai().data1(i)).isZero();
        assertThat(s.ai().data0(i)).isEqualTo((int) h.tick() + 10);
        assertThat(s.orders().slot(i)).isGreaterThanOrEqualTo(0);
        assertThat(s.shots().pristine()).isTrue();
        h.runTicks(9);
        assertThat(h.events(UnitStruck.class)).isEmpty();
        h.step();
        assertThat(h.events(UnitStruck.class)).contains(new UnitStruck(ranger, dummy));
    }

    @Test
    void disablingTheOnlyWeaponWhileAttackingStopsWithoutAZeroDamageMeleeHit() {
        SimHarness h = harness(12, 2, 20, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 7.0, 10.5);
        MatchState s = KernelAccess.state(h.core());
        int i = Handle.index(ranger);
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(ranger), dummy));
        h.step();
        assertThat(s.ai().data1(i)).isEqualTo(1);
        s.stats().add(F0, Modifier.replace(CorePaths.RANGED_RANGE, 0, 25));
        h.runTicks(4);
        assertThat(s.orders().kind(i)).isEqualTo(OrderKind.NONE);
        assertThat(h.events(UnitStruck.class)).isEmpty();
        assertThat(s.shots().pristine()).isTrue();
    }

    @Test
    void disablingTheOnlyWeaponWhileApproachingStopsWithoutTakingARing() {
        SimHarness h = harness(8, 2, 20, 0);
        int ranger = spawn(h, RANGER, F0, 5.5, 10.5);
        int dummy = spawn(h, DUMMY, F1, 15.5, 10.5);
        MatchState s = KernelAccess.state(h.core());
        int i = Handle.index(ranger);
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(ranger), dummy));
        h.step();
        assertThat(UnitAi.leaf(s.ai().code(i))).isEqualTo(UnitAi.APPROACHING);
        assertThat(s.orders().slot(i)).isEqualTo(-1);
        s.stats().add(F0, Modifier.replace(CorePaths.RANGED_RANGE, 0, 26));
        h.runTicks(6);
        assertThat(s.orders().kind(i)).isEqualTo(OrderKind.NONE);
        assertThat(h.events(UnitStruck.class)).isEmpty();
        assertThat(s.shots().pristine()).isTrue();
    }

    static SimHarness harness(double range, int prepare, int repeat, double spread) {
        return SimHarness.builder()
                .size(64, 64)
                .seed(Seeds.first("canary"))
                .templates(templates(range, prepare, repeat, spread))
                .build();
    }

    static int spawn(SimHarness h, TemplateId template, FactionSlot faction, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .mapToInt(UnitCreated::handle)
                .findFirst()
                .orElseThrow();
    }

    static TemplateSet templates(double range, int prepare, int repeat, double spread) {
        String body = """
                {"Identity":{},"UnitMotion":{"walkSpeed":0.25},
                 "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}}
                """;
        JsonObject ranger = JsonParser.parseString(body).getAsJsonObject();
        JsonObject attack = new JsonObject();
        JsonObject ranged = new JsonObject();
        ranged.addProperty("pierce", 1f / 256);
        ranged.addProperty("range", range);
        ranged.addProperty("prepare", prepare);
        ranged.addProperty("repeat", repeat);
        ranged.addProperty("speed", 0.5);
        ranged.addProperty("spread", spread);
        attack.add("Ranged", ranged);
        ranger.add("Attack", attack);
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(
                new RawTemplate(RANGER, "ranger.json", ranger),
                new RawTemplate(
                        DUMMY, "dummy.json", JsonParser.parseString(body).getAsJsonObject())));
        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        return result.set();
    }
}
