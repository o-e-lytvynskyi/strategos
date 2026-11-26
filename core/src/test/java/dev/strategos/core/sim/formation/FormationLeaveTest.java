package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.order.OrderKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ORD-06.5 (pillar P3: individual control wins): a member given an order of its own, stopped, or killed leaves the
 * formation; the rest take new places, only then, and keep marching. A formation with no member left is removed.
 */
class FormationLeaveTest {
    private static final PlayerSlot P0 = PlayerSlot.ADMIN;

    private static FormationController marching(FormationFixtures f, int[] units) {
        FormationController c = f.march(units, 120, 50, FormationShape.BOX);
        f.h.runTicks(80);
        assertThat(c.advancing()).as("it is marching").isTrue();
        return c;
    }

    private static void assertReslotted(FormationFixtures f, FormationController c, int[] expected) {
        List<Integer> members = FormationFixtures.members(c);
        assertThat(members).containsExactlyInAnyOrderElementsOf(boxed(expected));
        Set<Integer> slots = new HashSet<>();
        for (int k = 0; k < c.size(); k++) {
            int i = Handle.index(c.member(k));
            assertThat(f.s.orders().kind(i)).isEqualTo(OrderKind.FORMATION);
            assertThat(f.s.orders().target(i)).isEqualTo(c.id());
            assertThat(slots.add(f.s.orders().slot(i))).isTrue();
            assertThat(f.s.orders().slot(i)).isEqualTo(k);
        }
        assertThat(slots).hasSize(expected.length);
    }

    @Test
    void aMemberGivenAMoveLeavesAndTheRestReslotAndKeepMarching() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 24, 6, 1.5, 20, 40);
        FormationController c = marching(f, units);
        List<Integer> before = FormationFixtures.members(c);
        int leaver = units[5];
        int[] rest = without(units, leaver);

        f.h.submit(P0, Move.replace(UnitSet.of(leaver), new Vec2d(30, 100)));
        f.h.step();

        assertThat(f.s.play().formations().count()).isEqualTo(1);
        assertThat(f.s.play().formations().at(0)).isSameAs(c);
        assertReslotted(f, c, rest);
        assertThat(FormationFixtures.members(c)).isNotEqualTo(before);
        assertThat(f.s.orders().kind(Handle.index(leaver))).isEqualTo(OrderKind.MOVE);
        double along = c.along();
        f.h.runTicks(100);
        assertThat(c.along()).as("the formation keeps marching").isGreaterThan(along + 5);
        double worst = 0;
        for (int k = 0; k < c.size(); k++) {
            worst = Math.max(worst, f.error(c, k));
        }
        assertThat(worst).as("the rest are in their new places").isLessThan(1.5);
    }

    @Test
    void aStoppedMemberLeaves() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 12, 4, 1.5, 20, 40);
        FormationController c = marching(f, units);

        f.h.submit(P0, new Stop(UnitSet.of(units[0], units[1])));
        f.h.step();

        assertReslotted(f, c, java.util.Arrays.copyOfRange(units, 2, units.length));
    }

    @Test
    void aMemberThatDiesLeaves() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 12, 4, 1.5, 20, 40);
        FormationController c = marching(f, units);
        int dead = units[3];

        f.h.damage(new ExternalDamage(dead, ExternalDamage.Kind.LETHAL, 1000f, ExternalDamage.SourceKind.NONE));
        f.h.step();
        f.h.step();

        assertThat(f.s.entities().isAlive(dead)).isFalse();
        assertReslotted(f, c, without(units, dead));
        double along = c.along();
        f.h.runTicks(40);
        assertThat(c.along()).isGreaterThan(along);
    }

    @Test
    void aFormationWithNoMemberLeftIsRemoved() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 6, 3, 1.5, 20, 40);
        marching(f, units);

        f.h.submit(P0, Move.replace(UnitSet.of(units), new Vec2d(30, 100)));
        f.h.step();

        assertThat(f.s.play().formations().empty()).isTrue();
    }

    @Test
    void aMemberMovedIntoAnotherFormationLeavesTheFirst() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] a = f.block(TemplateSet.INFANTRY, 8, 4, 1.5, 20, 40);
        int[] b = f.block(TemplateSet.INFANTRY, 8, 4, 1.5, 20, 60);
        FormationController first = f.march(a, 120, 50, FormationShape.BOX);
        f.h.runTicks(10);

        f.h.submit(
                P0,
                dev.strategos.core.protocol.FormationMove.replace(
                        UnitSet.of(FormationFixtures.both(new int[] {a[0]}, b)),
                        new Vec2d(120, 70),
                        FormationShape.LINE));
        f.h.step();

        assertThat(f.s.play().formations().count()).isEqualTo(2);
        assertThat(FormationFixtures.members(first)).doesNotContain(a[0]).hasSize(7);
    }

    @Test
    void aMemberThatFinishesItsPlaceWithAnOrderQueuedAfterTheFormationTakesItUp() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 6, 3, 1.5, 20, 40);
        FormationController c = f.march(units, 50, 46, FormationShape.BOX);
        f.h.submit(
                P0, new Move(UnitSet.of(units[0]), new Vec2d(60, 100), dev.strategos.core.protocol.QueueMode.QUEUE, 0));
        f.h.step();
        assertThat(FormationFixtures.members(c))
                .as("a queued order leaves it in")
                .contains(units[0]);

        assertThat(f.h.runUntil(() -> !FormationFixtures.members(c).contains(units[0]), 800))
                .as("it leaves once the formation stands")
                .isTrue();
        assertThat(c.holding()).isTrue();
        assertThat(f.s.orders().kind(Handle.index(units[0]))).isEqualTo(OrderKind.MOVE);
    }

    /**
     * A member of a standing formation that cannot get nearer its place (here it is put back three blocks off it every
     * tick, as a crowd or a wall would) leaves the formation after a while; the rest keep their shape and stand.
     */
    @Test
    void aMemberThatCannotReachItsPlaceInAStandingFormationLeaves() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 6, 3, 1.5, 20, 40);
        FormationController c = f.march(units, 60, 50, FormationShape.BOX);
        for (int t = 0; t < 3_000 && !c.holding(); t++) {
            f.h.step();
        }
        assertThat(c.holding()).as("the formation stands").isTrue();
        f.h.runTicks(60);
        int stuck = c.member(2);
        int i = Handle.index(stuck);
        for (int t = 0; t < 400 && f.s.orders().kind(i) == OrderKind.FORMATION; t++) {
            f.s.motion().set(i, c.slotX(2) + 3, f.s.motion().y(i), c.slotZ(2), 0);
            f.h.step();
        }
        assertThat(f.s.orders().kind(i)).as("it left").isNotEqualTo(OrderKind.FORMATION);
        f.h.step();
        assertReslotted(f, c, without(units, stuck));
    }

    private static int[] without(int[] units, int handle) {
        List<Integer> out = new ArrayList<>();
        for (int u : units) {
            if (u != handle) {
                out.add(u);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<Integer> boxed(int[] units) {
        return java.util.Arrays.stream(units).boxed().toList();
    }
}
