package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchEnded;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The M5 demo's scenario {@code strategos:battle_at_the_ford} (M5 spec §1.3) on the proving ground: it holds 60
 * soldiers a side in the shares of BattleScene (24 spearmen, 20 archers, 16 horsemen), and with both slots played by the
 * battle bot against a general a seeded match ends with a winner.
 */
class BattleAtTheFordTest {
    static final String SPEAR = "strategos:unit/infantry";
    static final String ARCHER = "strategos:unit/archer";
    static final String HORSEMAN = "strategos:unit/horseman";

    /** The game-time cap of a seeded match: five minutes at 20 ticks a second. */
    static final long CAP = 20_000;

    static final long ADVANCE = 100;
    static final long ORDERS = 100;
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final Vec2d FRONT = new Vec2d(344.5, 192.5);

    static Scenario scenario() {
        return Scenario.CODEC
                .parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(HeadlessMatch.read("scenarios/battle_at_the_ford.json")))
                .getOrThrow();
    }

    /** The units of {@code slot} by template id. */
    static Map<String, Integer> units(Scenario.Slot slot) {
        Map<String, Integer> out = new TreeMap<>();
        for (Scenario.Entity e : slot.entities()) {
            if (e.template().value().startsWith("strategos:unit/")) {
                out.merge(e.template().value(), e.count(), Integer::sum);
            }
        }
        return out;
    }

    @Test
    void holdsSixtyASideInEqualCostShares() {
        Scenario s = scenario();
        assertThat(s.map()).isEqualTo("strategos:proving_ground");
        assertThat(s.slots()).hasSize(2);
        assertThat(s.slots().get(0).player()).isEqualTo("human");
        assertThat(s.slots().get(1).player()).isEqualTo("ai:battle");
        for (Scenario.Slot slot : s.slots()) {
            assertThat(units(slot))
                    .containsEntry(SPEAR, 24)
                    .containsEntry(ARCHER, 20)
                    .containsEntry(HORSEMAN, 16);
            long buildings = slot.entities().stream()
                    .filter(e -> e.template().value().startsWith("strategos:building/"))
                    .count();
            assertThat(buildings).as("a centre, a barracks and two deposits").isEqualTo(4);
            assertThat(slot.entities())
                    .anyMatch(e -> e.template().value().equals("strategos:building/civic_centre"))
                    .anyMatch(e -> e.template().value().equals("strategos:building/barracks"))
                    .anyMatch(e -> e.template().equals(TemplateSet.WORKER));
        }
    }

    @Test
    void setsUpOnTheProvingGroundWithTheBotSeated() {
        Scenario s = scenario();
        List<Command> commands = ScenarioSetup.commands(
                s,
                ProvingGround.generate().startSlots(),
                TemplateSet.builtin(),
                p -> HeadlessMatch.read("ai/" + p + ".json"));
        assertThat(commands.stream().filter(c -> c instanceof dev.strategos.core.protocol.SetAi))
                .singleElement()
                .isEqualTo(new dev.strategos.core.protocol.SetAi(
                        new FactionSlot(1), "battle", HeadlessMatch.read("ai/battle.json")));
    }

    /**
     * The match of {@code seed}: slot 1 played by the shipped battle bot, slot 0 (the human's) by this test's general,
     * who attack-moves the whole army at the enemy centre at tick {@value #ADVANCE} and, every {@value #ORDERS} ticks
     * once the enemy has no soldier left, sends the spearmen at the centre itself (fighters never attack a building by
     * themselves). Two bots alone stalemate: the survivors are archers, which cannot burn a centre, and the bots' wood
     * runs short for more spearmen. The {@code MatchEnded} the match ends with, or null at the cap.
     */
    static MatchEnded play(long seed) {
        ProvingGround ground = ProvingGround.generate();
        SimHarness h = SimHarness.builder()
                .size(ProvingGround.SIZE, ProvingGround.SIZE)
                .yBand(0, ProvingGround.HEIGHT, 16)
                .seed(seed)
                .build();
        h.publishAll(ground.voxels());
        h.step();
        String script = HeadlessMatch.read("ai/battle.json");
        for (Command c : ScenarioSetup.commands(scenario(), ground.startSlots(), TemplateSet.builtin(), p -> script)) {
            h.submit(PlayerSlot.ADMIN, c);
        }
        MatchQueries q = h.core().queries();
        FactionSlot me = new FactionSlot(0);
        PlayerSlot general = new PlayerSlot(0);
        while (h.tick() < CAP) {
            for (OutputEvent e : h.step().events()) {
                if (e instanceof MatchEnded end) {
                    return end;
                }
            }
            if (h.tick() == 2) {
                for (int f = 0; f < 2; f++) {
                    assertThat(q.unitHandlesOf(new FactionSlot(f)))
                            .as("faction %d", f)
                            .hasSize(65);
                }
            }
            if (h.tick() == ADVANCE) {
                h.submit(general, AttackMove.replace(UnitSet.of(fighters(q, me, null)), FRONT));
            } else if (h.tick() > ADVANCE
                    && h.tick() % ORDERS == 0
                    && fighters(q, new FactionSlot(1), null).length == 0) {
                int[] spears = fighters(q, me, TemplateSet.INFANTRY);
                int centre = -1;
                for (BuildingView b : q.buildings()) {
                    if (b.faction() == 1 && b.template().equals(CENTRE)) {
                        centre = b.handle();
                    }
                }
                if (spears.length > 0 && centre >= 0) {
                    h.submit(general, Attack.replace(UnitSet.of(spears), centre));
                }
            }
        }
        return null;
    }

    /** The soldiers (not the workers) of {@code f}, only those of {@code template} if given. */
    private static int[] fighters(MatchQueries q, FactionSlot f, @Nullable TemplateId template) {
        return java.util.Arrays.stream(q.unitHandlesOf(f))
                .filter(handle -> {
                    UnitView u = Objects.requireNonNull(q.unit(handle));
                    return template == null
                            ? !u.template().equals(TemplateSet.WORKER)
                            : u.template().equals(template);
                })
                .toArray();
    }

    @Test
    @Tag("slow")
    void botAgainstTheGeneralEndsWithAWinnerSeed1() {
        MatchEnded end = play(1);
        assertThat(end).as("a winner by tick %d", CAP).isNotNull();
        assertThat(play(1)).as("the same seed ends the same").isEqualTo(end);
    }

    @Test
    @Tag("slow")
    void botAgainstTheGeneralEndsWithAWinnerSeed2() {
        MatchEnded end = play(2);
        assertThat(end).as("a winner by tick %d", CAP).isNotNull();
    }
}
