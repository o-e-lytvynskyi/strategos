package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
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
 * QA-12: the place {@link Engagement#choose} gives a melee attacker round its target never lies on a building's footprint.
 * A horseman chased an enemy at a civic centre's edge to a place inside the centre, which it could not reach, and stood
 * WALKING there until the stuck ladder failed its order, again and again (the match of rush against boom, seed 14).
 */
class EngagementPlaceTest {
    private static int spawn(SimHarness h, FactionSlot faction, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, faction, at, 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .map(UnitCreated.class::cast)
                .mapToInt(UnitCreated::handle)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void thePlaceRoundATargetAtACentresEdgeLiesOutsideTheCentre() {
        SimHarness h = SimHarness.builder().size(64, 64).build();
        h.step();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(new TemplateId("strategos:building/civic_centre"), new FactionSlot(1), 20, 20, 0));
        h.step();
        int target = spawn(h, new FactionSlot(1), new Vec2d(19.5, 25.5)); // the centre's west edge
        int attacker = spawn(h, new FactionSlot(0), new Vec2d(40.5, 25.5)); // east of it: its nearest places are inside
        MatchState s = KernelAccess.state(h.core());
        int t = Handle.index(target);
        int a = Handle.index(attacker);
        assertThat(s.footprintAt(21, 25)).as("the centre stands there").isTrue();
        double body = Engagement.radius(s, a);
        double radii = body + Engagement.radius(s, t);
        int place = new Engagement().choose(s, a, target);
        double x = s.motion().x(t) + Engagement.offsetX(place, radii, body);
        double z = s.motion().z(t) + Engagement.offsetZ(place, radii, body);
        assertThat(s.footprintAt((int) Math.floor(x), (int) Math.floor(z)))
                .as("the place (%.2f, %.2f)", x, z)
                .isFalse();
    }
}
