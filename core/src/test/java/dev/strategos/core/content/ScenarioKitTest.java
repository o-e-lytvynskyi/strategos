package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.command.ScenarioSetup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-01's start kits (M10 §2.2): a slot that plays the civilisation it was written for emits its entities as written;
 * one that plays another gets that civilisation's kit in place of its flagged entries, turned to the slot's yaw.
 */
class ScenarioKitTest {
    private static final CivId PROBE = new CivId("t:probe");
    private static final CivId OTHER = new CivId("t:other");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId WORKER = new TemplateId("strategos:unit/worker");

    /** A civilisation with its own kit: resources, a house by role, workers by template. */
    private static final String PROBE_JSON = """
            { "roles": { "house": "strategos:building/house", "worker": "strategos:unit/worker" },
              "start": { "resources": { "wood": 77 },
                         "entities": [ { "role": "house", "dz": -12, "rotation": 1 },
                                       { "template": "strategos:unit/worker", "dx": 5, "dz": 7,
                                         "count": 3, "spread": 1 } ] } }
            """;

    private static final TemplateSet SET = set(PROBE_JSON);

    private static TemplateSet set(String probe) {
        TemplateCompiler.Result r = CivTestSupport.compile(
                CivTestSupport.civ("strategos:default", BuiltinTemplates.DEFAULT_CIV_JSON),
                CivTestSupport.civ("t:probe", probe),
                CivTestSupport.civ("t:other", CivTestSupport.MINIMAL));
        assertThat(r.problems()).isEmpty();
        return r.set();
    }

    private static Scenario parse(String json) {
        return ScenarioTest.parse(json);
    }

    private static List<Command> commands(Scenario s, TemplateSet set, List<StartSlot> starts, CivId... civs) {
        return ScenarioSetup.commands(s, starts, set, p -> null, List.of(civs), r -> r);
    }

    private static int span(TemplateSet set, TemplateId id, int rotation) {
        return Footprint.of(set.blueprint(set.byId(id).netIndex()), 0, 0, rotation)
                .spanX();
    }

    private static Scenario firstSkirmish() throws IOException {
        Path file = Path.of(System.getProperty("strategos.repoRoot"))
                .resolve("mod/src/main/resources/data/strategos/strategos/scenarios/first_skirmish.json");
        return parse(Files.readString(file));
    }

    private static final List<StartSlot> SKIRMISH_STARTS =
            List.of(new StartSlot(44, 16, 140, 270f), new StartSlot(339, 16, 243, 90f));

    private static Scenario unflagged(Scenario s) {
        List<Scenario.Slot> slots = new ArrayList<>();
        for (Scenario.Slot slot : s.slots()) {
            List<Scenario.Entity> es = new ArrayList<>();
            for (Scenario.Entity e : slot.entities()) {
                es.add(new Scenario.Entity(e.template(), e.dx(), e.dz(), e.count(), e.spread(), e.rotation(), false));
            }
            slots.add(new Scenario.Slot(slot.player(), slot.resources(), es, slot.civ(), slot.kit()));
        }
        return new Scenario(s.map(), s.victory(), slots);
    }

    @Test
    void firstSkirmishOnTheDefaultCivilisationEmitsItsListAsWritten() throws IOException {
        Scenario s = firstSkirmish();
        assertThat(s.slots()).allSatisfy(slot -> {
            assertThat(slot.civ()).isEqualTo(CivId.DEFAULT);
            assertThat(slot.entities().stream()
                            .filter(Scenario.Entity::civ)
                            .map(e -> e.template().value().substring("strategos:".length())))
                    .containsExactly(
                            "building/civic_centre",
                            "unit/worker",
                            "unit/horseman",
                            "unit/sheep",
                            "unit/pig",
                            "unit/chicken");
        });
        List<Command> written = ScenarioSetup.commands(unflagged(s), SKIRMISH_STARTS, TemplateSet.builtin());
        assertThat(ScenarioSetup.commands(s, SKIRMISH_STARTS, TemplateSet.builtin()))
                .as("the flags change nothing for the civilisation the slot was written for")
                .isEqualTo(written);
        assertThat(commands(s, TemplateSet.builtin(), SKIRMISH_STARTS, CivId.DEFAULT, CivId.DEFAULT))
                .isEqualTo(written);
        assertThat(written)
                .contains(
                        new Grant(new FactionSlot(0), "food", 300),
                        new SpawnUnits(WORKER, new FactionSlot(0), new Vec2d(44.5, 149.5), 5, 2.0),
                        new SpawnUnits(WORKER, new FactionSlot(1), new Vec2d(339.5, 234.5), 5, 2.0));
    }

