package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * ECO-10 in L1: a wild boar (passive, flee distance 16 in its data) runs from whoever hurts it until it is 16 blocks
 * away and then stops where it is; a passive animal notices nobody, so an enemy that merely comes near, or stands
 * beside it, never moves it.
 */
class FleeStanceTest {
    private static final TemplateId BOAR = new TemplateId("strategos:unit/wild_boar");
    private static final double FLEE = 16;

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    private static int spawn(SimHarness h, TemplateId template, int faction, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private static double apart(SimHarness h, int a, int b) {
        MatchState s = state(h);
        int i = Handle.index(a);
        int j = Handle.index(b);
        return DetMath.length(
                s.motion().x(i) - s.motion().x(j), s.motion().z(i) - s.motion().z(j));
    }

    @Test
    void aHurtBoarRunsTheStancesDistanceFromItsAttackerAndStops() {
        SimHarness h = SimHarness.builder().size(128, 128).factions(2).build();
        int boar = spawn(h, BOAR, 2, 60.5, 60.5);
        int spearman = spawn(h, TemplateSet.INFANTRY, 0, 57.5, 60.5);
        int b = Handle.index(boar);
        assertThat(state(h).stats().fleeDistance(b)).as("the data's distance").isEqualTo(FLEE);
        // one real blow, from a spearman that then stands still where the blow came from
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(spearman), boar));
        boolean hurt = h.runUntil(() -> !h.events(UnitHurt.class).isEmpty(), 200);
        assertThat(hurt).as("struck").isTrue();
        h.step();
        assertThat(state(h).orders().kind(b)).as("it runs").isEqualTo(OrderKind.FLEE);
        assertThat(state(h).orders().target(b)).as("from its attacker").isEqualTo(spearman);
        // the attacker is held where it is, so the distance is the attacker's place to the boar's
        state(h).orders().clear(Handle.index(spearman));
        double x = state(h).motion().x(Handle.index(spearman));
        double z = state(h).motion().z(Handle.index(spearman));
        h.runTicks(400);
        state(h).motion().set(Handle.index(spearman), x, state(h).motion().y(Handle.index(spearman)), z, 0);
        assertThat(state(h).orders().size(b)).as("it stopped").isZero();
        assertThat(apart(h, boar, spearman))
                .as("the stance's distance from where the attacker stood")
                .isBetween(FLEE - 1.5, FLEE + 3);
        double at = state(h).motion().x(b);
        h.runTicks(100);
        assertThat(state(h).motion().x(b)).as("and stays").isEqualTo(at);
    }

    @Test
    void aPassiveBoarIgnoresAnEnemyThatOnlyComesNear() {
        SimHarness h = SimHarness.builder().size(128, 128).factions(2).build();
        int boar = spawn(h, BOAR, 2, 60.5, 60.5);
        int b = Handle.index(boar);
        double x = state(h).motion().x(b);
        double z = state(h).motion().z(b);
        int wolf = spawn(h, TemplateSet.INFANTRY, 0, 40.5, 60.5);
        h.submit(new PlayerSlot(0), Move.replace(UnitSet.of(wolf), new Vec2d(59.5, 60.5)));
        for (int t = 0; t < 300; t++) {
            h.step();
            assertThat(state(h).orders().kind(b))
                    .as("it never takes a flight while nobody hurts it")
                    .isNotEqualTo(OrderKind.FLEE);
        }
        assertThat(apart(h, boar, wolf)).as("the enemy came near").isLessThan(3);
        assertThat(state(h).motion().x(b)).isEqualTo(x);
        assertThat(state(h).motion().z(b)).isEqualTo(z);
        assertThat(h.events(UnitHurt.class)).isEmpty();
    }
}
