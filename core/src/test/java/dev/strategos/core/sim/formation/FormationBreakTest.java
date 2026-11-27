package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.TreeFixtures;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import org.junit.jupiter.api.Test;

/**
 * ORD-06.3: an attack, an attack-move or a gather order to a member breaks the formation up (an order to one member
 * breaks it for all, as the formation fights and works as units do); the members left walk on to where their places
 * end, each on its own. Orders to every member leave nothing of it.
 */
class FormationBreakTest {
    private static final PlayerSlot P0 = PlayerSlot.ADMIN;

    private static int enemy(FormationFixtures f) {
        f.h.submit(P0, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(150, 90), 1, 0));
        return f.h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private static void assertBrokenToWalkers(FormationFixtures f, int[] rest) {
        assertThat(f.s.play().formations().empty()).as("the formation is gone").isTrue();
        int group = f.s.orders().group(Handle.index(rest[0]));
        assertThat(group).as("the rest walk on as one group order").isNotZero();
        for (int handle : rest) {
            int i = Handle.index(handle);
            assertThat(f.s.orders().kind(i)).isEqualTo(OrderKind.MOVE);
            assertThat(f.s.orders().group(i)).isEqualTo(group);
            assertThat(f.s.ai().state(i)).isEqualTo(UnitState.WALKING);
        }
    }

    @Test
    void anAttackOrderBreaksTheFormation() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 16, 4, 1.5, 20, 40);
        int enemy = enemy(f);
        FormationController c = f.march(units, 100, 50, FormationShape.BOX);
        f.h.runTicks(60);
        assertThat(c.advancing()).as("it is marching").isTrue();
        int[] rest = java.util.Arrays.copyOfRange(units, 1, units.length);

        f.h.submit(P0, Attack.replace(UnitSet.of(units[0]), enemy));
        f.h.step();

        assertBrokenToWalkers(f, rest);
        assertThat(f.s.orders().kind(Handle.index(units[0]))).isEqualTo(OrderKind.ATTACK);
    }

    @Test
    void anAttackMoveBreaksTheFormation() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 16, 4, 1.5, 20, 40);
        f.march(units, 100, 50, FormationShape.BOX);
        f.h.runTicks(60);
        int[] rest = java.util.Arrays.copyOfRange(units, 1, units.length);

        f.h.submit(P0, AttackMove.replace(UnitSet.of(units[0]), new Vec2d(60, 100)));
        f.h.step();

        assertBrokenToWalkers(f, rest);
        assertThat(f.s.orders().kind(Handle.index(units[0]))).isEqualTo(OrderKind.ATTACK_MOVE);
    }

    @Test
    void aGatherOrderBreaksTheFormation() {
        VoxelWorld world = VoxelFixtures.flat(200, 120, 32, 1);
        TreeFixtures.draw(world, TreeFixtures.Species.OAK, 150, 1, 60);
        FormationFixtures f = FormationFixtures.flat(true, world);
        int[] workers = f.block(TemplateSet.WORKER, 4, 4, 1.5, 20, 40);
        int[] spears = f.block(TemplateSet.INFANTRY, 12, 4, 1.5, 20, 46);
        int[] all = FormationFixtures.both(workers, spears);
        f.march(all, 100, 50, FormationShape.BOX);
        f.h.runTicks(60);
        int[] rest = java.util.Arrays.copyOfRange(all, 1, all.length);

        f.h.submit(P0, Gather.replace(UnitSet.of(workers[0]), 150, 2, 60));
        f.h.step();

        assertBrokenToWalkers(f, rest);
        assertThat(f.s.orders().kind(Handle.index(workers[0]))).isEqualTo(OrderKind.GATHER);
    }

    @Test
    void anOrderToEveryMemberLeavesNothing() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 10, 5, 1.5, 20, 40);
        int enemy = enemy(f);
        f.march(units, 100, 50, FormationShape.BOX);
        f.h.runTicks(30);

        f.h.submit(P0, Attack.replace(UnitSet.of(units), enemy));
        f.h.step();

        assertThat(f.s.play().formations().empty()).isTrue();
        for (int handle : units) {
            assertThat(f.s.orders().kind(Handle.index(handle))).isEqualTo(OrderKind.ATTACK);
        }
    }

    @Test
    void aStandingFormationBreaksTooAndItsMembersWalkOn() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 10, 5, 1.5, 20, 40);
        FormationController c = f.march(units, 40, 46, FormationShape.BOX);
        assertThat(f.h.runUntil(c::holding, 600)).isTrue();
        f.h.runTicks(30);
        int[] rest = java.util.Arrays.copyOfRange(units, 1, units.length);

        f.h.submit(P0, AttackMove.replace(UnitSet.of(units[0]), new Vec2d(150, 100)));
        f.h.step();

        assertThat(f.s.play().formations().empty()).as("the formation is gone").isTrue();
        for (int handle : rest) {
            int i = Handle.index(handle);
            assertThat(f.s.orders().kind(i))
                    .as("each stands in its place, its walk there done")
                    .isNotEqualTo(OrderKind.FORMATION);
            assertThat(UnitAi.leaf(f.s.ai().code(i)).name()).doesNotStartWith("formation");
        }
    }
}
