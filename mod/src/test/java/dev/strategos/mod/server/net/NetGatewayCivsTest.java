package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchConfig;
import dev.strategos.core.sim.MatchRules;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CIV-01: a join lists civilisations only when one is not the default, so an old client reads every other join. */
class NetGatewayCivsTest {
    private static MatchConfig config(List<CivId> civs) {
        return new MatchConfig(
                new MatchId(1), 1L, new MatchBounds(32, 32, 0, 16, 1.0), 2, MatchRules.DEFAULTS, List.of(), civs);
    }

    @Test
    void aMatchOfDefaultsSendsNoCivilisationsWhetherTheListIsEmptyOrExplicit() {
        assertThat(NetGateway.civs(config(List.of()))).isEmpty();
        assertThat(NetGateway.civs(config(List.of(CivId.DEFAULT, CivId.DEFAULT))))
                .isEmpty();
    }

    @Test
    void aMatchWithAnotherCivilisationSendsEveryFactionsId() {
        assertThat(NetGateway.civs(config(List.of(new CivId("t:lean"), CivId.DEFAULT))))
                .containsExactly("t:lean", "strategos:default");
    }
}
