package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.WorldEffect;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * ECO-09.1 and .2 in L1: n farmers on a field reap {@code rate × (1 − 0.9ⁿ) ÷ 0.1} together, each its share of its
 * rate; a field ripens in 30 s, its crop's age rising, and reaped empty it stays empty, at age 0, until a reseed its
 * faction pays for.
 */
class FieldTest {
    static final TemplateId FIELD = new TemplateId("strategos:building/field");

    /** A field at (20, 20), its corner, beside faction 0's centre. */
    static EconScene field() {
        EconScene e = EconScene.flat(64, 48).build();
        e.civicCentre(0, 2, 2);
        e.building(FIELD, 0, 20, 20);
        return e;
    }

    static int fieldHandle(EconScene e) {
        return e.s.buildings().handles()[1];
    }

    @Test
    void yieldFollowsDiminishingReturns() {
        for (int n = 1; n <= 5; n++) {
            EconScene e = field();
            int node = e.s.buildings().field(fieldHandle(e));
            assertThat(node).isPositive();
            int[] farmers = e.workers(0, new Vec2d(22.5, 22.5), n, 1);
            e.gather(farmers, 22, 1, 22); // its crop, over the floor layer that took the ground's place
            int ticks = 0;
            while (e.s.nodes().ripening(node) > 0 || !allGathering(e, farmers)) {
                e.h.step();
                assertThat(++ticks).as("n=%d: ripe and at work within 40 s", n).isLessThan(800);
            }
            long rate = Math.round(500 * Fields.share(n)); // each farmer's, in thousandths a second
            assertThat(n * rate)
                    .as("n=%d: together 0.5 × (1 − 0.9ⁿ) ÷ 0.1 a second", n)
                    .isCloseTo(
                            Math.round(500 * (1 - DetMath.pow(0.9, n)) / 0.1),
                            org.assertj.core.data.Offset.offset((long) n));
            long before = e.s.ledger().taken(EconScene.FOOD);
            e.h.runTicks(300);
            long reaped = e.s.ledger().taken(EconScene.FOOD) - before;
            assertThat(reaped)
                    .as("n=%d: 300 ticks of %d farmers at %d a second", n, n, rate)
                    .isCloseTo(n * 300 * rate / 20, org.assertj.core.data.Offset.offset((long) n));
        }
    }

    private static boolean allGathering(EconScene e, int[] units) {
        return Arrays.stream(units).allMatch(u -> e.s.ai().state(e.index(u)) == UnitState.GATHERING);
    }

    @Test
    void anEmptyFieldWaitsForAPaidReseed() {
        EconScene e = field();
        int handle = fieldHandle(e);
        int node = e.s.buildings().field(handle);
        NodeStore nodes = e.s.nodes();
        assertThat(nodes.ripening(node))
                .as("a new field ripens 30 s, a tick of it in the step that placed it")
                .isEqualTo(599);
        int seen = e.h.events(WorldEffect.class).size();
        e.h.runTicks(600);
        assertThat(nodes.amount(node)).isEqualTo(300_000);
        assertThat(e
                        .h
                        .events(WorldEffect.class)
                        .subList(seen, e.h.events(WorldEffect.class).size())
                        .stream()
                        .filter(WorldEffect::propertiesOnly)
                        .map(WorldEffect::state)
                        .distinct())
                .as("its crop rose through every age, then stood ripe")
                .containsExactly("[age=1]", "[age=2]", "[age=3]", "[age=4]", "[age=5]", "[age=6]", "[age=7]");
        long taken = Fields.take(e.s, node, 1_000_000);
        e.s.ledger().taken(EconScene.FOOD, taken);
        e.s.ledger().dropped(EconScene.FOOD, taken);
        assertThat(taken).isEqualTo(300_000);
        e.h.runTicks(2_000);
        assertThat(nodes.amount(node)).as("empty, it stays empty").isZero();
        assertThat(nodes.exhausted(node)).isTrue();
        assertThat(Fields.age(nodes, node, 600)).isZero();

        e.h.submit(EconScene.P0, new Reseed(handle));
        e.h.step();
        CommandResult broke = e.h.results().get(e.h.results().size() - 1);
        assertThat(broke.firstReason()).isEqualTo(RejectReason.NOT_ENOUGH_RESOURCES);
        assertThat(broke.missing()).containsExactly(0L, 40_000L, 0L, 0L);
        e.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 100));
        e.h.submit(EconScene.P0, new Reseed(handle));
        e.h.step();
        assertThat(e.h.results().get(e.h.results().size() - 1).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(e.s.treasury().stock(0, EconScene.WOOD))
                .as("the reseed's forty")
                .isEqualTo(60_000);
        assertThat(nodes.ripening(node)).as("ripening again").isPositive();
        e.h.submit(EconScene.P0, new Reseed(handle));
        e.h.step();
        assertThat(e.h.results().get(e.h.results().size() - 1).firstReason())
                .as("a field that ripens is not reseeded")
                .isEqualTo(RejectReason.INVALID_ARGUMENT);
        e.h.runTicks(600);
        assertThat(nodes.amount(node)).isEqualTo(300_000);
        assertThat(e.s.ledger().found(EconScene.FOOD)).as("two sowings found").isEqualTo(600_000);
    }
}
