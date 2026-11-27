package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-01's roster rule (M10 §2.1): production, placement and research refuse what the faction's civilisation lacks and
 * accept it for a faction of the default; a captured building follows its new owner's roster.
 */
class CivRosterTest {
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final PlayerSlot P1 = new PlayerSlot(1);
    private static final List<CivId> LEAN_VS_DEFAULT = List.of(CivScene.LEAN, CivId.DEFAULT);

    private static SimHarness lean() {
        return CivScene.match(CivScene.lean("[]"), LEAN_VS_DEFAULT);
    }

    private static CommandResult last(SimHarness h) {
        return h.results().get(h.results().size() - 1);
    }

    private static CommandResult train(SimHarness h, PlayerSlot who, int building) {
        h.submit(who, new Train(building, CivScene.WORKER));
        h.step();
        return last(h);
    }

    @Test
    void productionRefusesWhatTheRosterLacksAndAcceptsItForTheDefault() {
        SimHarness h = lean();
        int[] centres = KernelAccess.state(h.core()).buildings().handles();
        assertThat(train(h, P0, centres[0]).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(train(h, P1, centres[1]).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void placementRefusesWhatTheRosterLacksAndAcceptsItForTheDefault() {
        SimHarness h = lean();
        MatchState s = KernelAccess.state(h.core());
        int farmstead = s.templates().byId(CivScene.FARMSTEAD).netIndex();
        int house =
                s.templates().byId(new TemplateId("strategos:building/house")).netIndex();
        assertThat(Placing.listed(s, farmstead, 0)).isFalse();
        assertThat(Placing.listed(s, farmstead, 1)).isTrue();
        assertThat(Placing.listed(s, house, 0)).as("what its roster has").isTrue();

        h.submit(P0, new PlaceBuilding(CivScene.FARMSTEAD, 12, 12, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        assertThat(last(h).firstReason()).isEqualTo(RejectReason.REQUIREMENTS);
        h.submit(P1, new PlaceBuilding(CivScene.FARMSTEAD, 42, 42, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        assertThat(last(h).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void researchRefusesATechTheRosterLacksAndAcceptsItForTheDefault() {
        SimHarness h = lean();
        int shed0 = CivScene.building(h, 0, CivScene.STOREHOUSE, 14, 14);
        int shed1 = CivScene.building(h, 1, CivScene.STOREHOUSE, 40, 40);
        h.submit(P0, new Research(shed0, CivScene.WOOD_1));
        h.step();
        assertThat(last(h).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        h.submit(P1, new Research(shed1, CivScene.WOOD_1));
        h.step();
        assertThat(last(h).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void aCapturedBuildingTrainsOnlyItsNewOwnersRoster() {
        SimHarness h = lean();
        MatchState s = KernelAccess.state(h.core());
        int[] centres = s.buildings().handles();
        BuildingLife.transfer(s, centres[1], 0); // the default's centre goes to the lean faction
        assertThat(train(h, P0, centres[1]).firstReason())
                .as("the roster is the owner's, not the building's")
                .isEqualTo(RejectReason.UNSUPPORTED);
        BuildingLife.transfer(s, centres[0], 1); // and the lean faction's own to the default
        assertThat(train(h, P1, centres[0]).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void aMatchOfDefaultsRefusesNothing() {
        SimHarness h = CivScene.match(TemplateSet.builtin(), List.of());
        int[] centres = KernelAccess.state(h.core()).buildings().handles();
        assertThat(train(h, P0, centres[0]).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void aRulesChangeKeepsTheCivilisationsTheHashTheSnapshotAndTheRoster() {
        SimHarness h = lean();
        h.submit(PlayerSlot.ADMIN, new RuleChange("populationMax", "40"));
        h.step();
        MatchState s = KernelAccess.state(h.core());
        assertThat(s.config().rules().economy().populationMax()).isEqualTo(40);
        assertThat(s.config().civs()).as("the copy keeps them").isEqualTo(LEAN_VS_DEFAULT);
        h.runTicks(5);

        assertThat(h.replay().matches())
                .as("the journal replays to the live hash")
                .isTrue();
        MatchSnapshot snapshot = h.core().snapshot();
        assertThat(snapshot.config().civs()).isEqualTo(LEAN_VS_DEFAULT);
        SimHarness back = SimHarness.restore(snapshot, CivScene.lean("[]"));
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        assertThat(KernelAccess.state(back.core()).config().civs()).isEqualTo(LEAN_VS_DEFAULT);

        int[] centres = s.buildings().handles();
        assertThat(train(h, P0, centres[0]).firstReason()).as("still enforced").isEqualTo(RejectReason.UNSUPPORTED);
        int[] restored = KernelAccess.state(back.core()).buildings().handles();
        assertThat(train(back, P0, restored[0]).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
    }
}
