package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import org.junit.jupiter.api.Test;

/**
 * M6 §2.3: under conquest a faction with no unit and no building but fields and decorations is defeated, the match
 * ends with the last one standing, and a save after the end restores it; under the civic-centre rule units alone do
 * not count.
 */
class ConquestTest {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");

    /** Two centres and three units each on a flat 96 x 64, under {@code victory}. */
    static final class Scene {
        final SimHarness h;
        final MatchState s;
        final int[] units0;
        final int[] units1;

        Scene(PlayRules.Victory victory) {
            h = SimHarness.builder()
                    .size(96, 64)
                    .yBand(0, 31, 1)
                    .rules(MatchRules.DEFAULTS.withPlay(new PlayRules(victory, false, false)))
                    .build();
            h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 4, 4, 0));
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(1), 70, 40, 0));
            h.step();
            units0 = h.spawn(new FactionSlot(0), new Vec2d(12, 12), 3, 2);
            units1 = h.spawn(new FactionSlot(1), new Vec2d(64, 36), 3, 2);
            h.step();
            s = KernelAccess.state(h.core());
        }

        int centreOf(int faction) {
            for (int handle : s.buildings().handles()) {
                if (s.buildings().faction(handle) == faction) {
                    return handle;
                }
            }
            throw new AssertionError("no centre of faction " + faction);
        }
    }

    @Test
    void defeatWithinTwentyTicks() {
        Scene scene = new Scene(PlayRules.Victory.CONQUEST);
        SimHarness h = scene.h;
        assertThat(scene.units1).hasSize(3);
        h.runTicks(40);
        assertThat(h.events(MatchEnded.class)).as("both stand: on it goes").isEmpty();
        for (int unit : scene.units1) {
            scene.s.queueDamage(Damage.lethal(unit, Handle.NONE));
        }
        h.step();
        h.runTicks(40);
        assertThat(h.events(MatchEnded.class))
                .as("a centre still stands, so the faction does")
                .isEmpty();
        scene.s.queueDamage(Damage.lethal(scene.centreOf(1), Handle.NONE));
        long fell = -1;
        long ended = -1;
        for (int t = 0; t < 40 && ended < 0; t++) {
            TickOutputs out = h.step();
            for (OutputEvent e : out.events()) {
                if (e instanceof BuildingDestroyed) {
                    fell = out.tick();
                }
                if (e instanceof MatchEnded) {
                    ended = out.tick();
                }
            }
        }
        assertThat(fell).isPositive();
        assertThat(ended - fell).as("ended within 20 ticks of the fall").isBetween(0L, 20L);
        assertThat(h.events(FactionDefeated.class))
                .containsExactly(new FactionDefeated(new FactionSlot(1), FactionDefeated.CONQUERED));
        MatchEnded end = h.events(MatchEnded.class).get(0);
        assertThat(end.winner()).isZero();
        h.runTicks(40);
        assertThat(h.events(MatchEnded.class)).as("it ends once").hasSize(1);

        SimHarness back = SimHarness.restore(h.core().snapshot(), scene.s.templates());
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        MatchState restored = KernelAccess.state(back.core());
        assertThat(restored.outcome().ended()).isTrue();
        assertThat(restored.outcome().winner()).isZero();
    }

    @Test
    void civicCentresOnly() {
        Scene scene = new Scene(PlayRules.Victory.CIVIC_CENTRE);
        for (int unit : scene.units1) {
            scene.s.queueDamage(Damage.lethal(unit, Handle.NONE));
        }
        scene.h.runTicks(60);
        assertThat(scene.h.events(FactionDefeated.class)).isEmpty();
        assertThat(scene.h.events(MatchEnded.class)).isEmpty();
        assertThat(scene.s.buildings().alive(scene.centreOf(1))).isTrue();
    }
}
