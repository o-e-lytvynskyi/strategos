package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QA-12, the mop-up (M6 §2.21): the enemy's centre is down and three of its buildings stand; twelve of our soldiers
 * stand idle. They go in squads of four, each squad at a different building, and not all at the nearest.
 */
class MopUpTest {
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");

    @Test
    void idleSoldiersBurnDifferentBuildingsInSquads() {
        SimHarness h =
                SimHarness.builder().size(96, 96).yBand(0, 8, 1).factions(2).build();
        h.publishAll(VoxelFixtures.flat(96, 96, 8, 1));
        h.step();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(new TemplateId("strategos:building/civic_centre"), new FactionSlot(0), 10, 10, 0));
        int[][] at = {{50, 50}, {62, 50}, {50, 62}};
        for (int[] p : at) {
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(1), p[0], p[1], 0));
            h.step();
        }
        List<Integer> soldiers = new ArrayList<>();
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(58.5, 56.5), 12, 3));
        h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .forEach(e -> soldiers.add(((UnitCreated) e).handle()));
        assertThat(soldiers).hasSize(12);

        AiView view = new AiView(h.core().queries(), 0, new AiBudget(AiBudget.CAP));
        assertThat(view.enemyBuildings()).as("the enemy's houses").hasSize(3);
        Base base = Base.of(view);
        assertThat(base).isNotNull();
        List<UnitView> army = new ArrayList<>();
        for (int s : soldiers) {
            army.add(h.core().queries().unit(s));
        }
        List<Command> sent = new ArrayList<>();
        boolean gave = MopUp.run(view, new AiBudget(AiBudget.CAP), sent::add, base, army);

        assertThat(gave).isTrue();
        Set<Integer> targets = new HashSet<>();
        for (Command c : sent) {
            if (c instanceof Attack a) {
                targets.add(a.target());
            }
        }
        assertThat(targets).as("buildings attacked, one squad each").hasSize(3);
        assertThat(sent).as("one order a squad of four").hasSize(3);
    }
}
