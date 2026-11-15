package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LobbyLaunchPlanTest {
    private static final String CIV = "strategos:placeholder";
    private static final List<StartSlot> STARTS = List.of(new StartSlot(8, 1, 8, 0), new StartSlot(24, 1, 24, 180));
    private static final Set<String> CIVS = Set.of(CIV);

    private static MatchLobby.Seat seat(int player, MatchLobby.Kind kind, int faction) {
        return new MatchLobby.Seat(
                new PlayerSlot(player),
                kind,
                kind == MatchLobby.Kind.HUMAN ? new UUID(7, player + 1) : null,
                new FactionSlot(faction),
                faction,
                CIV,
                faction);
    }

    private static MatchLobby ready(List<MatchLobby.Seat> seats) {
        MatchLobby lobby = MatchLobby.create(2, seats);
        for (MatchLobby.Seat seat : seats) {
            UUID member = seat.occupant();
            if (member != null) lobby = lobby.withReady(member, true, 0);
        }
        return lobby.prepare();
    }

    private static Scenario scenario() {
        return new Scenario(
                SkirmishSetup.FIRST_SKIRMISH,
                "civic_centre",
                List.of(
                        new Scenario.Slot("human", Map.of("food", 50L), List.of()),
                        new Scenario.Slot("ai:skirmish", Map.of("food", 70L), List.of())));
    }

    private static LobbyLaunchPlan plan(MatchLobby lobby, @Nullable String ai) {
        return LobbyLaunchPlan.create(
                lobby, SkirmishSetup.DEFAULTS, scenario(), STARTS, TemplateSet.builtin(), CIVS, ai);
    }

    @Test
    void fourCoopMembersInitializeEachFactionOnceAndKeepDistinctPlayerBindings() {
        MatchLobby lobby = ready(List.of(
                seat(0, MatchLobby.Kind.HUMAN, 0),
                seat(1, MatchLobby.Kind.HUMAN, 0),
                seat(2, MatchLobby.Kind.HUMAN, 1),
                seat(3, MatchLobby.Kind.HUMAN, 1)));
        LobbyLaunchPlan plan = plan(lobby, null);
        assertThat(plan.commands().stream().filter(Grant.class::isInstance).toList())
                .containsExactly(new Grant(new FactionSlot(0), "food", 50), new Grant(new FactionSlot(1), "food", 70));
        assertThat(plan.commands()).contains(new RuleChange("populationMax", "150"));
        assertThat(plan.authority().snapshot().teamsLocked()).isTrue();
        var authority = dev.strategos.core.sim.diplomacy.DiplomacyState.restore(
                plan.authority().snapshot());
        for (int p = 0; p < 4; p++) {
            assertThat(authority.factionOf(new PlayerSlot(p))).contains(new FactionSlot(p / 2));
        }
    }

    @Test
    void capacityScenarioAndCivilisationMustBeSupportedBeforeAllocation() {
        MatchLobby lobby = ready(List.of(seat(0, MatchLobby.Kind.HUMAN, 0), seat(1, MatchLobby.Kind.HUMAN, 1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LobbyLaunchPlan.create(
                        lobby,
                        SkirmishSetup.DEFAULTS,
                        scenario(),
                        STARTS.subList(0, 1),
                        TemplateSet.builtin(),
                        CIVS,
                        null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LobbyLaunchPlan.create(
                        lobby, SkirmishSetup.DEFAULTS, scenario(), STARTS, TemplateSet.builtin(), Set.of(), null));
        Scenario shortScenario = new Scenario(
                SkirmishSetup.FIRST_SKIRMISH, "civic_centre", scenario().slots().subList(0, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LobbyLaunchPlan.create(
                        lobby, SkirmishSetup.DEFAULTS, shortScenario, STARTS, TemplateSet.builtin(), CIVS, null));
    }

    @Test
    void editingLobbyAndAnotherMapsScenarioCannotLaunch() {
        MatchLobby editing = MatchLobby.create(2, List.of(seat(0, MatchLobby.Kind.HUMAN, 0)));
        assertThatIllegalStateException().isThrownBy(() -> plan(editing, null));
        MatchLobby frozen = ready(editing.seats());
        Scenario other =
                new Scenario("strategos:other", "civic_centre", scenario().slots());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LobbyLaunchPlan.create(
                        frozen, SkirmishSetup.DEFAULTS, other, STARTS, TemplateSet.builtin(), CIVS, null));
    }

    @Test
    void closedFactionDoesNotGetResourcesAndHighResourcesUseM6Scaling() {
        MatchLobby lobby = ready(List.of(seat(0, MatchLobby.Kind.HUMAN, 0), seat(1, MatchLobby.Kind.CLOSED, 1)));
        SkirmishSetup high = new SkirmishSetup(
                SkirmishSetup.FIRST_SKIRMISH,
                SkirmishSetup.Level.NORMAL,
                SkirmishSetup.StartResources.HIGH,
                100,
                SkirmishSetup.Visibility.REVEALED);
        LobbyLaunchPlan plan =
                LobbyLaunchPlan.create(lobby, high, scenario(), STARTS, TemplateSet.builtin(), CIVS, null);
        assertThat(plan.commands().stream().filter(Grant.class::isInstance).toList())
                .containsExactly(new Grant(new FactionSlot(0), "food", 100));
        assertThat(plan.commands()).contains(new RuleChange("populationMax", "100"));
        assertThat(plan.play().revealMap()).isTrue();
    }

    @Test
    void unknownScenarioTemplatesAreRejectedByTheActualM6CommandBuilder() {
        MatchLobby lobby = ready(List.of(seat(0, MatchLobby.Kind.HUMAN, 0), seat(1, MatchLobby.Kind.HUMAN, 1)));
        Scenario invalid = new Scenario(
                SkirmishSetup.FIRST_SKIRMISH,
                "civic_centre",
                List.of(
                        new Scenario.Slot(
                                "human",
                                Map.of(),
                                List.of(new Scenario.Entity(
                                        new dev.strategos.core.shared.TemplateId("strategos:missing"), 0, 0, 1, 1, 0))),
                        scenario().slots().get(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LobbyLaunchPlan.create(
                        lobby, SkirmishSetup.DEFAULTS, invalid, STARTS, TemplateSet.builtin(), CIVS, null));
    }

    @Test
    void aFactionCannotMixAiAndHumanControllersAndAiNeedsItsActualScript() {
        MatchLobby mixed = ready(List.of(seat(0, MatchLobby.Kind.HUMAN, 0), seat(1, MatchLobby.Kind.AI, 0)));
        assertThatIllegalArgumentException().isThrownBy(() -> plan(mixed, null));
        MatchLobby ai = ready(List.of(seat(0, MatchLobby.Kind.HUMAN, 0), seat(1, MatchLobby.Kind.AI, 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> plan(ai, null));
    }
}
