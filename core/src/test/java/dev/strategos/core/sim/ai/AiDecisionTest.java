package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-01.2–4 on synthetic states: a computer player with 300 wood and 9 of 10 population builds a house; with food and
 * room it trains workers; an idle worker is sent to gather; a think spends no more than its cap; and its commands
 * pass the validators a human's pass, a refused one refused for the same reason.
 */
class AiDecisionTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    /** The shipped first-loop strategy, read from the mod's data as the server reads it. */
    static String script() throws Exception {
        return Files.readString(Path.of("../mod/src/main/resources/data/strategos/strategos/ai/first_loop.json"));
    }

    /** A 64 × 64 flat match, its rules' population maximum {@code max}, faction 0's centre and {@code workers}. */
    static SimHarness match(int max, int workers) {
        MatchRules d = MatchRules.DEFAULTS;
        SimHarness h = SimHarness.builder()
                .size(64, 64)
                .yBand(0, 31, 1)
                .rules(d.withEconomy(new EconomyRules(d.economy().resources(), max)))
                .build();
        h.publishAll(VoxelFixtures.flat(64, 64, 32, 1));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 8, 8, 0));
        h.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(14, 24), workers, 2));
        h.step();
        h.step();
        return h;
    }

    /** One think of a player with the first-loop script, as faction 0 at the harness's tick; what it sent. */
    static List<Command> think(SimHarness h, int cap, int[] spent) throws Exception {
        ComputerPlayer p = new ComputerPlayer(0, "first_loop", script());
        AiBudget budget = new AiBudget(cap);
        List<Command> sent = new ArrayList<>();
        p.think(new AiView(h.core().queries(), 0, budget), budget, sent::add);
        spent[0] = budget.spent();
        return sent;
    }

    @Test
    void threeHundredWoodAndNineOfTenBuildsAHouse() throws Exception {
        SimHarness h = match(10, 9);
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 300));
        h.step();
        List<Command> sent = think(h, AiBudget.CAP, new int[1]);
        assertThat(sent)
                .filteredOn(PlaceBuilding.class::isInstance)
                .extracting(c -> ((PlaceBuilding) c).template().value())
                .containsExactly("strategos:building/house");
    }

    @Test
    void foodAndRoomTrainAWorkerAndIdleWorkersGather() throws Exception {
        SimHarness h = match(150, 5);
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 100));
        h.step();
        List<Command> sent = think(h, AiBudget.CAP, new int[1]);
        assertThat(sent).filteredOn(Train.class::isInstance).hasSize(1);
        assertThat(sent).as("a flat match has no node: nothing to gather").noneMatch(Gather.class::isInstance);
    }

    /**
     * M4 T21: a match below the world's height 0, an arena's: the next block of a node has a negative height, and an
     * idle worker is still sent to the oak (the player had taken a negative height for "no block left").
     */
    @Test
    void belowHeightZeroIdleWorkersStillGather() throws Exception {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        TreeFixtures.draw(world, TreeFixtures.Species.OAK, 30, 1, 30);
        SimHarness h = SimHarness.builder().size(64, 64).yBand(-64, -33, -63).build();
        h.publishAll(world);
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 8, 8, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(24, 24), 2, 1));
        h.step();
        h.step();
        assertThat(h.core().queries().nodes()).allMatch(n -> n.topY() < 0, "the oak's blocks below 0");
        List<Command> sent = think(h, AiBudget.CAP, new int[1]);
        assertThat(sent).filteredOn(Gather.class::isInstance).isNotEmpty();
    }

    /** M3 T31: a placement's REPLACE took the nearest workers off the site they were building, which then stood. */
    @Test
    void theNextPlacementLeavesBuildersOnTheirSite() throws Exception {
        SimHarness h = match(10, 9);
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 600));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "stone", 100)); // the barracks' (M4 T5, T6)
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "iron", 100));
        h.step();
        PlaceBuilding first = placement(think(h, AiBudget.CAP, new int[1]));
        h.submit(new PlayerSlot(0), first);
        h.step();
        h.step();
        PlaceBuilding second = placement(think(h, AiBudget.CAP, new int[1])); // a fresh player: the house again
        assertThat(boxed(second.builders().toArray()))
                .as("the first site's builders, %s", first.builders())
                .doesNotContainAnyElementsOf(boxed(first.builders().toArray()));
    }

    private static PlaceBuilding placement(List<Command> sent) {
        return sent.stream()
                .filter(PlaceBuilding.class::isInstance)
                .map(PlaceBuilding.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no placement in " + sent));
    }

    private static List<Integer> boxed(int[] handles) {
        return java.util.Arrays.stream(handles).boxed().toList();
    }

    @Test
    void aThinkNeverExceedsItsCap() throws Exception {
        SimHarness h = match(150, 40);
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 1_000));
        h.step();
        for (int cap : new int[] {AiBudget.CAP, 60, 30}) {
            int[] spent = new int[1];
            think(h, cap, spent);
            assertThat(spent[0]).as("cap %d", cap).isLessThanOrEqualTo(cap);
        }
    }

    @Test
    void itsCommandsPassTheValidatorsAHumansPass() throws Exception {
        SimHarness ai = match(10, 9);
        SimHarness human = match(10, 9);
        for (SimHarness h : new SimHarness[] {ai, human}) {
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 100));
            h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 300));
        }
        ai.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "first_loop", script()));
        while (ai.tick() < 19) {
            ai.step();
            human.step();
        }
        List<Command> thought = think(human, AiBudget.CAP, new int[1]); // what the player thinks at its turn, tick 20
        assertThat(thought).isNotEmpty();
        for (Command c : thought) {
            human.submit(new PlayerSlot(0), c); // as a human sends them, at tick 19
        }
        ai.runTicks(3); // the player thinks in tick 20; its commands apply in tick 21
        human.runTicks(3);
        MatchState s = KernelAccess.state(ai.core());
        assertThat(s.aiSlots().get(0)).isNotNull();
        List<String> fromAi = judged(ai);
        assertThat(fromAi).as("its commands were judged").hasSize(thought.size());
        assertThat(judged(human)).as("a human's same commands are judged alike").isEqualTo(fromAi);
    }

    /** Faction 0's judged commands: kind, outcome and first reason, in order. */
    private static List<String> judged(SimHarness h) {
        return h.results().stream()
                .filter(r -> r.issuer().value() == 0)
                .map(r -> r.kind() + " " + r.outcome() + " " + r.firstReason())
                .toList();
    }
}
