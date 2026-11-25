package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import org.junit.jupiter.api.Test;

/**
 * ORD-06.2: a formation walks at its slowest member's speed. The members are horsemen, spearmen and workers, whose
 * speeds differ; the anchor walks exactly the slowest's step each tick it walks, the faster members keep their
 * places by standing when they are there, and when the slowest member leaves the next slowest sets the pace.
 */
class FormationSpeedTest {
    @Test
    void theFormationWalksAtTheSlowestMembersSpeed() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] horses = f.block(FormationFixtures.HORSEMAN, 6, 6, 2.0, 20, 40);
        int[] spears = f.block(TemplateSet.INFANTRY, 10, 5, 1.5, 20, 46);
        int[] workers = f.block(TemplateSet.WORKER, 4, 4, 1.5, 20, 52);
        int[] all = FormationFixtures.both(FormationFixtures.both(horses, spears), workers);
        double slowest = Double.MAX_VALUE;
        double fastest = 0;
        for (int u : all) {
            double v = f.s.stats().walkSpeed(Handle.index(u));
            slowest = Math.min(slowest, v);
            fastest = Math.max(fastest, v);
        }
        assertThat(fastest).as("the group's speeds differ").isGreaterThan(slowest + 1e-9);

        FormationController c = f.march(all, 120, 50, FormationShape.BOX);

        int walked = 0;
        double worst = 0;
        for (int t = 0; t < 1500 && !c.holding(); t++) {
            double before = c.along();
            f.h.step();
            if (c.advancing()) {
                walked++;
                assertThat(c.speed()).isEqualTo(slowest);
                assertThat(c.along() - before).isCloseTo(slowest, within(1e-9));
                for (int k = 0; k < c.size(); k++) {
                    worst = Math.max(worst, f.error(c, k));
                }
            }
        }
        assertThat(c.holding()).isTrue();
        assertThat(walked).isGreaterThan(100);
        assertThat(worst).as("the faster members keep their places").isLessThan(1.0);
    }

    @Test
    void whenTheSlowestLeavesTheNextSlowestSetsThePace() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] horses = f.block(FormationFixtures.HORSEMAN, 5, 5, 2.0, 20, 40);
        int[] workers = f.block(TemplateSet.WORKER, 1, 1, 1.5, 20, 46);
        int[] spears = f.block(TemplateSet.INFANTRY, 6, 6, 1.5, 20, 50);
        int[] all = FormationFixtures.both(FormationFixtures.both(horses, workers), spears);
        FormationController c = f.march(all, 120, 50, FormationShape.BOX);
        f.h.runTicks(20);
        double before = c.speed();
        int slowest = all[0];
        for (int u : all) {
            if (f.s.stats().walkSpeed(Handle.index(u)) < f.s.stats().walkSpeed(Handle.index(slowest))) {
                slowest = u;
            }
        }
        assertThat(before).isEqualTo(f.s.stats().walkSpeed(Handle.index(slowest)));

        f.h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(slowest), new Vec2d(30, 100)));
        f.h.step();

        double next = Double.MAX_VALUE;
        for (int k = 0; k < c.size(); k++) {
            next = Math.min(next, f.s.stats().walkSpeed(Handle.index(c.member(k))));
        }
        assertThat(c.size()).isEqualTo(all.length - 1);
        assertThat(c.speed()).isEqualTo(next);
    }
}
