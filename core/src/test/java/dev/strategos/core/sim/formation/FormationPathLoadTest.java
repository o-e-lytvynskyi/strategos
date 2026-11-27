package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * ORD-06 / architecture §9.4: a formation costs a tenth of the path searches individual planning would. Searches are
 * the requests that were not answered at once ({@code requests - decided}); forty marching members on flat ground and
 * across the proving ground ask for at most four a tick on average.
 */
class FormationPathLoadTest {
    private static double searchesPerTick(SimHarness h, FormationController c, int maxTicks) {
        PathService.Stats before = h.queries().pathStats();
        int ticks = 0;
        for (; ticks < maxTicks && !c.holding(); ticks++) {
            h.step();
        }
        PathService.Stats after = h.queries().pathStats();
        long searches = (after.requests() - after.decided()) - (before.requests() - before.decided());
        System.out.printf(
                "FORMATION-PATH-LOAD ticks=%d searches=%d per-tick=%.3f requests=%d decided=%d arrived=%s%n",
                ticks,
                searches,
                searches / (double) ticks,
                after.requests() - before.requests(),
                after.decided() - before.decided(),
                c.holding());
        return searches / (double) ticks;
    }

    @Test
    void fortyOnFlatGroundAskForAtMostATenthPerTick() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] units = f.block(TemplateSet.INFANTRY, 40, 8, 1.5, 20, 50);
        FormationController c = f.march(units, 150, 56, FormationShape.BOX);

        assertThat(searchesPerTick(f.h, c, 1500)).isLessThanOrEqualTo(4.0);
    }

    @Test
    void fortyAcrossTheProvingGroundAskForAtMostATenthPerTick() {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        StartSlot from = ground.startSlots().get(0);
        StartSlot to = ground.startSlots().get(1);
        h.spawn(
                new dev.strategos.core.shared.FactionSlot(0),
                new dev.strategos.core.shared.Vec2d(from.x() + 0.5, from.z() + 0.5),
                40,
                6);
        int[] units = h.queries().unitHandles();
        h.submit(
                dev.strategos.core.shared.PlayerSlot.ADMIN,
                dev.strategos.core.protocol.FormationMove.replace(
                        dev.strategos.core.protocol.UnitSet.of(units),
                        new dev.strategos.core.shared.Vec2d(to.x() + 0.5, to.z() + 0.5),
                        FormationShape.BOX));
        h.step();
        FormationController c = dev.strategos.core.sim.KernelAccess.state(h.core())
                .play()
                .formations()
                .at(0);

        assertThat(searchesPerTick(h, c, 4000)).isLessThanOrEqualTo(4.0);
    }
}