    @Test
    void aSlotPlayingAnotherCivilisationGetsItsKitInPlaceOfTheFlaggedEntries() {
        Scenario s = parse("""
                { "map": "m", "slots": [ { "resources": { "food": 300 }, "entities": [
                    { "template": "strategos:building/civic_centre", "civ": true },
                    { "template": "strategos:building/quarry", "dx": -14, "dz": -22 },
                    { "template": "strategos:unit/worker", "dz": 9, "count": 5, "civ": true },
                    { "template": "strategos:unit/horseman", "dx": 4, "civ": true } ] } ] }
                """);
        FactionSlot f = new FactionSlot(0);
        int quarry = span(SET, new TemplateId("strategos:building/quarry"), 0);
        // yaw 270 is the kit's own frame: the offsets as written, the rotation as written
        assertThat(commands(s, SET, List.of(new StartSlot(100, 0, 100, 270f)), PROBE))
                .containsExactly(
                        new dev.strategos.core.protocol.RuleChange("victory", "civic_centre"),
                        new Grant(f, "wood", 77),
                        new SpawnBuilding(HOUSE, f, 100 - span(SET, HOUSE, 1) / 2, 88 - span(SET, HOUSE, 1) / 2, 1),
                        new SpawnUnits(WORKER, f, new Vec2d(105.5, 107.5), 3, 1.0),
                        new dev.strategos.core.protocol.SpawnDeposit(
                                new TemplateId("strategos:building/quarry"), 86 - quarry / 2, 78 - quarry / 2));
    }

    @Test
    void theKitTurnsWithTheSlotsYaw() {
        Scenario s = parse("""
                { "map": "m", "slots": [ { "entities": [
                    { "template": "strategos:building/civic_centre", "civ": true } ] } ] }
                """);
        // the kit has the house at (0, -12) rotated 1 and workers at (5, 7); each 90 degrees of yaw is a quarter turn
        // from east to south, (dx, dz) -> (-dz, dx), and adds one to a building's rotation
        Object[][] expected = { // yaw, the workers' centre, the house's rotation, its dx and dz
            {270f, new Vec2d(105.5, 107.5), 1, 0, -12}, // the kit's own frame
            {0f, new Vec2d(93.5, 105.5), 2, 12, 0},
            {90f, new Vec2d(95.5, 93.5), 3, 0, 12}, // facing the other way: (dx, dz) -> (-dx, -dz)
            {180f, new Vec2d(107.5, 95.5), 0, -12, 0},
        };
        for (Object[] row : expected) {
            List<Command> out = commands(s, SET, List.of(new StartSlot(100, 0, 100, (float) row[0])), PROBE);
            int rotation = (int) row[2];
            int half = span(SET, HOUSE, rotation) / 2;
            assertThat(out)
                    .as("yaw %s", row[0])
                    .contains(
                            new SpawnUnits(WORKER, new FactionSlot(0), (Vec2d) row[1], 3, 1.0),
                            new SpawnBuilding(
                                    HOUSE,
                                    new FactionSlot(0),
                                    100 + (int) row[3] - half,
                                    100 + (int) row[4] - half,
                                    rotation));
        }
    }

    @Test
    void aSlotWithKitAndNoFlaggedEntryEmitsTheKitFirstAndTheKitsResources() {
        Scenario s = parse("""
                { "map": "m", "slots": [ { "kit": true, "resources": { "food": 300 }, "entities": [
                    { "template": "strategos:building/quarry", "dx": -14, "dz": -22 } ] } ] }
                """);
        List<Command> out = commands(s, SET, List.of(new StartSlot(100, 0, 100, 270f)), PROBE);
        assertThat(out.subList(1, out.size()))
                .extracting(c -> c.getClass().getSimpleName())
                .containsExactly("Grant", "SpawnBuilding", "SpawnUnits", "SpawnDeposit");
        assertThat(out.get(1)).isEqualTo(new Grant(new FactionSlot(0), "wood", 77));
        // a kit with no resources of its own leaves the slot's
        TemplateSet bare = set("""
                { "roles": { "house": "strategos:building/house" },
                  "start": { "entities": [ { "role": "house" } ] } }
                """);
        List<Command> plain = commands(s, bare, List.of(new StartSlot(100, 0, 100, 270f)), PROBE);
        assertThat(plain).contains(new Grant(new FactionSlot(0), "food", 300));
        assertThat(plain.get(2)).isInstanceOf(SpawnBuilding.class);
        // the default civilisation's kit is the same two ways
        assertThat(commands(s, SET, List.of(new StartSlot(100, 0, 100, 270f)), CivId.DEFAULT))
                .contains(new Grant(new FactionSlot(0), "iron", 100))
                .doesNotContain(new Grant(new FactionSlot(0), "wood", 77));
    }

