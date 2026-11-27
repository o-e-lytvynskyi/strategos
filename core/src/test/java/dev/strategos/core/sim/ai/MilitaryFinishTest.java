package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * QA-12, the finishing rules of the military manager (M6 §2.21): while a wave falls back no single spearman is sent
 * out after it, and a leaving wave takes the idle rams with it.
 */
class MilitaryFinishTest {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId RAM = new TemplateId("strategos:unit/ram");

    private final SimHarness h = SimHarness.builder()
            .size(160, 160)
            .yBand(0, 8, 1)
            .factions(2)
            .rules(MatchRules.DEFAULTS.withPlay(HeadlessMatch.EXPLORED)) // the AI sees all
            .build();

    private StrategyScript script(boolean spears) {
        String json = Levels.resolve(HeadlessMatch.read("ai/skirmish.json"), "normal", "balanced");
        StrategyScript s =
                StrategyScript.parse(spears ? json : json.replaceAll("\"spears\":\\s*\\d+", "\"spears\": 0"));
        assertThat(s.battle().finish()).isTrue();
        assertThat(s.battle().spears() > 0).isEqualTo(spears);
        assertThat(s.battle().rams()).isPositive();
        return s;
    }

    private int[] spawn(TemplateId t, int faction, double x, double z, int n) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(t, new FactionSlot(faction), new Vec2d(x, z), n, 3));
        List<Integer> out = new ArrayList<>();
        h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .forEach(e -> out.add(((UnitCreated) e).handle()));
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private void world() {
        h.publishAll(VoxelFixtures.flat(160, 160, 8, 1));
        h.step();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 20, 20, 0));
        h.step();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 120, 120, 0));
        h.step();
    }

    private List<Command> think(AiMemory memory, boolean spears) {
        List<Command> sent = new ArrayList<>();
        AiBudget budget = new AiBudget(AiBudget.CAP);
        new MilitaryManager(script(spears), memory).think(new AiView(h.core().queries(), 0, budget), budget, sent::add);
        return sent;
    }

    private static boolean orders(Command c, int[] handles) {
        UnitSet set = switch (c) {
            case Attack a -> a.units();
            case AttackMove a -> a.units();
            case Move m -> m.units();
            case dev.strategos.core.protocol.FormationMove f -> f.units(); // AI-09: a long march leaves as a formation
            default -> null;
        };
        if (set == null) {
            return false;
        }
        for (int u : handles) {
            if (set.contains(u)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void whileAWaveFallsBackNoSingleSpearmanIsSentOut() {
        world();
        int[] spears = spawn(TemplateSet.INFANTRY, 0, 25.5, 25.5, 6);
        // the enemy's centre is the only thing in sight: the way looks clear
        h.step();
        AiMemory memory = new AiMemory();
        memory.set(Slots.FIGHT, 2); // the wave is falling back
        memory.set(Slots.WAVE, 1);

        List<Command> sent = think(memory, true);

        assertThat(sent)
                .as("no attack or attack-move names a spearman while the wave falls back")
                .noneMatch(c -> (c instanceof Attack || c instanceof AttackMove) && orders(c, spears));
    }

    @Test
    void aLeavingWaveSendsItsIdleRams() {
        world();
        int[] soldiers = spawn(TemplateSet.INFANTRY, 0, 25.5, 25.5, 12);
        int[] rams = spawn(RAM, 0, 30.5, 25.5, 2);
        assertThat(rams).hasSize(2);
        AiMemory memory = new AiMemory();
        memory.set(Slots.MUSTER, 1); // mustered at tick 0, its warning has run
        for (int i = 0; i < 20 * script(false).attack().announce() + 5; i++) {
            h.step();
        }

        List<Command> sent = think(memory, false);

        assertThat(sent).as("the wave leaves").anyMatch(c -> orders(c, soldiers));
        assertThat(sent).as("and takes the rams").anyMatch(c -> c instanceof Attack && orders(c, rams));
    }
}
