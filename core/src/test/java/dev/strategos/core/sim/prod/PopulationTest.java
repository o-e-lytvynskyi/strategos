package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * ECO-06 in L1: the population counters equal a scan of the match through seeded training, cancels, spawns and deaths;
 * training waits at the cap with one notice and resumes once a house stands; the cap is never above the match's most.
 */
class PopulationTest {
    @Test
    void invariantsOverSeededTrainingAndDeaths() {
        for (long seed = 1; seed <= 20; seed++) {
            ProdScene p = ProdScene.of(5_000, 5_000, ProdScene.withMax(150), VoxelFixtures.flat(64, 64, 32, 1), seed);
            SplittableRandom rng = new SplittableRandom(seed);
            for (int t = 0; t < 800; t++) {
                switch (rng.nextInt(40)) {
                    case 0 ->
                        p.h.submit(ProdScene.P0, new dev.strategos.core.protocol.Train(p.centre, TemplateSet.WORKER));
                    case 1 -> {
                        if (p.s.buildings().queueSize(p.centre) > 0) {
                            p.h.submit(
                                    ProdScene.P0,
                                    new CancelTrain(
                                            p.centre,
                                            rng.nextInt(p.s.buildings().queueSize(p.centre))));
                        }
                    }
                    case 2 ->
                        p.h.submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(
                                        TemplateSet.INFANTRY,
                                        new FactionSlot(0),
                                        new Vec2d(40, 40),
                                        1 + rng.nextInt(3),
                                        2));
                    case 3 -> {
                        int[] units = p.h.queries().unitHandles();
                        if (units.length > 0) {
                            p.h.damage(new ExternalDamage(
                                    units[rng.nextInt(units.length)],
                                    ExternalDamage.Kind.LETHAL,
                                    0,
                                    ExternalDamage.SourceKind.NONE));
                        }
                    }
                    case 4 ->
                        p.h.submit(
                                PlayerSlot.ADMIN,
                                new SpawnBuilding(
                                        ProdScene.HOUSE,
                                        new FactionSlot(0),
                                        20 + rng.nextInt(30),
                                        20 + rng.nextInt(30),
                                        0));
                    default -> {}
                }
                p.h.step();
                int[] scan = p.scanned(0);
                assertThat(p.s.population().used(0))
                        .as("seed %d tick %d: used", seed, t)
                        .isEqualTo(scan[0]);
                assertThat(p.s.population().housing(0))
                        .as("seed %d tick %d: housing", seed, t)
                        .isEqualTo(scan[1]);
            }
        }
    }

    @Test
    void trainingWaitsAtTheCapAndResumes() {
        ProdScene p = ProdScene.of(5_000, 0);
        p.h.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(40, 40), 19, 4));
        p.h.step();
        assertThat(p.s.population().used(0)).isEqualTo(19);
        assertThat(p.s.population().cap(0)).as("the centre's twenty").isEqualTo(20);
        assertThat(p.train().outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(p.train().outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        p.h.runTicks(201); // the first worker's 10 s
        assertThat(p.s.population().used(0)).isEqualTo(20);
        assertThat(p.s.buildings().queueSize(p.centre)).isEqualTo(1);
        assertThat(p.s.buildings().reserved(p.centre)).as("the second waits").isFalse();
        p.h.runTicks(100);
        long notices = p.h.events(Notice.class).stream()
                .filter(n -> n.kind() == NoticeKind.HOUSING_NEEDED)
                .count();
        assertThat(notices).as("told once").isEqualTo(1);
        assertThat(p.s.buildings().trained(p.centre))
                .as("no progress while waiting")
                .isZero();
        p.h.submit(PlayerSlot.ADMIN, new SpawnBuilding(ProdScene.HOUSE, new FactionSlot(0), 30, 30, 0));
        p.h.step();
        assertThat(p.s.population().cap(0)).isEqualTo(25);
        p.h.runTicks(201);
        assertThat(p.s.population().used(0)).as("the second trained").isEqualTo(21);
        assertThat(p.s.buildings().queueSize(p.centre)).isZero();
    }

    @Test
    void theCapNeverExceedsTheMatchMaximum() {
        ProdScene p =
                ProdScene.of(0, 0, ProdScene.withMax(30), VoxelFixtures.flat(64, 64, 32, 1), SimHarness.DEFAULT_SEED);
        for (int k = 0; k < 6; k++) {
            p.h.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(ProdScene.HOUSE, new FactionSlot(0), 20 + 7 * (k % 3), 20 + 7 * (k / 3), 0));
        }
        p.h.step();
        assertThat(p.s.population().housing(0)).as("20 + 6 × 5").isEqualTo(50);
        assertThat(p.s.population().cap(0)).isEqualTo(30);
        int house = p.s.buildings().handles()[1];
        p.s.destroyBuilding(house);
        assertThat(p.s.population().housing(0)).isEqualTo(45);
        assertThat(p.s.population().cap(0)).isEqualTo(30);
        assertThat(p.s.buildings().state(p.centre)).isEqualTo(BuildingStore.COMPLETE);
    }
}
