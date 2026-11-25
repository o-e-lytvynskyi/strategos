package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.prod.Techs;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-08 in L1 (M9 §2.12): no placement a computer player sends is refused for territory, over a hundred seeded short
 * matches; and its second civic centre in Town goes to neutral land, beyond the centres' spacing.
 */
class AiTerritoryPlacementTest {
    static final int MATCHES = 100;

    static final String SKIRMISH = HeadlessMatch.read("ai/skirmish.json");

    /** Two game minutes: the build order's storehouses, houses, fields and barracks are all placed by then. */
    static final long TICKS = 2_400;

    @Test
    void noPlacementIsRefusedForTerritory() {
        long placements = 0;
        long refused = 0;
        for (long seed = 1; seed <= MATCHES; seed++) {
            long[] count = {0, 0};
            HeadlessMatch.run(
                    seed,
                    TICKS,
                    SKIRMISH,
                    (h, out) -> {
                        for (var e : out.events()) {
                            if (e instanceof CommandResult r && r.kind().equals("strategos:place_building")) {
                                count[0]++;
                                if (r.firstReason() == RejectReason.OUTSIDE_TERRITORY) {
                                    count[1]++;
                                }
                            }
                        }
                    },
                    false);
            placements += count[0];
            refused += count[1];
        }
        assertThat(placements).as("the matches did place buildings").isGreaterThan(MATCHES);
        assertThat(refused).as("placements refused for territory").isZero();
    }

    /** A bot with no build order of its own and no wave: once in Town with a stock, only the expansion acts. */
    static final String EXPANDER = "{\"maxWorkers\":0,\"ratio\":{},\"build\":[],\"housingMargin\":0,\"soldierQueue\":0,"
            + "\"attack\":{\"first\":100000,\"every\":100000,\"min\":4,\"muster\":24,\"announce\":30},\"sites\":[]}";

    @Test
    void theSecondCentreInTownGoesToNeutralLand() {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        MatchState s = KernelAccess.state(h.core());
        BattleBotTest.spawn(h, TemplateSet.WORKER, 0, 14.5, 14.5, 6);
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), resource, 5_000));
        }
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "expander", EXPANDER));
        h.runTicks(40);
        assertThat(centres(h)).as("one centre before Town: the limit holds").hasSize(1);

        s.progress().complete(0, new TechId("strategos:phase_town"));
        Techs.reapply(s, null);
        h.runTicks(400);

        List<BuildingView> centres = centres(h);
        assertThat(centres).as("the second centre is placed").hasSize(2);
        AiView view = new AiView(h.core().queries(), 0, new AiBudget(AiBudget.CAP));
        BuildingView first = centres.get(0);
        BuildingView second = centres.get(1);
        double dx = second.footprint().anchorX() - first.footprint().anchorX();
        double dz = second.footprint().anchorZ() - first.footprint().anchorZ();
        assertThat(Math.hypot(dx, dz)).as("beyond the centres' spacing").isGreaterThanOrEqualTo(64);
        assertThat(view.territoryOwner(
                        second.footprint().anchorX() + second.footprint().spanX() / 2,
                        second.footprint().anchorZ() + second.footprint().spanZ() / 2))
                .as("on land that was no one's when it was placed, or its own now")
                .isIn(-1, 0);
        assertThat(h.results().stream()
                        .filter(r -> r.firstReason() == RejectReason.OUTSIDE_TERRITORY)
                        .count())
                .isZero();
    }

    private static List<BuildingView> centres(SimHarness h) {
        return h.core().queries().buildings().stream()
                .filter(b -> b.faction() == 0 && b.template().equals(BattleBotTest.CENTRE))
                .toList();
    }
}
