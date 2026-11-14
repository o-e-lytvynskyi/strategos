package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.Requirements;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchFactionView;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UI-13: the phase panel's rows are the server's own checklist, fed by what the wire carried to the client. */
class PhasePanelTest {
    static final TemplateSet T = TemplateSet.builtin();
    static final TechId TOWN = new TechId("strategos:phase_town");
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final PlayerSlot P0 = new PlayerSlot(0);
    static final FactionSlot F0 = new FactionSlot(0);
    static final int MATCH = 4;

    private static SimHarness match() {
        SimHarness h = SimHarness.builder().size(64, 64).yBand(0, 31, 1).build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, F0, 2, 2, 0));
        h.step();
        return h;
    }

    private static void houses(SimHarness h, int complete, int foundations) {
        for (int i = 0; i < complete + foundations; i++) {
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, F0, 14 + 10 * (i % 4), 14 + 10 * (i / 4), 0));
            h.step();
            if (i >= complete) {
                int[] all = dev.strategos.core.sim.KernelAccess.state(h.core())
                        .buildings()
                        .handles();
                dev.strategos.core.sim.KernelAccess.state(h.core())
                        .buildings()
                        .setState(all[all.length - 1], BuildingStore.FOUNDATION, 0);
            }
        }
    }

    private static void grant(SimHarness h, long amount) {
        for (String r : List.of("food", "wood", "stone", "iron")) {
            h.submit(PlayerSlot.ADMIN, new Grant(F0, r, amount));
        }
        h.step();
    }

    /** The panel the client builds from the wire against the server's evaluation of the same match. */
    private static void assertMirrors(String name, SimHarness h, boolean phaseAhead) {
        BuildingState wireBuildings = WireCodec.decode(
                BuildingState.WIRE,
                WireCodec.encode(BuildingState.WIRE, ReplicationService.buildingState(h.queries(), MATCH, F0)));
        FactionState wireFaction = WireCodec.decode(
                FactionState.WIRE,
                WireCodec.encode(FactionState.WIRE, ReplicationService.factionState(h.queries(), MATCH, F0)));
        ClientFactionView client = new ClientFactionView(T, wireFaction, wireBuildings.buildings());
        MatchFactionView server = new MatchFactionView(dev.strategos.core.sim.KernelAccess.state(h.core()), 0);
        TechDef town = T.tech(TOWN);
        List<Requirements.Leaf> expected = Requirements.checklist(town.requires(), server);
        assertThat(Requirements.checklist(town.requires(), client)).as(name).isEqualTo(expected);
        assertThat(Requirements.evaluate(town.requires(), client))
                .as(name)
                .isEqualTo(Requirements.evaluate(town.requires(), server));
        assertThat(client.researched(TOWN)).as(name).isEqualTo(server.researched(TOWN));

        BuildingState.Entry centre = wireBuildings.buildings().stream()
                .filter(e -> e.template() == T.byId(CENTRE).netIndex())
                .findFirst()
                .orElseThrow();
        PhasePanel panel = PhasePanel.of(T, client, centre);
        if (!phaseAhead) {
            assertThat(panel).as(name).isNull();
            return;
        }
        assertThat(panel).as(name).isNotNull();
        assertThat(panel.phase().id()).isEqualTo(TOWN);
        assertThat(panel.rows())
                .as(name)
                .extracting(PhasePanel.Row::met, PhasePanel.Row::have, PhasePanel.Row::need)
                .containsExactlyElementsOf(expected.stream()
                        .map(l -> org.assertj.core.groups.Tuple.tuple(l.met(), l.have(), l.need()))
                        .toList());
        assertThat(panel.rows()).as(name).hasSize(expected.size());
    }

    @Test
    void theChecklistMirrorsTheServer() {
        for (int n : new int[] {0, 3, 4, 5, 7}) {
            SimHarness h = match();
            houses(h, n, 0);
            assertMirrors(n + " houses", h, true);
        }
        SimHarness sites = match();
        houses(sites, 4, 1);
        assertMirrors("four and a foundation", sites, true);

        SimHarness poor = match(); // resources are no leaf: short stock changes no row
        houses(poor, 5, 0);
        assertMirrors("five houses, empty treasury", poor, true);

        SimHarness done = match();
        houses(done, 5, 0);
        grant(done, 2_000);
        done.submit(P0, new Research(firstCentre(done), TOWN));
        boolean researched =
                done.runUntil(() -> !done.events(TechResearched.class).isEmpty(), 4_000);
        assertThat(researched).isTrue();
        assertMirrors("Town researched", done, false);
    }

    @Test
    void anEnemyBuildingOrAFoundationIsNeverCounted() {
        SimHarness h = match();
        houses(h, 4, 0);
        for (int i = 0; i < 3; i++) {
            h.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, new FactionSlot(1), 40 + 6 * i, 40, 0));
            h.step();
        }
        assertMirrors("rival houses", h, true);
        List<PhasePanel.Row> rows = new ArrayList<>();
        BuildingState wire = ReplicationService.buildingState(h.queries(), MATCH, F0);
        BuildingState.Entry centre = wire.buildings().get(0);
        PhasePanel panel = PhasePanel.of(T, new ClientFactionView(T, null, wire.buildings()), centre);
        rows.addAll(panel.rows());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).have()).isEqualTo(4);
        assertThat(rows.get(0).met()).isFalse();
    }

    private static int firstCentre(SimHarness h) {
        return ReplicationService.buildingState(h.queries(), MATCH, F0).buildings().stream()
                .filter(e -> e.template() == T.byId(CENTRE).netIndex())
                .findFirst()
                .orElseThrow()
                .handle();
    }
}
