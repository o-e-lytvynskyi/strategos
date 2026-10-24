package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * UI-12.1: a raid that burns a house for its whole two minutes raises one alert, not one a blow; an attack far from it
 * raises a second; the raiders, never struck, raise none.
 */
class AttackAlertTest {
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    @Test
    void aSustainedAttackRaisesOneAlert() {
        SimHarness h = SimHarness.builder().size(96, 64).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 10, 10, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(0), 80, 50, 0));
        h.step();
        MatchState s = KernelAccess.state(h.core());
        int first = s.buildings().handles()[0];
        int second = s.buildings().handles()[1];
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(14, 20), 1, 0));
        int raider = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        h.submit(new PlayerSlot(1), Attack.replace(UnitSet.of(raider), first));
        h.runUntil(() -> !s.buildings().alive(first), 4_000);
        assertThat(s.buildings().alive(first)).isFalse();
        long blows = h.events(BuildingHurt.class).stream()
                .filter(e -> e.handle() == first)
                .count();
        assertThat(blows).as("a blow a second for two minutes").isGreaterThan(100);
        assertThat(h.events(UnderAttack.class))
                .as("one alert for the whole raid")
                .containsExactly(new UnderAttack(new FactionSlot(0), 12.5, 12.5));

        h.submit(new PlayerSlot(1), Attack.replace(UnitSet.of(raider), second));
        h.runUntil(() -> h.events(UnderAttack.class).size() > 1, 4_000);
        assertThat(h.events(UnderAttack.class))
                .as("a house 80 blocks off: a new attack")
                .last()
                .isEqualTo(new UnderAttack(new FactionSlot(0), 82.5, 52.5));
        assertThat(h.events(UnderAttack.class)).allMatch(a -> a.faction().value() == 0);
    }
}
