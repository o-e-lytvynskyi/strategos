package dev.strategos.mod.server.match;

import dev.strategos.core.content.Scenario;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.world.ArenaPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skirmishes. A player's (MATCH-02, spec §2.11): a match on the chosen map in the arena's next slot, under the
 * setup's rules; once imported it is set up as the map's scenario, slot 0 the player, slot 1 the computer, and the
 * player is seated in slot 0 as its commander. One to watch (QA-11.5): every slot played by the computer, its observer
 * seated in the admin's slot. Server thread only.
 */
public final class Skirmishes {
    /** Where a skirmish's match goes: the arena's next slot in a game, a test's own space in a GameTest. */
    public interface Placer {
        MatchRuntime place(ResourceLocation map, MatchOptions options);
    }

    /** A match waiting for its import; {@code setup} is null for one to watch. */
    private record Pending(
            MatchRuntime match,
            UUID player,
            ResourceLocation scenario,
            @Nullable SkirmishSetup setup) {}

    private static final Logger LOG = LoggerFactory.getLogger(Skirmishes.class);

    private final List<Pending> pending = new ArrayList<>();

    /** Once {@code match} runs, it plays {@code scenario} bot against bot, {@code observer} watching. */
    public void observe(MatchRuntime match, UUID observer, ResourceLocation scenario) {
        pending.add(new Pending(match, observer, scenario, null));
    }

    /** {@code player}'s skirmish in the arena's next slot. */
    public MatchRuntime start(StrategosServer server, SkirmishSetup setup, ServerPlayer player) {
        return start(
                server,
                setup,
                player.getUUID(),
                (map, options) -> server.matches().createInArena(map, options));
    }

    /**
     * {@code player}'s skirmish, its match made by {@code placer}: refused when the player already sits in a match, or
     * the map is no package or has no scenario. The match imports first; the setup follows in {@link #tick}.
     */
    public MatchRuntime start(StrategosServer server, SkirmishSetup setup, UUID player, Placer placer) {
        // a skirmish still importing seats nobody yet: its player counts as playing it
        if (server.matches().seatOf(player) != null
                || pending.stream().anyMatch(p -> p.player().equals(player))) {
            throw new MatchRefusedException("you already play a match");
        }
        ResourceLocation map = ResourceLocation.tryParse(setup.map());
        if (map == null || !MapPackageLoader.list(server.server()).contains(map)) {
            throw new MatchRefusedException("no map " + setup.map());
        }
        ResourceLocation scenario = scenarioFor(server, map);
        if (scenario == null) {
            throw new MatchRefusedException("no scenario is made for " + map);
        }
        MatchOptions options = MatchOptions.generated(ArenaPolicy.FLOOR_Y).withPlay(play(setup));
        MatchRuntime rt = placer.place(map, options);
        pending.add(new Pending(rt, player, scenario, setup));
        LOG.info("Skirmish {} on {} for {}: {}", rt.id(), map, player, setup);
        return rt;
    }

    /** The first scenario made for {@code map}, in the data packs' order; null for none. */
    public static @Nullable ResourceLocation scenarioFor(StrategosServer server, ResourceLocation map) {
        for (ResourceLocation id : Scenarios.list(server.server())) {
            if (Scenarios.load(server.server(), id).map().equals(map.toString())) {
                return id;
            }
        }
        return null;
    }

    /** Conquest under the setup's visibility (MATCH-07); the computer does not see through fog. */
    static PlayRules play(SkirmishSetup setup) {
        return new PlayRules(
                PlayRules.Victory.CONQUEST,
                setup.visibility() == SkirmishSetup.Visibility.REVEALED,
                setup.visibility() == SkirmishSetup.Visibility.EXPLORED,
                false);
    }

