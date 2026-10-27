package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * ORD-05 in L1 (M5 §2.5): an Attack makes every archer in range shoot the one target, an AttackSpread shares the group
 * over the enemies in the target's sight to within one of even, and an attacker whose enemy dies takes another of the
 * spread.
 */
class FocusTest {
    private static final TemplateId RANGER = RangedOrdersTest.RANGER;
    private static final TemplateId DUMMY = RangedOrdersTest.DUMMY;
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);

    private final SimHarness h = RangedOrdersTest.harness(12, 2, 4, 0);
    private final MatchState s = KernelAccess.state(h.core());

    private int[] archers(int n) {
        int[] out = new int[n];
        for (int k = 0; k < n; k++) {
            out[k] = RangedOrdersTest.spawn(h, RANGER, F0, 5.5, 4.5 + k);
            h.submit(P0, new SetStance(UnitSet.of(out[k]), Stance.PASSIVE)); // only an order makes them shoot
        }
        h.step();
        return out;
    }

    private int[] dummies(int k) {
        int[] out = new int[k];
        for (int j = 0; j < k; j++) {
            out[j] = RangedOrdersTest.spawn(h, DUMMY, F1, 14.5 + (j % 2), 4.5 + j * 1.5);
        }
        return out;
    }

    private CommandResult send(dev.strategos.core.protocol.Command command) {
        h.submit(P0, command);
        h.step();
        return h.results().get(h.results().size() - 1);
    }

    /** ORD-05.1: five archers in range of a target all release a shot at it within their prepare time. */
    @org.junit.jupiter.api.Test
    void aFocusMakesEveryArcherInRangeShootIt() {
        int[] archers = archers(5);
        int target = dummies(1)[0];
        h.runTicks(10);
        assertThat(h.events(UnitStruck.class))
                .as("passive archers hold their fire")
                .isEmpty();

        assertThat(send(Attack.replace(UnitSet.of(archers), target)).accepted()).isEqualTo(5);
        Set<Integer> shooters = new HashSet<>();
        for (int t = 0; t < 8 && shooters.size() < archers.length; t++) { // prepare 2, and a step to take the order
            for (UnitStruck struck : h.events(UnitStruck.class)) {
                assertThat(struck.target()).isEqualTo(target);
                shooters.add(struck.attacker());
            }
            h.step();
        }
        for (UnitStruck struck : h.events(UnitStruck.class)) {
            shooters.add(struck.attacker());
        }
        assertThat(shooters).containsExactlyInAnyOrder(boxed(archers));
    }

    private static Integer[] boxed(int[] values) {
        Integer[] out = new Integer[values.length];
        for (int k = 0; k < values.length; k++) out[k] = values[k];
        return out;
    }

    /** ORD-05.2: each enemy of the spread gets the group's size over their number, rounded down or up. */
    @ParameterizedTest(name = "{0} attackers over {1} enemies")
    @CsvSource({"1,1", "5,2", "7,3", "8,4", "3,5", "10,3", "6,6"})
    void aSpreadIsWithinOneOfEven(int n, int k) {
        int[] archers = archers(n);
        int[] enemies = dummies(k);
        CommandResult result = send(new AttackSpread(UnitSet.of(archers), enemies[0], QueueMode.REPLACE));
        assertThat(result.accepted()).isEqualTo(n);

        int[] held = new int[k];
        int group = s.orders().group(Handle.index(archers[0]));
        assertThat(group).as("one group for the whole spread").isNotZero();
        for (int archer : archers) {
            int i = Handle.index(archer);
            assertThat(s.orders().kind(i)).isEqualTo(OrderKind.ATTACK);
            assertThat(s.orders().group(i)).isEqualTo(group);
            for (int j = 0; j < k; j++) {
                if (s.orders().target(i) == enemies[j]) held[j]++;
            }
        }
        for (int j = 0; j < k; j++) {
            assertThat(held[j]).as("attackers of enemy %d", j).isBetween(n / k, (n + k - 1) / k);
        }
        assertThat(java.util.Arrays.stream(held).sum()).isEqualTo(n);
        assertThat(held[0]).as("the clicked enemy is first in line").isEqualTo((n + k - 1) / k);
    }

    /** ORD-05.2: an attacker whose enemy dies takes the nearest target of the others on the spread. */
    @org.junit.jupiter.api.Test
    void anAttackerWhoseEnemyDiesTakesAnotherOfTheSpread() {
        int[] archers = archers(4);
        int[] enemies = dummies(2);
        send(new AttackSpread(UnitSet.of(archers), enemies[0], QueueMode.REPLACE));
        int group = s.orders().group(Handle.index(archers[0]));
        s.removeUnit(enemies[0]);

        assertThat(h.runUntil(
                        () -> {
                            for (int archer : archers) {
                                if (s.orders().target(Handle.index(archer)) != enemies[1]) return false;
                            }
                            return true;
                        },
                        20))
                .as("all four end on the one enemy left")
                .isTrue();
        for (int archer : archers) {
            int i = Handle.index(archer);
            assertThat(s.orders().kind(i)).isEqualTo(OrderKind.ATTACK);
            assertThat(s.orders().group(i)).as("still on the spread").isEqualTo(group);
        }
        s.removeUnit(enemies[1]);
        h.runTicks(10);
        for (int archer : archers) {
            assertThat(s.orders().kind(Handle.index(archer)))
                    .as("none left to take")
                    .isNotEqualTo(OrderKind.ATTACK);
        }
    }

    /** The command's refusals: own units, a dead target, another faction's units, no units and unarmed ones. */
    @org.junit.jupiter.api.Test
    void aSpreadIsRefusedForBadArguments() {
        int[] archers = archers(2);
        int[] enemies = dummies(2);
        int stranger = RangedOrdersTest.spawn(h, RANGER, F1, 20.5, 20.5);

        CommandResult own = send(new AttackSpread(UnitSet.of(archers[0]), archers[1], QueueMode.REPLACE));
        assertThat(own.accepted()).isZero();
        assertThat(own.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);

        CommandResult foreign = send(new AttackSpread(UnitSet.of(stranger), enemies[0], QueueMode.REPLACE));
        assertThat(foreign.firstReason()).isEqualTo(RejectReason.NOT_OWNER);

        CommandResult unarmed = send(new AttackSpread(UnitSet.of(enemies[1]), archers[0], QueueMode.REPLACE));
        assertThat(unarmed.accepted()).isZero();

        CommandResult none = send(new AttackSpread(UnitSet.of(), enemies[0], QueueMode.REPLACE));
        assertThat(none.firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);

        int dead = enemies[1];
        s.removeUnit(dead);
        CommandResult gone = send(new AttackSpread(UnitSet.of(archers), dead, QueueMode.REPLACE));
        assertThat(gone.firstReason()).isEqualTo(RejectReason.UNKNOWN_TARGET);
        assertThat(s.orders().kind(Handle.index(archers[0]))).isNotEqualTo(OrderKind.ATTACK);
    }
}
