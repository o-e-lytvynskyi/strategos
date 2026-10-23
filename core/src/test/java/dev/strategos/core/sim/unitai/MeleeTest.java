package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * CMB-02 in L1, on the bundled spearman (3 hack and 3 pierce a hit, reach 1.2, wind-up 10 ticks, period 20, sight 12)
 * and, as targets that never fight back, the debug walker: the rhythm of hits (CMB-02.1), places round one target
 * (CMB-02.2), the chase (CMB-02.3), and the aggressive default: engaging an enemy that comes into sight, giving it up
 * when it leaves, and hitting back (CMB-02.4).
 */
class MeleeTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final int SOURCE = 11;

    private static int[] spawn(
            SimHarness h, TemplateId template, FactionSlot faction, Vec2d at, int count, double spread) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, at, count, spread));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    private static int spearman(SimHarness h, FactionSlot faction, Vec2d at) {
        return spawn(h, TemplateSet.INFANTRY, faction, at, 1, 0)[0];
    }

    private static int dummy(SimHarness h, Vec2d at) {
        return spawn(h, TemplateSet.DEBUG_WALKER, F1, at, 1, 0)[0];
    }

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    private static StateId leaf(SimHarness h, int handle) {
        return UnitAi.leaf(state(h).ai().code(Handle.index(handle)));
    }

    /** The ticks on which {@code attacker}'s hits landed, over {@code ticks} steps. */
    private static List<Long> hits(SimHarness h, int attacker, int ticks) {
        List<Long> at = new ArrayList<>();
        for (int k = 0; k < ticks; k++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitHurt hurt && hurt.attacker() == attacker) {
                    at.add(out.tick());
                }
            }
        }
        return at;
    }

    @Test
    void hitsLandAtTheWindUpAndRepeatAtThePeriod() {
        SimHarness h = SimHarness.builder().build();
        int a = spearman(h, F0, new Vec2d(10, 16));
        int b = dummy(h, new Vec2d(11.9, 16)); // the edges 1.2 apart: in reach
        h.submit(P0, Attack.replace(UnitSet.of(a), b));
        long ordered = h.tick() + 1;
        List<UnitHurt> blows = new ArrayList<>();
        List<Long> struck = new ArrayList<>();
        List<Long> at = new ArrayList<>();
        for (int k = 0; k < 80; k++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitHurt hurt && hurt.attacker() == a) {
                    blows.add(hurt);
                    at.add(out.tick());
                } else if (e instanceof UnitStruck s && s.attacker() == a) {
                    struck.add(out.tick());
                }
            }
        }
        assertThat(at).containsExactly(ordered + 10, ordered + 30, ordered + 50, ordered + 70);
        assertThat(struck).as("the body swings with each hit").isEqualTo(at);
        assertThat(blows).allSatisfy(hurt -> {
            assertThat(hurt.handle()).isEqualTo(b);
            assertThat(hurt.amount()).as("3 hack and 3 pierce on no resistance").isEqualTo(6f);
        });
    }

    @Test
    void tenAttackersTakeDistinctPlacesRoundOneTargetAndAllReachIt() {
        SimHarness h = SimHarness.builder().build();
        int target = dummy(h, new Vec2d(18, 16));
        state(h).stats().addPersonal(target, Modifier.replace(CorePaths.HEALTH, 100_000, SOURCE)); // outlasts them all
        int[] attackers = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(7, 16), 10, 4);
        assertThat(attackers).hasSize(10);
        h.submit(P0, Attack.replace(UnitSet.of(attackers), target));
        Set<Integer> striking = new HashSet<>();
        for (int k = 0; k < 300 && striking.size() < attackers.length; k++) {
            h.step();
            for (int a : attackers) {
                if (leaf(h, a).equals(UnitAi.ATTACKING)) {
                    striking.add(a);
                }
            }
        }
        assertThat(striking).as("every attacker reached its target").hasSize(10);
        Set<Integer> places = new HashSet<>();
        for (int a : attackers) {
            places.add(state(h).orders().slot(Handle.index(a)));
            assertThat(state(h).orders().target(Handle.index(a))).isEqualTo(target);
        }
        assertThat(places).as("each its own place").hasSize(10);
    }

    @Test
    void aMeleeUnitChasesAFleeingTargetUntilItDies() {
        SimHarness h = SimHarness.builder().build();
        int a = spearman(h, F0, new Vec2d(6, 16));
        int b = dummy(h, new Vec2d(8, 16));
        h.submit(P0, Attack.replace(UnitSet.of(a), b));
        h.submit(P1, Move.replace(UnitSet.of(b), new Vec2d(29, 16))); // faster than a spearman
        boolean chased = false;
        int ticks = 0;
        while (h.queries().unit(b) != null && ticks++ < 1_000) {
            h.step();
            assertThat(state(h).orders().kind(Handle.index(a)))
                    .as("the attack holds while its target lives")
                    .isEqualTo(OrderKind.ATTACK);
            chased |= leaf(h, a).equals(UnitAi.CHASING) || leaf(h, a).equals(UnitAi.APPROACHING);
        }
        assertThat(h.queries().unit(b)).as("the target died").isNull();
        assertThat(chased).isTrue();
        h.runTicks(2);
        assertThat(leaf(h, a)).as("no enemy left in sight").isEqualTo(UnitAi.IDLE);
    }

    /** The stop ends the player's attack; blind, the spearman does not take the target up again by itself. */
    @Test
    void aStopEndsAChase() {
        SimHarness h = SimHarness.builder().build();
        int a = spearman(h, F0, new Vec2d(6, 16));
        state(h).stats().addPersonal(a, Modifier.replace(CorePaths.VISION, 0, SOURCE));
        int b = dummy(h, new Vec2d(8, 16));
        h.submit(P0, Attack.replace(UnitSet.of(a), b));
        h.submit(P1, Move.replace(UnitSet.of(b), new Vec2d(29, 16)));
        h.runTicks(20);
        assertThat(h.queries().unit(a).state()).isEqualTo(UnitState.WALKING);
        h.submit(P0, new Stop(UnitSet.of(a)));
        h.step();
        assertThat(state(h).orders().kind(Handle.index(a))).isEqualTo(OrderKind.NONE);
        assertThat(hits(h, a, 200)).isEmpty();
        assertThat(leaf(h, a)).isEqualTo(UnitAi.IDLE);
        assertThat(h.queries().unit(b)).as("the target got away").isNotNull();
    }

    @Test
    void anIdleUnitEngagesAnEnemyComingIntoSightAndGivesItUpWhenItLeaves() {
        SimHarness h = SimHarness.builder().size(96, 32).build();
        int a = spearman(h, F0, new Vec2d(16, 16));
        int b = dummy(h, new Vec2d(30, 2)); // 19.8 blocks off: out of sight
        h.runTicks(40);
        assertThat(leaf(h, a)).as("nothing in sight").isEqualTo(UnitAi.IDLE);
        h.submit(P1, Move.replace(UnitSet.of(b), new Vec2d(24, 16))); // into sight, out of reach
        int engagedAfter = -1;
        for (int k = 0; k < 120 && engagedAfter < 0; k++) {
            h.step();
            if (state(h).orders().kind(Handle.index(a)) == OrderKind.ENGAGE) {
                engagedAfter = k;
            }
        }
        assertThat(engagedAfter).as("engaged once in sight").isNotNegative();
        assertThat(state(h).orders().target(Handle.index(a))).isEqualTo(b);
        h.submit(P1, Move.replace(UnitSet.of(b), new Vec2d(94, 16))); // away, faster than the spearman
        h.runTicks(150);
        assertThat(state(h).orders().kind(Handle.index(a)))
                .as("given up out of sight")
                .isEqualTo(OrderKind.NONE);
        assertThat(leaf(h, a)).isEqualTo(UnitAi.IDLE);
    }

    @Test
    void anAttackedUnitHitsBackEvenWhenItSawNothingComing() {
        SimHarness h = SimHarness.builder().build();
        int a = spearman(h, F0, new Vec2d(6, 16));
        state(h).stats().addPersonal(a, Modifier.replace(CorePaths.VISION, 0, SOURCE)); // blind: it notices no one
        int b = spearman(h, F1, new Vec2d(20, 16));
        h.submit(P1, Attack.replace(UnitSet.of(b), a));
        List<Long> back = new ArrayList<>();
        long firstBlow = -1;
        for (int k = 0; k < 300 && back.isEmpty(); k++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof UnitHurt hurt && hurt.attacker() == b && firstBlow < 0) {
                    firstBlow = out.tick();
                }
                if (e instanceof UnitHurt hurt && hurt.attacker() == a) {
                    back.add(out.tick());
                }
            }
        }
        assertThat(firstBlow).as("the attacker struck").isPositive();
        assertThat(back).as("the blind unit hit back").isNotEmpty();
        assertThat(back.get(0)).as("a wind-up after the blow").isEqualTo(firstBlow + 10);
        assertThat(state(h).orders().target(Handle.index(a))).isEqualTo(b);
    }

    /** ORD-04.2: an attack-move fights the enemies met on the way, then walks on to its point. */
    @Test
    void attackMovingUnitsFightWhatTheyMeetThenGoOn() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int[] squad = spawn(h, TemplateSet.INFANTRY, F0, new Vec2d(6, 16), 5, 2);
        int[] met = spawn(h, TemplateSet.DEBUG_WALKER, F1, new Vec2d(32, 16), 2, 2);
        h.submit(P0, AttackMove.replace(UnitSet.of(squad), new Vec2d(64, 16)));
        boolean fought = false;
        int ticks = 0;
        while (ticks++ < 2_000) {
            h.step();
            for (int a : squad) {
                fought |= state(h).orders().kind(Handle.index(a)) == OrderKind.ENGAGE;
            }
            boolean arrived = true;
            for (int a : squad) {
                arrived &= state(h).orders().kind(Handle.index(a)) == OrderKind.NONE;
            }
            if (arrived) {
                break;
            }
        }
        assertThat(fought).as("they engaged on the way").isTrue();
        for (int d : met) {
            assertThat(h.queries().unit(d)).as("the enemies met died").isNull();
        }
        for (int a : squad) {
            assertThat(leaf(h, a)).isEqualTo(UnitAi.IDLE);
            assertThat(state(h).motion().x(Handle.index(a)))
                    .as("walked on to the point")
                    .isGreaterThan(58);
        }
    }
}
