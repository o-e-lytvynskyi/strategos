package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnBuilding;
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
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * QA-12: an archer's place for shooting at its target is never inside a building: it was, 20 blocks from a target on the
 * far side of a civic centre, which no one can stand in, and the archer stood WALKING short of it until its order failed.
 */
class RangedPlaceTest {
    private static int spawn(SimHarness h, String template, FactionSlot f, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(new TemplateId(template), f, at, 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .mapToInt(UnitCreated::handle)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void anArchersPlaceIsNotInsideTheBuildingBetweenItAndItsTarget() {
        SimHarness h = SimHarness.builder().size(96, 64).build();
        h.step();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(new TemplateId("strategos:building/civic_centre"), new FactionSlot(1), 40, 20, 0));
        h.step();
        int target = spawn(h, "strategos:unit/infantry", new FactionSlot(1), new Vec2d(30.5, 25.5));
        MatchState s = KernelAccess.state(h.core());
        int archer = spawn(h, "strategos:unit/archer", new FactionSlot(0), new Vec2d(80.5, 25.5));
        // the place, a range short of the target toward the archer, would fall in the centre (x 40 to 50)
        h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(archer), target));
        h.runTicks(10);
        int i = Handle.index(archer);
        assertThat(s.footprintAt((int) Math.floor(s.orders().targetX(i)), (int)
                        Math.floor(s.orders().targetZ(i))))
                .as("its place (%.2f, %.2f)", s.orders().targetX(i), s.orders().targetZ(i))
                .isFalse();
    }
}
