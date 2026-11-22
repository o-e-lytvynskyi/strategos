package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class MultiplayerSceneTest {
    private static ProvingGround map;

    @BeforeAll
    static void terrain() {
        map = ProvingGround.generate();
    }

    @Test
    void normalAndStressScenesUseFourCompleteTowns() {
        holdsScale(MvpScene.Config.SOAK);
        holdsScale(MvpScene.Config.STRESS);
    }

    private static void holdsScale(MvpScene.Config scale) {
        var config = new MultiplayerScene.Config(scale);
        var scene = config.create(map.startSlots());
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(SimHarness.DEFAULT_SEED)
                .factions(MultiplayerScene.FACTIONS)
                .rules(MatchRules.DEFAULTS.withPlay(PlayRules.DEFAULTS))
                .starts(map.startSlots())
                .build();
        h.publishAll(map.voxels());
        h.step();
        var setup = scene.setup(TemplateSet.builtin());
        setup.forEach(i -> h.submit(i.issuer(), i.command()));
        int count = 0;
        for (int step = 0; step < 5; step++) {
            for (var event : h.step().events()) {
                if (event instanceof CommandResult result) {
                    assertThat(result.outcome()).as(result.toString()).isEqualTo(CommandOutcome.ACCEPTED);
                    count++;
                }
            }
        }
        assertThat(count).isEqualTo(setup.size());
        var q = h.core().queries();
        assertThat(q.unitCount()).isEqualTo(config.unitsAtStart());
        for (int f = 0; f < MultiplayerScene.FACTIONS; f++) {
            int faction = f;
            assertThat(q.populationUsed(new FactionSlot(f)))
                    .as("faction %s population", f)
                    .isEqualTo(scale.population());
            assertThat(q.buildings().stream()
                            .filter(b -> b.faction() == faction && b.state() == BuildingStore.COMPLETE)
                            .count())
                    .as("faction %s buildings", f)
                    .isEqualTo(scale.buildings());
            assertThat(Arrays.stream(q.unitHandlesOf(new FactionSlot(f))).count())
                    .isEqualTo(scale.unitsPerFaction());
        }
        assertThat(scene.next(q)).isEmpty();

        var faction = new FactionSlot(2);
        int unit = q.unitHandlesOf(faction)[0];
        int house = q.buildings().stream()
                .filter(b -> b.faction() == faction.value() && b.template().equals(MvpScene.HOUSE))
                .findFirst()
                .orElseThrow()
                .handle();
        h.damage(new ExternalDamage(unit, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
        h.damage(new ExternalDamage(house, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
        h.step();
        assertThat(q.unitCount()).isEqualTo(config.unitsAtStart() - 1);
        assertThat(MultiplayerScene.finishedBuildings(q)[2]).isEqualTo(scale.buildings() - 1);
        for (int i = 0; i < 6; i++) {
            scene.next(q).forEach(order -> h.submit(order.issuer(), order.command()));
            for (var event : h.step().events()) {
                if (event instanceof CommandResult result) {
                    assertThat(result.outcome()).as(result.toString()).isEqualTo(CommandOutcome.ACCEPTED);
                }
            }
        }
        assertThat(q.populationUsed(faction)).isEqualTo(scale.population());
        assertThat(q.unitCount()).isEqualTo(config.unitsAtStart());
        assertThat(MultiplayerScene.finishedBuildings(q)[2]).isEqualTo(scale.buildings());
    }
}
