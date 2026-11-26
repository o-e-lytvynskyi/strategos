package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Guard;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ORD-07.2 in L1: a guard follows a charge that walks away and stands beside it once it stops; defends it against an
 * attacker (the charge itself never fights back, as a debug walker does), then returns to it; its order ends when the
 * charge dies; a charge that is an enemy, a stranger's handle or nothing is refused, and a building can be guarded.
 */
class GuardTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final int SOURCE = 11;

    private static int[] spawn(SimHarness h, TemplateId template, FactionSlot faction, Vec2d at, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, at, count, 1));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    private static double dist(SimHarness h, int a, int b) {
        return Math.hypot(
                state(h).motion().x(Handle.index(a)) - state(h).motion().x(Handle.index(b)),
                state(h).motion().z(Handle.index(a)) - state(h).motion().z(Handle.index(b)));
    }

    private static int kind(SimHarness h, int unit) {
        return state(h).orders().kind(Handle.index(unit));
    }

    private static CommandResult lastResult(SimHarness h) {
        List<CommandResult> all = h.results();
        return all.get(all.size() - 1);
    }

    /** A charge that outlasts any fight; {@code template} says whether it fights back and how fast it walks. */
    private static int charge(SimHarness h, TemplateId template, Vec2d at) {
        int c = spawn(h, template, F0, at, 1)[0];
        state(h).stats().addPersonal(Handle.index(c), Modifier.replace(CorePaths.HEALTH, 100_000, SOURCE));
        return c;
    }

    @Test
    void aGuardFollowsAChargeThatWalksAwayAndStandsBesideItOnceItStops() {
        SimHarness h = SimHarness.builder().size(120, 32).build();
        int c = charge(h, TemplateSet.INFANTRY, new Vec2d(10, 16)); // as fast as its guards
        int[] guards = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(6, 16), 2);
        h.submit(P0, Guard.replace(UnitSet.of(guards), c));
        h.submit(P0, Move.replace(UnitSet.of(c), new Vec2d(100, 16)));
        double most = 0;
        for (int t = 0; t < 1_200; t++) {
            h.step();
            if (t > 150 && t < 400) {
                for (int g : guards) {
                    most = Math.max(most, dist(h, g, c));
                }
            }
        }

        assertThat(state(h).motion().x(Handle.index(c)))
                .as("the charge arrived")
                .isGreaterThan(95);
        assertThat(most).as("never left far behind while it walked").isLessThan(12);
        for (int g : guards) {
            assertThat(dist(h, g, c)).as("beside it once it stands").isLessThan(Guarding.CLOSE + Guarding.SLACK + 1);
            assertThat(kind(h, g)).as("the order stands").isEqualTo(OrderKind.GUARD);
            assertThat(state(h).orders().target(Handle.index(g))).isEqualTo(c);
            assertThat(UnitAi.leaf(state(h).ai().code(Handle.index(g)))).isEqualTo(UnitAi.GUARD_STANDING);
        }
    }

    /** The attacker is engaged (an engagement before the guard order), killed, and the guard goes back to the charge. */
    @Test
    void aGuardDefendsItsChargeThenReturnsToIt() {
        SimHarness h = SimHarness.builder().size(120, 64).build();
        int c = charge(h, TemplateSet.DEBUG_WALKER, new Vec2d(30, 32));
        int[] guards = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(26, 32), 3);
        h.submit(P0, Guard.replace(UnitSet.of(guards), c));
        h.runTicks(60);
        int[] raiders = spawn(h, TemplateSet.INFANTRY, F1, new Vec2d(52, 32), 1);
        h.submit(P1, Attack.replace(UnitSet.of(raiders), c));
        boolean engaged = false;
        for (int t = 0; t < 1_500 && h.queries().unit(raiders[0]) != null; t++) {
            h.step();
            for (int g : guards) {
                engaged |= kind(h, g) == OrderKind.ENGAGE;
            }
        }

        assertThat(engaged).as("a guard took the attacker on").isTrue();
        assertThat(h.queries().unit(raiders[0])).as("the attacker died").isNull();
        h.runTicks(300);
        for (int g : guards) {
            assertThat(kind(h, g)).as("back on guard").isEqualTo(OrderKind.GUARD);
            assertThat(state(h).orders().size(Handle.index(g))).isEqualTo(1);
            assertThat(dist(h, g, c)).as("back beside the charge").isLessThan(Guarding.CLOSE + Guarding.SLACK + 1);
        }
        assertThat(h.queries().unit(c)).as("the charge lived").isNotNull();
    }

    @Test
    void anEnemyFarFromTheChargeIsNoneOfTheGuardsBusiness() {
        SimHarness h = SimHarness.builder().size(120, 64).build();
        int c = charge(h, TemplateSet.DEBUG_WALKER, new Vec2d(30, 32));
        int[] guards = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(28, 32), 1);
        int[] far = spawn(h, TemplateSet.DEBUG_WALKER, F1, new Vec2d(100, 32), 1);
        h.submit(P0, Guard.replace(UnitSet.of(guards), c));
        h.runTicks(300);

        assertThat(kind(h, guards[0])).isEqualTo(OrderKind.GUARD);
        assertThat(h.queries().unit(far[0])).as("left alone").isNotNull();
        assertThat(dist(h, guards[0], c)).isLessThan(Guarding.CLOSE + Guarding.SLACK + 1);
    }

    @Test
    void theOrderEndsWhenTheChargeDies() {
        SimHarness h = SimHarness.builder().size(120, 32).build();
        int c = spawn(h, TemplateSet.DEBUG_WALKER, F0, new Vec2d(30, 16), 1)[0];
        int[] guards = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(26, 16), 2);
        h.submit(P0, Guard.replace(UnitSet.of(guards), c));
        h.runTicks(80);
        assertThat(kind(h, guards[0])).isEqualTo(OrderKind.GUARD);

        h.damage(new ExternalDamage(c, ExternalDamage.Kind.LETHAL, 1_000, ExternalDamage.SourceKind.NONE));
        h.runTicks(30);

        assertThat(h.queries().unit(c)).isNull();
        for (int g : guards) {
            assertThat(kind(h, g)).as("done").isEqualTo(OrderKind.NONE);
            assertThat(UnitAi.leaf(state(h).ai().code(Handle.index(g)))).isEqualTo(UnitAi.IDLE);
        }
    }

    @Test
    void anEnemyChargeAnUnknownOneAndOneselfAreRefused() {
        SimHarness h = SimHarness.builder().size(120, 32).build();
        int[] mine = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(20, 16), 2);
        int[] theirs = spawn(h, TemplateSet.INFANTRY, F1, new Vec2d(60, 16), 1);

        h.submit(P0, Guard.replace(UnitSet.of(mine), theirs[0]));
        h.step();
        assertThat(lastResult(h).firstReason()).as("guarding an enemy").isEqualTo(RejectReason.INVALID_ARGUMENT);
        h.submit(P0, Guard.replace(UnitSet.of(mine), Handle.of(500, 3)));
        h.step();
        assertThat(lastResult(h).firstReason()).as("no such unit").isEqualTo(RejectReason.UNKNOWN_TARGET);
        h.submit(P0, Guard.replace(UnitSet.of(mine[0]), mine[0]));
        h.step();
        assertThat(lastResult(h).firstReason()).as("oneself").isEqualTo(RejectReason.INVALID_ARGUMENT);
        h.submit(P1, Guard.replace(UnitSet.of(mine), mine[0]));
        h.step();
        assertThat(lastResult(h).firstReason())
                .as("another's units")
                .isIn(RejectReason.NOT_OWNER, RejectReason.UNKNOWN_TARGET);
        for (int u : mine) {
            assertThat(kind(h, u)).isEqualTo(OrderKind.NONE);
        }
    }

    @Test
    void aGuardStandsBesideABuildingAndDefendsIt() {
        SimHarness h = SimHarness.builder().size(120, 64).build();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(new TemplateId("strategos:building/house"), F0, 40, 30, 0));
        h.step();
        int house = state(h).buildings().handles()[0];
        int[] guards = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(20, 32), 2);
        h.submit(P0, Guard.replace(UnitSet.of(guards), house));
        h.runTicks(400);

        double[] at = dev.strategos.core.sim.order.Charges.centre(state(h), house);
        for (int g : guards) {
            assertThat(kind(h, g)).isEqualTo(OrderKind.GUARD);
            assertThat(Math.hypot(
                            state(h).motion().x(Handle.index(g)) - at[0],
                            state(h).motion().z(Handle.index(g)) - at[1]))
                    .as("beside the house")
                    .isLessThan(12);
        }
        int[] raider = spawn(h, TemplateSet.INFANTRY, F1, new Vec2d(12, 32), 1); // on the guards' side of the house
        h.submit(P1, Attack.replace(UnitSet.of(raider), house));
        h.runTicks(900);
        assertThat(h.queries().unit(raider[0]))
                .as("the raider fell to the guards")
                .isNull();
    }
}
