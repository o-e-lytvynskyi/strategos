package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WritePriority;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ECO-05.6 in L1: a bush gives exactly what is gathered from it and regrows exactly its rate a second, in a remainder
 * kept tick to tick, up to what it was found with; an emptied bush stays empty. Its block's age follows what it holds,
 * written once at discovery and then only on a change; the ledger counts what regrew.
 */
class ForageTest {
    private static List<String> ages(EconScene e, int from) {
        return e
                .h
                .events(OutputEvent.class)
                .subList(from, e.h.events(OutputEvent.class).size())
                .stream()
                .filter(WorldEffect.class::isInstance)
                .map(WorldEffect.class::cast)
                .filter(WorldEffect::propertiesOnly)
                .map(w -> w.x() + "," + w.y() + "," + w.z() + " " + w.state())
                .toList();
    }

    /** Takes {@code amount} from the bush as a gatherer whose load is then lost would, so the ledger balances. */
    private static long take(EconScene e, int bush, long amount) {
        long taken = Forage.take(e.s, bush, amount);
        e.s.ledger().taken(EconScene.FOOD, taken);
        e.s.ledger().dropped(EconScene.FOOD, taken);
        return taken;
    }

    @Test
    void aBushShowsItsAgeFromDiscoveryOn() {
        EconScene e = EconScene.flat(32, 32)
                .bush(10, 10, 60, 0.1)
                .bush(20, 20, 60, 0.1)
                .build();
        assertThat(ages(e, 0)).as("both shown full once found").containsExactly("10,1,10 [age=3]", "20,1,20 [age=3]");
        assertThat(e.h.events(WorldEffect.class))
                .allSatisfy(w -> assertThat(w.priority()).isEqualTo(WritePriority.COSMETIC));
    }

    @Test
    void takenAndRegrownExactly() {
        EconScene e = EconScene.flat(32, 32).bush(10, 10, 60, 0.1).build();
        int bush = 1;
        NodeStore nodes = e.s.nodes();
        assertThat(nodes.regrow(bush)).isEqualTo(100);
        assertThat(take(e, bush, 15_000)).isEqualTo(15_000);
        assertThat(nodes.amount(bush)).isEqualTo(45_000);
        assertThat(Forage.age(nodes, bush)).as("45 of 60: ceil(2.25)").isEqualTo(3);
        assertThat(take(e, bush, 5_001)).isEqualTo(5_001);
        assertThat(Forage.age(nodes, bush)).as("39.999 of 60: ceil(1.99995)").isEqualTo(2);
        e.h.step();
        assertThat(nodes.amount(bush)).as("one tick: 100 a second is 5 a tick").isEqualTo(39_999 + 5);
        e.h.runTicks(999);
        assertThat(nodes.amount(bush))
                .as("a thousand ticks: 50 s at 0.1 a second")
                .isEqualTo(39_999 + 5_000);
        e.h.runTicks(10_000);
        assertThat(nodes.amount(bush)).as("full, and no more").isEqualTo(60_000);
        assertThat(Forage.age(nodes, bush)).isEqualTo(3);
        assertThat(take(e, bush, 1_000_000)).as("all it holds, no more").isEqualTo(60_000);
        assertThat(Forage.age(nodes, bush)).isZero();
    }

    @Test
    void anEmptiedBushStaysEmpty() {
        EconScene e = EconScene.flat(32, 32).bush(10, 10, 60, 0.1).build();
        e.civicCentre(0, 18, 4);
        int worker = e.workers(0, new Vec2d(14, 14), 1, 0)[0];
        e.gather(new int[] {worker}, 10, 1, 10);
        assertThat(e.h.runUntil(() -> e.s.nodes().exhausted(1), 5_000)).isTrue();
        int i = e.index(worker);
        assertThat(e.h.runUntil(() -> e.s.ai().state(i) == dev.strategos.core.sim.UnitState.IDLE, 2_000))
                .as("the last load home, nothing left to pick")
                .isTrue();
        long regrew = e.gathered(0, EconScene.FOOD) - 60_000;
        assertThat(regrew).as("it regrew while it was picked").isPositive();
        assertThat(e.s.ledger().found(EconScene.FOOD) + regrew).isEqualTo(e.gathered(0, EconScene.FOOD));
        e.h.runTicks(4_000);
        assertThat(e.s.nodes().amount(1)).as("no regrowth once empty").isZero();
        assertThat(e.gathered(0, EconScene.FOOD))
                .as("all it gave, what grew while it was picked too")
                .isEqualTo(60_000 + regrew);
        List<String> ages = ages(e, 0);
        assertThat(ages.get(ages.size() - 1)).isEqualTo("10,1,10 [age=0]");
        for (int k = 1; k < ages.size(); k++) {
            assertThat(ages.get(k)).as("written only on a change").isNotEqualTo(ages.get(k - 1));
        }
        assertThat(ages).as("picked down through every age").contains("10,1,10 [age=2]", "10,1,10 [age=1]");
    }
}
