package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CIV-01's bonuses: faction modifiers under {@link CivBonuses#SOURCE}, named by the breakdown, derived afresh. */
class CivBonusBreakdownTest {
    private static final List<CivId> LEAN_VS_DEFAULT = List.of(CivScene.LEAN, CivId.DEFAULT);

    private static double rate(SimHarness h, int faction) {
        MatchState s = KernelAccess.state(h.core());
        int worker = s.templates().byId(CivScene.WORKER).netIndex();
        return s.stats().blockValue(worker, faction, s.stats().path(CivScene.RATE));
    }

    private static StatBreakdown breakdown(SimHarness h, int faction) {
        MatchState s = KernelAccess.state(h.core());
        int worker = s.templates().byId(CivScene.WORKER).netIndex();
        StatBreakdown b = s.stats().blockBreakdown(worker, faction, CivScene.RATE);
        assertThat(b).isNotNull();
        return b;
    }

    @Test
    void aBonusChangesTheStatOfItsFactionOnlyAndTheBreakdownNamesTheCivilisation() {
        SimHarness h = CivScene.match(CivScene.lean(CivScene.WOOD_BONUS), LEAN_VS_DEFAULT);
        assertThat(rate(h, 0)).isEqualTo(0.6 * 1.5);
        assertThat(rate(h, 1)).as("a default faction has none").isEqualTo(0.6);

        StatBreakdown b = breakdown(h, 0);
        assertThat(b.modifiers()).hasSize(1);
        int source = b.modifiers().get(0).sourceId();
        assertThat(source).isEqualTo(CivBonuses.SOURCE).isEqualTo(Integer.MIN_VALUE + 1);
        assertThat(h.queries().civOf(source, new FactionSlot(0)).id()).isEqualTo(CivScene.LEAN);
        assertThat(h.queries().civOf(7, new FactionSlot(0)))
                .as("an id that is no civilisation's")
                .isNull();
        assertThat(breakdown(h, 1).modifiers()).isEmpty();
    }

    @Test
    void theBonusSurvivesARestoreAndTheHashHolds() {
        SimHarness h = CivScene.match(CivScene.lean(CivScene.WOOD_BONUS), LEAN_VS_DEFAULT);
        h.runTicks(5);
        SimHarness back = SimHarness.restore(h.core().snapshot(), CivScene.lean(CivScene.WOOD_BONUS));
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        assertThat(rate(back, 0)).isEqualTo(0.6 * 1.5);
        assertThat(breakdown(back, 0).modifiers()).hasSize(1);
        assertThat(rate(back, 1)).isEqualTo(0.6);
    }

    @Test
    void theBonusIsDerivedAfreshAtAContentSwap() {
        SimHarness h = CivScene.match(CivScene.lean(CivScene.WOOD_BONUS), LEAN_VS_DEFAULT);
        TemplateSet stronger = CivScene.lean(CivScene.WOOD_BONUS.replace("1.5", "2.0"));
        h.schedule(h.tick() + 1, new ContentSwap(stronger));
        h.runTicks(2);
        assertThat(KernelAccess.state(h.core()).templates()).isSameAs(stronger);
        assertThat(rate(h, 0)).as("the new set's bonus, not stacked on the old").isEqualTo(0.6 * 2.0);
        assertThat(breakdown(h, 0).modifiers()).hasSize(1);
        assertThat(rate(h, 1)).isEqualTo(0.6);
    }
}