    @Test
    void aKitEntityByRoleResolvesThroughTheCivilisationsRoles() {
        Scenario s = parse("""
                { "map": "m", "slots": [ { "entities": [
                    { "template": "strategos:building/civic_centre", "civ": true } ] } ] }
                """);
        TemplateSet withOther = set("""
                { "roles": { "house": "strategos:building/house", "worker": "strategos:unit/infantry" },
                  "start": { "entities": [ { "role": "worker", "count": 2 } ] } }
                """);
        assertThat(commands(s, withOther, List.of(new StartSlot(10, 0, 10, 270f)), PROBE))
                .contains(new SpawnUnits(
                        new TemplateId("strategos:unit/infantry"), new FactionSlot(0), new Vec2d(10.5, 10.5), 2, 2.0));
    }

    @Test
    void aCivilisationTheSetLacksIsRefused() {
        Scenario s = parse("""
                { "map": "m", "slots": [ { "kit": true, "entities": [] } ] }
                """);
        assertThatThrownBy(() ->
                        commands(s, TemplateSet.builtin(), List.of(new StartSlot(1, 0, 1, 270f)), new CivId("t:gone")))
                .hasMessageContaining("t:gone");
    }

    /**
     * The mirror check (M10 T4): the kit written for slot 0 (yaw 270) and turned by 180 degrees for slot 1 (yaw 90)
     * gives slot 1's hand-mirrored offsets, {@code (dx, dz) -> (-dx, -dz)}, in every unit and building position. It
     * does not give the centre's rotation: the kit turns it by two quarters, the hand-written slot 1 leaves it 0.
     */
    @Test
    void firstSkirmishsKitReproducesSlotZeroExactlyAndSlotOnesOffsetsByTheMirror() throws IOException {
        Scenario s = firstSkirmish();
        List<Scenario.Slot> slots = new ArrayList<>();
        for (Scenario.Slot slot : s.slots()) { // written for another civilisation, so the default's kit takes over
            slots.add(new Scenario.Slot(slot.player(), slot.resources(), slot.entities(), OTHER, false));
        }
        Scenario forOther = new Scenario(s.map(), s.victory(), slots);
        List<Command> written = ScenarioSetup.commands(s, SKIRMISH_STARTS, TemplateSet.builtin());
        List<Command> kit = commands(forOther, SET, SKIRMISH_STARTS, CivId.DEFAULT, CivId.DEFAULT);

        FactionSlot zero = new FactionSlot(0);
        assertThat(kit.stream().filter(c -> onSlot(c, zero)).toList())
                .as("slot 0: the same commands in the same order, the deposits aside")
                .isEqualTo(written.stream().filter(c -> onSlot(c, zero)).toList());

        FactionSlot one = new FactionSlot(1);
        List<Command> mine = kit.stream().filter(c -> onSlot(c, one)).toList();
        List<Command> theirs = written.stream().filter(c -> onSlot(c, one)).toList();
        assertThat(mine.stream().map(ScenarioKitTest::unturned).toList())
                .as("slot 1: the same positions as the hand-mirrored offsets")
                .isEqualTo(theirs.stream().map(ScenarioKitTest::unturned).toList());
        assertThat(mine.stream().filter(c -> c instanceof SpawnBuilding).map(c -> ((SpawnBuilding) c).rotation()))
                .as("the centre is turned by two quarters; the hand-written slot 1 keeps rotation 0")
                .containsExactly(2);
        assertThat(theirs.stream().filter(c -> c instanceof SpawnBuilding).map(c -> ((SpawnBuilding) c).rotation()))
                .containsExactly(0);
    }

    private static boolean onSlot(Command c, FactionSlot f) {
        return switch (c) {
            case SpawnBuilding b -> b.faction().equals(f);
            case SpawnUnits u -> u.faction().equals(f);
            case Grant g -> g.faction().equals(f);
            default -> false;
        };
    }

    /** The command with a building's rotation zeroed, so positions can be compared. */
    private static Command unturned(Command c) {
        return c instanceof SpawnBuilding b ? new SpawnBuilding(b.template(), b.faction(), b.x(), b.z(), 0) : c;
    }
}
