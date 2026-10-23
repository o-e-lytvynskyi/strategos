package dev.strategos.core.sim.prod;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CancelTrain;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * PROD-01 in L1: seeded queues, cancels and completions lose and duplicate no food (the stock, what was spent and
 * refunded, and the units trained always add up); a sixth entry is refused; a full cap pauses the head and keeps its
 * progress; a trained unit stands outside the footprint by the door.
 */
class ProductionTest {
    @Test
    void noResourceLostOrDuplicated() {
        int everTrained = 0;
        for (long seed = 1; seed <= 20; seed++) {
            ProdScene p = ProdScene.of(10_000, 0);
            SplittableRandom rng = new SplittableRandom(seed);
            int trained = 0;
            for (int t = 0; t < 1_500; t++) {
                int roll = rng.nextInt(30);
                if (roll == 0) {
                    p.h.submit(ProdScene.P0, new Train(p.centre, TemplateSet.WORKER));
                } else if (roll == 1 && p.s.buildings().queueSize(p.centre) > 0) {
                    p.h.submit(
                            ProdScene.P0,
                            new CancelTrain(
                                    p.centre, rng.nextInt(p.s.buildings().queueSize(p.centre))));
                }
                trained += (int) p.h.step().events().stream()
                        .filter(UnitCreated.class::isInstance)
                        .count();
                long stock = p.s.treasury().stock(0, 0);
                long spent = p.s.treasury().spent(0, 0);
                long refunded = p.s.treasury().refunded(0, 0);
                long queued = p.s.buildings().queueSize(p.centre) * 60_000L;
                assertThat(stock + queued + trained * 60_000L)
                        .as("seed %d tick %d: the food in stock, queued and trained", seed, t)
                        .isEqualTo(10_000_000L);
                assertThat(spent - refunded).isEqualTo(queued + trained * 60_000L);
            }
            everTrained += trained;
        }
        assertThat(everTrained)
                .as("the streams trained units too, not only cancelled them")
                .isGreaterThan(20);
    }

    @Test
    void aSixthEntryIsRefused() {
        ProdScene p = ProdScene.of(1_000, 0);
        for (int k = 0; k < 5; k++) {
            assertThat(p.train().outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        }
        CommandResult sixth = p.train();
        assertThat(sixth.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(sixth.firstReason()).isEqualTo(RejectReason.QUEUE_FULL);
        assertThat(p.s.treasury().stock(0, 0)).as("the sixth paid nothing").isEqualTo(1_000_000L - 5 * 60_000L);
        CommandResult broke = ProdScene.of(59, 0).train();
        assertThat(broke.firstReason()).isEqualTo(RejectReason.NOT_ENOUGH_RESOURCES);
        assertThat(broke.missing()).containsExactly(1_000L, 0L, 0L, 0L);
    }

    @Test
    void aFullCapPausesTheHeadKeepingProgress() {
        ProdScene p = ProdScene.of(1_000, 0);
        assertThat(p.train().outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        p.h.runTicks(100);
        long halfway = p.s.buildings().trained(p.centre);
        assertThat(halfway).isBetween(95L, 105L);
        p.h.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(40, 40), 19, 4));
        p.h.runTicks(300);
        assertThat(p.s.population().used(0)).as("19 and the head's reservation").isEqualTo(20);
        assertThat(p.s.buildings().queueSize(p.centre))
                .as("its unit trained: the reservation was held")
                .isZero();
        assertThat(p.train().outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        p.h.runTicks(50);
        assertThat(p.s.buildings().reserved(p.centre)).isFalse();
        assertThat(p.s.buildings().trained(p.centre))
                .as("a waiting head keeps what it had: none yet")
                .isZero();
    }

    @Test
    void aTrainedUnitStandsOutsideByTheDoor() {
        ProdScene p = ProdScene.of(
                1_000,
                0,
                dev.strategos.core.sim.MatchRules.DEFAULTS,
                VoxelFixtures.flat(64, 64, 32, 1),
                SimHarness.DEFAULT_SEED);
        p.train();
        UnitCreated made = null;
        for (int t = 0; t < 250 && made == null; t++) {
            made = p.h.step().events().stream()
                    .filter(UnitCreated.class::isInstance)
                    .map(UnitCreated.class::cast)
                    .findFirst()
                    .orElse(null);
        }
        assertThat(made).isNotNull();
        var at = p.s.footprintOf(p.centre);
        int x = (int) Math.floor(made.x());
        int z = (int) Math.floor(made.z());
        boolean inside = x >= at.anchorX()
                && x < at.anchorX() + at.spanX()
                && z >= at.anchorZ()
                && z < at.anchorZ() + at.spanZ();
        assertThat(inside)
                .as("outside the footprint at %s, %s", made.x(), made.z())
                .isFalse();
        var door = p.s.blueprintOf(p.centre).marker("door");
        assertThat(door).isNotNull();
        double dx = made.x() - (at.worldX(door.x(), door.z()) + 0.5);
        double dz = made.z() - (at.worldZ(door.x(), door.z()) + 0.5);
        assertThat(Math.hypot(dx, dz)).as("by the door").isLessThanOrEqualTo(1.5);
        assertThat(made.y()).as("on the ground").isEqualTo(1.0);
        assertThat(PlayerSlot.ADMIN).isNotNull();
    }
}
