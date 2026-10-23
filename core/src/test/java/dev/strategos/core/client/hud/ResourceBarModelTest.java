package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.WireCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * UI-07.2: after every {@code faction_state}, newer or stale, through the wire, the resource bar shows what the client
 * model holds: each stock in whole units, the workers per resource, the population and its cap, the idle workers.
 */
class ResourceBarModelTest {
    static final List<String> IDS = List.of("food", "wood", "stone", "iron");

    @Test
    void theBarEqualsTheModelAfterEveryState() {
        SplittableRandom rng = new SplittableRandom(11);
        ClientMatchModel model = new ClientMatchModel();
        Income income = new Income();
        long tick = 0;
        for (int k = 0; k < 500; k++) {
            boolean stale = k > 0 && rng.nextInt(10) == 0;
            long at = stale ? tick - 1 - rng.nextInt(5) : (tick += 1 + rng.nextInt(10));
            List<Long> stock = new ArrayList<>();
            List<Long> gathered = new ArrayList<>();
            List<Long> spent = new ArrayList<>();
            List<Integer> workers = new ArrayList<>();
            for (int r = 0; r < IDS.size(); r++) {
                stock.add(rng.nextLong(5_000_000));
                gathered.add(at * 100 + r);
                spent.add(0L);
                workers.add(rng.nextInt(30));
            }
            int cap = rng.nextInt(5, 150);
            FactionState state = new FactionState(
                    2, 0, at, stock, gathered, spent, rng.nextInt(0, cap + 1), cap, rng.nextInt(9), workers);
            FactionState arrived = WireCodec.decode(FactionState.WIRE, WireCodec.encode(FactionState.WIRE, state));
            model.accept(arrived, 2);
            FactionState held = model.faction();
            assertThat(held).isNotNull();
            income.accept(held);
            ResourceBar bar = ResourceBar.of(model.faction(), IDS, income);
            for (int r = 0; r < IDS.size(); r++) {
                ResourceBar.Entry e = bar.resources().get(r);
                assertThat(e.id()).isEqualTo(IDS.get(r));
                assertThat(e.stock()).isEqualTo(held.stock().get(r) / 1000);
                assertThat(e.workers()).isEqualTo(held.workers().get(r));
            }
            assertThat(bar.populationUsed()).isEqualTo(held.populationUsed());
            assertThat(bar.populationCap()).isEqualTo(held.populationCap());
            assertThat(bar.idleWorkers()).isEqualTo(held.idleWorkers());
            assertThat(bar.capped()).isEqualTo(held.populationUsed() >= held.populationCap());
            if (stale) {
                assertThat(held.tick()).as("a stale state is ignored").isEqualTo(tick);
            }
        }
        assertThat(ResourceBar.of(null, IDS, income)).isEqualTo(ResourceBar.EMPTY);
    }

    @Test
    void incomeScalesWhatItSawToAMinute() {
        Income income = new Income();
        income.accept(state(100, 0));
        assertThat(income.perMinute(0)).as("one state is no rate").isZero();
        income.accept(state(400, 30_000));
        assertThat(income.perMinute(0)).as("30 in 300 ticks: 120 a minute").isEqualTo(120_000);
        for (long t = 405; t <= 2_000; t += 5) {
            income.accept(state(t, (t - 100) * 100));
        }
        assertThat(income.perMinute(0)).as("100 a tick").isEqualTo(120_000);
        income.accept(new FactionState(9, 0, 5, List.of(0L), List.of(7L), List.of(0L), 0, 0, 0, List.of(0)));
        assertThat(income.perMinute(0)).as("a new match starts afresh").isZero();
    }

    private static FactionState state(long tick, long gathered) {
        return new FactionState(2, 0, tick, List.of(0L), List.of(gathered), List.of(0L), 0, 0, 0, List.of(0));
    }
}
