package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * ECO-01.1: over 200 seeded streams of grants through the command path, and payments, refunds and deliveries as the
 * later systems will make them, every faction's treasury balances after every step (the core checks it in FINAL and
 * would throw) and each stock equals a model kept beside it; a payment the stock cannot cover changes nothing.
 */
class TreasuryInvariantTest {
    private static final String[] RESOURCES = {"food", "wood", "stone", "iron"};

    private int refusals;

    @Test
    void theInvariantHoldsOverTwoHundredSeededStreams() {
        for (long seed = 1; seed <= 200; seed++) {
            run(seed);
        }
        assertThat(refusals)
                .as("payments the stock could not cover, across the seeds")
                .isPositive();
    }

    private void run(long seed) {
        MatchRules rules = MatchRules.DEFAULTS.withEconomy(EconomyRules.DEFAULTS.withStart(300, 200, 0, 0));
        SimHarness h = SimHarness.builder().seed(seed).factions(2).rules(rules).build();
        MatchState s = KernelAccess.state(h.core());
        long[][] model = {{300_000, 200_000, 0, 0}, {300_000, 200_000, 0, 0}};
        long[][] paid = new long[2][4];
        int refused = 0;
        for (int tick = 0; tick < 100; tick++) {
            long r = Rng.u64(seed, 9, tick, 0, 0);
            int f = (int) Math.floorMod(r, 2L);
            int res = (int) Math.floorMod(r >>> 8, 4L);
            long amount = 1L + Math.floorMod(r >>> 16, 500L);
            switch ((int) Math.floorMod(r >>> 40, 4L)) {
                case 0 -> {
                    h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), RESOURCES[res], amount));
                    model[f][res] += amount * Amount.UNIT; // applied in the step's COMMANDS phase
                }
                case 1 -> {
                    long[] cost = new long[4];
                    cost[res] = amount * Amount.UNIT;
                    cost[(res + 1) % 4] = Math.floorMod(r >>> 24, 100L) * Amount.UNIT;
                    if (s.treasury().pay(f, cost)) {
                        for (int k = 0; k < 4; k++) {
                            model[f][k] -= cost[k];
                            paid[f][k] += cost[k];
                        }
                    } else {
                        refused++;
                        assertThat(s.treasury().missing(f, cost)).isNotNull();
                    }
                }
                case 2 -> {
                    long back = Math.min(paid[f][res], amount * Amount.UNIT);
                    long[] refund = new long[4];
                    refund[res] = back;
                    s.treasury().refund(f, refund);
                    paid[f][res] -= back;
                    model[f][res] += back;
                }
                default -> {
                    long load = Math.floorMod(r >>> 24, 10_000L); // a load in thousandths, as a worker delivers it
                    s.treasury().gather(f, res, load);
                    model[f][res] += load;
                }
            }
            h.step();
            assertThat(s.treasury().broken())
                    .as("seed " + seed + " tick " + tick)
                    .isNull();
            for (int ff = 0; ff < 2; ff++) {
                for (int k = 0; k < 4; k++) {
                    assertThat(s.treasury().stock(ff, k))
                            .as("seed " + seed + " faction " + ff + " " + RESOURCES[k])
                            .isEqualTo(model[ff][k]);
                }
            }
        }
        refusals += refused;
    }
}
