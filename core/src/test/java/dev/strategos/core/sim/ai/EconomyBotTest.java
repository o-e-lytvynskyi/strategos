package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-02.1–4 in L1: in ten seeded 15-minute matches of the economy script, both computer players complete the build
 * order with no site left waiting a minute with nobody on it; no worker idles longer than 30 s; after minute 3 no
 * resource's share of the gatherers strays more than 10 % from the ratio; neither walls off its own units or the
 * base's nodes; both send gatherers to animals and gather all four resources; and no think spends more than the budget.
 */
class EconomyBotTest {
    static final long MINUTES_15 = 18_000;

    static final long IDLE_MOST = 600;

    static final double SHARE_MOST = 0.10;

    static final long SITE_WAIT_MOST = 1_200;

    @Test
    void tenSeededFifteenMinuteMatches() {
        String json = HeadlessMatch.read("ai/economy.json");
        StrategyScript script = StrategyScript.parse(json);
        List<String> problems = new ArrayList<>();
        for (long seed = 1; seed <= 10; seed++) {
            EconomyWatch w = new EconomyWatch(script);
            HeadlessMatch.Summary m =
                    HeadlessMatch.run(seed, MINUTES_15, json, w, false).summary();
            String at = "seed " + seed + ": ";
            if (m.failed()) {
                problems.add(at + m.failure());
            }
            for (int f = 0; f < 2; f++) {
                String who = at + "faction " + f + " ";
                if (m.buildOrderDoneAt().get(f) < 0) {
                    problems.add(who + "did not complete its build order");
                }
                if (w.longestIdle[f] > IDLE_MOST) {
                    problems.add(who + "left a worker idle " + w.longestIdle[f] / 20 + " s " + w.worstIdle[f]);
                }
                if (w.shareDeviation[f] > SHARE_MOST) {
                    problems.add(who + "strayed " + Math.round(w.shareDeviation[f] * 100) + " % from its ratio at "
                            + w.worstShare[f]);
                }
                if (w.siteWait[f] > SITE_WAIT_MOST) {
                    problems.add(who + "left a site waiting " + w.siteWait[f] / 20 + " s");
                }
                if (!w.livestock[f]) {
                    problems.add(who + "never gathered from an animal");
                }
                if (w.gathered.get(f).stream().anyMatch(g -> g <= 0)) {
                    problems.add(who + "gathered " + w.gathered.get(f));
                }
            }
            for (String wall : w.walledOff) {
                problems.add(at + "walled off " + wall);
            }
            if (m.maxSpent() > AiBudget.CAP) {
                problems.add(at + "a think spent " + m.maxSpent());
            }
        }
        assertThat(problems).isEmpty();
    }
}
