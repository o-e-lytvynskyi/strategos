package dev.strategos.mod.server.match;

import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.ai.Levels;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Immutable preflight using the actual M6 setup, scenario and template contracts. It performs no allocation,
 * world mutation, admission or network send. The preparation backend must use it before creating a runtime and
 * refuse a changed template version before installing its commands.
 */
public final class LobbyLaunchPlan {
    private final int factions;
    private final long templateSetHash;
    private final PlayRules play;
    private final DiplomacySetup authority;
    private final List<Command> commands;

    private LobbyLaunchPlan(
            int factions, long templateSetHash, PlayRules play, DiplomacySetup authority, List<Command> commands) {
        this.factions = factions;
        this.templateSetHash = templateSetHash;
        this.play = play;
        this.authority = authority;
        this.commands = List.copyOf(commands);
    }

    public static LobbyLaunchPlan create(
            MatchLobby lobby,
            SkirmishSetup setup,
            Scenario source,
            List<StartSlot> starts,
            TemplateSet templates,
            Set<String> civilisations,
            @Nullable String aiJson) {
        if (lobby.phase() != MatchLobby.Phase.PREPARING || !lobby.allHumansReady()) {
            throw new IllegalStateException("launch requires a frozen ready lobby");
        }
        if (!source.map().equals(setup.map())) throw new IllegalArgumentException("scenario belongs to another map");
        int capacity = lobby.factionCapacity();
        if (starts.size() < capacity) throw new IllegalArgumentException("map has too few faction starts");
        var authority = new DiplomacyState(capacity);
        List<Scenario.Slot> slots = new ArrayList<>(capacity);
        boolean needsAi = false;
        for (int f = 0; f < capacity; f++) {
            MatchLobby.@Nullable Seat first = null;
            for (MatchLobby.Seat seat : lobby.seats()) {
                if (seat.kind() == MatchLobby.Kind.CLOSED || seat.faction().value() != f) continue;
                if (!civilisations.contains(seat.civilisation())) {
                    throw new IllegalArgumentException("unsupported civilisation " + seat.civilisation());
                }
                if (first != null && (first.kind() != seat.kind() || seat.kind() == MatchLobby.Kind.AI)) {
                    throw new IllegalArgumentException("one faction needs human co-op seats or one AI controller");
                }
                if (first == null) first = seat;
                authority = authority.bind(seat.player(), new FactionSlot(f));
            }
            if (first == null) {
                slots.add(new Scenario.Slot("human", Map.of(), List.of()));
                continue;
            }
            if (f >= source.slots().size()) throw new IllegalArgumentException("scenario has too few faction slots");
            authority = authority.withTeam(new FactionSlot(f), first.team());
            boolean ai = first.kind() == MatchLobby.Kind.AI;
            needsAi |= ai;
            Scenario.Slot base = source.slots().get(f);
            slots.add(new Scenario.Slot(
                    ai ? ScenarioSetup.AI + Levels.PROFILE : "human",
                    setup.startResources(base.resources()),
                    base.entities()));
        }
        if (needsAi && aiJson == null) throw new IllegalArgumentException("AI strategy is not available");
        @Nullable String script = needsAi
                ? Levels.resolve(
                        Objects.requireNonNull(aiJson), setup.level().name().toLowerCase(Locale.ROOT))
                : null;
        Scenario scenario = new Scenario(source.map(), source.victory(), slots);
        List<Command> commands = new ArrayList<>(ScenarioSetup.commands(
                scenario, starts, templates, profile -> profile.equals(Levels.PROFILE) ? script : null));
        commands.add(new RuleChange("populationMax", String.valueOf(setup.populationCap())));
        return new LobbyLaunchPlan(
                capacity,
                templates.hash(),
                Skirmishes.play(setup),
                new DiplomacySetup(authority.lockTeams().snapshot()),
                commands);
    }

    public int factions() {
        return factions;
    }

    public long templateSetHash() {
        return templateSetHash;
    }

    public PlayRules play() {
        return play;
    }

    public DiplomacySetup authority() {
        return authority;
    }

    public List<Command> commands() {
        return commands;
    }
}