    /** Each server tick, before the matches step: those imported are set up. */
    public void tick(StrategosServer server) {
        pending.removeIf(p -> {
            MatchLifecycle life = p.match().lifecycle();
            if (life == MatchLifecycle.FAULTED || life == MatchLifecycle.DISPOSED || life == MatchLifecycle.ENDED) {
                return true;
            }
            MatchCore core = p.match().core();
            if (life != MatchLifecycle.RUNNING || core == null) {
                preparing(server, p);
                return false;
            }
            try {
                if (p.setup() == null) {
                    watch(server, p, core);
                } else {
                    play(server, p, p.setup(), core);
                }
            } catch (
                    RuntimeException
                            e) { // a scenario that does not apply leaves the match as made, not the server down
                LOG.warn("Skirmish of match {} not set up: {}", p.match().id(), e.toString());
            }
            return true;
        });
    }

    /** A player's skirmish still importing: its progress on the player's action bar, twice a second. */
    private static void preparing(StrategosServer server, Pending p) {
        MapImporter importer = p.match().importer();
        ServerPlayer player = server.server().getPlayerList().getPlayer(p.player());
        if (p.setup() == null
                || importer == null
                || player == null
                || server.server().getTickCount() % 10 != 0) {
            return;
        }
        int percent = (int) Math.floor(100 * importer.progress());
        player.displayClientMessage(Component.translatable("strategos.skirmish.preparing", percent), true);
    }

    private static void watch(StrategosServer server, Pending p, MatchCore core) {
        Scenario scenario = Scenarios.load(server.server(), p.scenario());
        List<Scenario.Slot> computers = new ArrayList<>();
        // a human slot is played by the battle bot when the scenario's computer slot is one (the battle scenarios),
        // else by first_loop
        String human = scenario.slots().stream().anyMatch(sl -> sl.player().equals(ScenarioSetup.AI + "battle"))
                ? ScenarioSetup.AI + "battle"
                : ScenarioSetup.AI + "first_loop";
        for (Scenario.Slot slot : scenario.slots()) {
            String player = slot.player().startsWith(ScenarioSetup.AI) ? slot.player() : human;
            computers.add(new Scenario.Slot(player, slot.resources(), slot.entities()));
        }
        apply(server, p.match(), new Scenario(scenario.map(), scenario.victory(), computers), core);
        seat(server, p.match(), p.player(), PlayerSlot.ADMIN);
    }

    /**
     * The player's skirmish: the scenario with each slot's resources scaled, the computer's slots as the scenario
     * plays them, the population cap, then the player in slot 0.
     */
    private static void play(StrategosServer server, Pending p, SkirmishSetup setup, MatchCore core) {
        Scenario scenario = Scenarios.load(server.server(), p.scenario());
        List<Scenario.Slot> slots = new ArrayList<>();
        for (Scenario.Slot slot : scenario.slots()) {
            // AFTER-T22: a computer's slot plays ai/skirmish.json at setup.level(), resolved into SetAi's script
            // (§2.20)
            slots.add(new Scenario.Slot(slot.player(), setup.startResources(slot.resources()), slot.entities()));
        }
        apply(server, p.match(), new Scenario(scenario.map(), scenario.victory(), slots), core);
        p.match().submit(PlayerSlot.ADMIN, new RuleChange("populationMax", String.valueOf(setup.populationCap())));
        seat(server, p.match(), p.player(), new PlayerSlot(0));
    }

    private static void apply(StrategosServer server, MatchRuntime rt, Scenario scenario, MatchCore core) {
        ResourceLocation map = rt.map();
        Scenarios.apply(
                server.server(),
                rt,
                scenario,
                map == null
                        ? List.of()
                        : MapPackageLoader.load(server.server(), map).manifest().startSlots(),
                core.queries().templates());
    }

    private static void seat(StrategosServer server, MatchRuntime rt, UUID player, PlayerSlot slot) {
        ServerPlayer online = server.server().getPlayerList().getPlayer(player);
        if (online != null) {
            server.matches().join(rt, player, slot);
            server.commanders().enter(online, rt);
            server.net().joined(rt, online, slot);
        }
    }
}
