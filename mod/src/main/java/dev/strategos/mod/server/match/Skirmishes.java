package dev.strategos.mod.server.match;

import dev.strategos.core.content.Scenario;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skirmishes to watch (QA-11.5): a match made on a map waits for its import, then is set up as its scenario with every
 * slot played by the computer, and its observer is seated (the admin's slot, which sees everything) and made its
 * commander. Server thread only.
 */
public final class Skirmishes {
    private record Pending(MatchRuntime match, UUID observer, ResourceLocation scenario) {}

    private static final Logger LOG = LoggerFactory.getLogger(Skirmishes.class);

    private final List<Pending> pending = new ArrayList<>();

    /** Once {@code match} runs, it plays {@code scenario} bot against bot, {@code observer} watching. */
    public void observe(MatchRuntime match, UUID observer, ResourceLocation scenario) {
        pending.add(new Pending(match, observer, scenario));
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
                return false;
            }
            try {
                setUp(server, p, core);
            } catch (
                    RuntimeException
                            e) { // a scenario that does not apply leaves the match as made, not the server down
                LOG.warn("Skirmish of match {} not set up: {}", p.match().id(), e.toString());
            }
            return true;
        });
    }

    private static void setUp(StrategosServer server, Pending p, MatchCore core) {
        ResourceLocation map = p.match().map();
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
        Scenarios.apply(
                server.server(),
                p.match(),
                new Scenario(scenario.map(), scenario.victory(), computers),
                map == null
                        ? List.of()
                        : MapPackageLoader.load(server.server(), map).manifest().startSlots(),
                core.queries().templates());
        ServerPlayer observer = server.server().getPlayerList().getPlayer(p.observer());
        if (observer != null) {
            server.matches().join(p.match(), p.observer(), PlayerSlot.ADMIN);
            server.commanders().enter(observer, p.match());
            server.net().joined(p.match(), observer, PlayerSlot.ADMIN);
        }
    }
}
