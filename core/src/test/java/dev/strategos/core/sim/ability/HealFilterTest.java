package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.P0;
import static dev.strategos.core.sim.ability.AbilityFixtures.ram;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static dev.strategos.core.sim.ability.AbilityFixtures.walker;
import static dev.strategos.core.sim.ability.HealFixtures.damage;
import static dev.strategos.core.sim.ability.HealFixtures.healer;
import static dev.strategos.core.sim.ability.HealFixtures.infantry;
import static dev.strategos.core.sim.ability.HealFixtures.wound;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/** CMB-12.3: siege engines, buildings and what is not flesh are never healed, by the picker or by an order; they are repaired. */
class HealFilterTest {
    private static SimHarness match() {
        SimHarness h = SimHarness.builder().size(96, 64).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.step();
        return h;
    }

    private static RejectReason order(SimHarness h, int healer, int target) {
        h.submit(P0, UseAbility.replace(UnitSet.of(healer), new AbilityId("heal"), Target.unit(target)));
        h.step();
        return h.results().get(h.results().size() - 1).firstReason();
    }

    @Test
    void aWoundedRamAndAWalkerAreNeitherPickedNorHealed() {
        SimHarness h = match();
        int healer = healer(h, F0, 40, 30);
        int ram = ram(h, F0, 43, 30);
        int walker = walker(h, F0, 37, 30);
        wound(h, ram, 100);
        wound(h, walker, 3);
        assertThat(AbilityTargets.pick("most_wounded_ally", state(h), healer, HealFixtures.heal()))
                .isEqualTo(Handle.NONE);
        assertThat(order(h, healer, ram)).isEqualTo(RejectReason.INVALID_ARGUMENT);
        assertThat(order(h, healer, walker)).isEqualTo(RejectReason.INVALID_ARGUMENT);
        h.runTicks(120);
        assertThat(damage(h, ram)).as("repaired by workers, never healed").isEqualTo(100);
        assertThat(damage(h, walker)).isEqualTo(3);
    }

    @Test
    void aDamagedBuildingIsNeitherPickedNorHealed() {
        SimHarness h = match();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(new TemplateId("strategos:building/barracks"), new FactionSlot(0), 44, 30, 0));
        h.step();
        int[] handles = state(h).buildings().handles();
        int building = handles[handles.length - 1];
        state(h).buildings().setDamage(building, 400);
        int healer = healer(h, F0, 40, 30);
        assertThat(AbilityTargets.pick("most_wounded_ally", state(h), healer, HealFixtures.heal()))
                .isEqualTo(Handle.NONE);
        assertThat(order(h, healer, building)).isEqualTo(RejectReason.INVALID_ARGUMENT);
        h.runTicks(120);
        assertThat(state(h).buildings().damage(building)).isEqualTo(400);
    }

    @Test
    void aSoldierIsHealedWhereARamBesideItIsNot() {
        SimHarness h = match();
        healer(h, F0, 40, 30);
        int ram = ram(h, F0, 43, 30);
        int soldier = infantry(h, F0, 37, 30);
        wound(h, ram, 100);
        wound(h, soldier, 1);
        h.runTicks(60);
        assertThat(damage(h, soldier)).isZero();
        assertThat(damage(h, ram)).isEqualTo(100);
    }
}
