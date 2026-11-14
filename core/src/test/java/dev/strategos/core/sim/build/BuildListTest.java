package dev.strategos.core.sim.build;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.prod.Techs;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BLD-17 and CMB-09 in L1 (spec rows 29 and 52): a worker places the buildings its {@code Builder/buildings} names; a
 * Village faction's list leaves out the tower and the arsenal, Town's {@code TOKENS} effect adds them to the faction that
 * reached it and to no other, and the build page shows them greyed for Town until then.
 */
class BuildListTest {
    static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    static final TemplateId ARSENAL = new TemplateId("strategos:building/arsenal");
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TechId TOWN = new TechId("strategos:phase_town");
    static final PlayerSlot P0 = new PlayerSlot(0);
    static final PlayerSlot P1 = new PlayerSlot(1);

    final SimHarness h =
            SimHarness.builder().size(96, 96).yBand(0, 31, 1).factions(2).build();
    final MatchState s;

    BuildListTest() {
        h.publishAll(VoxelFixtures.flat(96, 96, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
        for (int f = 0; f < 2; f++) {
            for (String r : List.of("wood", "stone", "iron", "food")) {
                h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(f), r, 5_000));
            }
        }
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 20, 20, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 70, 70, 0));
        h.step();
    }

    CommandResult place(PlayerSlot who, TemplateId template, int x, int z) {
        h.submit(who, new PlaceBuilding(template, x, z, 0, UnitSet.of(), QueueMode.REPLACE));
        h.step();
        return h.results().get(h.results().size() - 1);
    }

    void townFor(int faction) {
        s.progress().complete(faction, TOWN);
        Techs.reapply(s, null); // Town's tokens join the list
    }

    @Test
    void aVillageFactionPlacesNeitherTheTowerNorTheArsenal() {
        for (TemplateId t : List.of(TOWER, ARSENAL)) {
            CommandResult refused = place(P0, t, 40, 40);
            assertThat(refused.outcome()).as(t.value()).isEqualTo(CommandOutcome.REJECTED);
            assertThat(refused.firstReason()).isEqualTo(RejectReason.REQUIREMENTS);
        }
        assertThat(s.buildings().handles()).as("nothing was laid, nothing paid").hasSize(2); // the two centres
        assertThat(place(P0, HOUSE, 40, 40).outcome())
                .as("a house is on the Village list")
                .isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void townAddsBothToItsOwnFactionsList() {
        townFor(0);
        assertThat(place(P0, TOWER, 40, 40).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(place(P0, ARSENAL, 60, 20).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        CommandResult other = place(P1, TOWER, 50, 70);
        assertThat(other.firstReason())
                .as("the other faction is still in Village")
                .isEqualTo(RejectReason.REQUIREMENTS);
    }

    @Test
    void theBuildPageShowsThemGreyedUntilTown() {
        TemplateSet set = TemplateSet.builtin();
        int town = set.tech(TOWN).netIndex();
        int worker = set.byId(TemplateSet.WORKER).netIndex();
        for (boolean reached : new boolean[] {false, true}) {
            List<Integer> researched = reached ? List.of(town) : List.of();
            FactionState f = new FactionState(
                    1,
                    0,
                    10,
                    List.of(9_000_000L, 9_000_000L, 9_000_000L, 9_000_000L),
                    List.of(0L, 0L, 0L, 0L),
                    List.of(0L, 0L, 0L, 0L),
                    3,
                    20,
                    1,
                    List.of(0, 0, 0, 0),
                    researched,
                    reached ? 1 : 0);
            CommandCard page = CommandCard.of(
                    new CommandCard.Input(
                            set, List.of("food", "wood", "stone", "iron"), f, List.of(worker), true, List.of(), false),
                    null);
            for (TemplateId t : List.of(TOWER, ARSENAL)) {
                CommandCard.Button b = page.buttons().stream()
                        .filter(x -> x.kind() == CommandCard.Kind.BUILD
                                && x.template() == set.byId(t).netIndex())
                        .findFirst()
                        .orElseThrow();
                assertThat(b.affordable())
                        .as("%s after Town: %s", t.value(), reached)
                        .isEqualTo(reached);
                assertThat(b.reason()).isEqualTo(reached ? null : "Needs the Town phase");
            }
        }
    }

    @Test
    void theCycleKeyOffersOnlyWhatTheListReaches() {
        TemplateSet set = TemplateSet.builtin();
        int tower = set.byId(TOWER).netIndex();
        int arsenal = set.byId(ARSENAL).netIndex();
        int house = set.byId(HOUSE).netIndex();
        FactionState village = faction(set, false);
        assertThat(CommandCard.placeable(set, village)).contains(house).doesNotContain(tower, arsenal);
        assertThat(CommandCard.placeable(set, faction(set, true))).contains(house, tower, arsenal);
        assertThat(CommandCard.placeable(set, null)).doesNotContain(tower, arsenal);
    }

    private static FactionState faction(TemplateSet set, boolean town) {
        return new FactionState(
                1,
                0,
                10,
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 0L, 0L, 0L),
                0,
                20,
                0,
                List.of(0, 0, 0, 0),
                town ? List.of(set.tech(TOWN).netIndex()) : List.of(),
                town ? 1 : 0);
    }
}
