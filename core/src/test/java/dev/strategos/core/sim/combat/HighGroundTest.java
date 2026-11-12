package dev.strategos.core.sim.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.ShotRequested;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-16: range and damage grow with the shooter's height over the target, by the template's knobs. */
class HighGroundTest {
    private static final TemplateId ARCHER = new TemplateId("strategos:unit/a_archer");
    private static final TemplateId DUMMY = new TemplateId("strategos:unit/z_dummy");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final double OFFSET = 0.6; // the two bodies' radii: a gap of g is a distance of g + 0.6

    @Test
    void advantageIsTheHeightOverTheTargetCappedAndNeverNegative() {
        Fixture f = new Fixture(null);
        double[][] rows = {{0, 0}, {3, 3}, {6, 6}, {10, 8}, {-4, 0}};
        for (double[] row : rows) {
            f.s.motion().set(f.archer, 10, row[0], 10, 0);
            f.s.motion().set(f.dummy, 20, 0, 10, 0);
            assertThat(HighGround.advantage(f.s, f.archer, f.dummy))
                    .as("dy %s", row[0])
                    .isEqualTo(row[1]);
        }
    }

    @Test
    void factorsFollowTheFormulaWithTheStartingValues() {
        Fixture f = new Fixture(null);
        assertThat(HighGround.rangeFactor(f.s, f.archer, 0)).isEqualTo(1);
        assertThat(HighGround.rangeFactor(f.s, f.archer, 6)).isCloseTo(1.3, within(1e-12));
        assertThat(HighGround.damageFactor(f.s, f.archer, 6)).isCloseTo(1.24, within(1e-12));
        assertThat(HighGround.rangeFactor(f.s, f.archer, 8)).isCloseTo(1.4, within(1e-12));
        assertThat(HighGround.damageFactor(f.s, f.archer, 8)).isCloseTo(1.32, within(1e-12));
    }

    @Test
    void aTemplateOverrideOfTheKnobsTakesEffect() {
        Fixture f = new Fixture("{\"rangePerBlock\":0.1,\"damagePerBlock\":0,\"maxBlocks\":2}");
        f.s.motion().set(f.archer, 10, 6, 10, 0);
        f.s.motion().set(f.dummy, 20, 0, 10, 0);
        double blocks = HighGround.advantage(f.s, f.archer, f.dummy);
        assertThat(blocks).isEqualTo(2);
        assertThat(HighGround.rangeFactor(f.s, f.archer, blocks)).isCloseTo(1.2, within(1e-12));
        assertThat(HighGround.damageFactor(f.s, f.archer, blocks)).isEqualTo(1);
    }

    @Test
    void anArcherSixBlocksUpEngagesAtBaseRangeTimesOnePointThreeAndNotBeyond() {
        // base range 20, so 26 at 6 blocks up
        Fixture inside = new Fixture(null);
        inside.place(6, 25 + OFFSET);
        inside.attack();
        double x0 = inside.s.motion().x(inside.archer);
        inside.h.runTicks(6);
        assertThat(inside.s.motion().x(inside.archer)).as("stands and shoots").isEqualTo(x0);
        assertThat(inside.shots).isNotEmpty();
        assertThat(inside.shots.get(0).range()).isCloseTo(26, within(1e-9));

        Fixture beyond = new Fixture(null);
        beyond.place(6, 27 + OFFSET);
        beyond.attack();
        double x1 = beyond.s.motion().x(beyond.archer);
        beyond.h.runTicks(6);
        assertThat(beyond.s.motion().x(beyond.archer)).as("closes in").isGreaterThan(x1);
        assertThat(beyond.shots).isEmpty();

        Fixture level = new Fixture(null);
        level.place(0, 25 + OFFSET);
        level.attack();
        double x2 = level.s.motion().x(level.archer);
        level.h.runTicks(6);
        assertThat(level.s.motion().x(level.archer)).as("no height, no reach").isGreaterThan(x2);
        assertThat(level.shots).isEmpty();
    }

    @Test
    void theShotsPierceIsSixTimesOnePointTwoFour() {
        Fixture f = new Fixture(null);
        f.place(6, 15);
        f.attack();
        f.h.runTicks(6);
        assertThat(f.shots).isNotEmpty();
        assertThat(f.shots.get(0).damage().pierce()).isCloseTo(6 * 1.24f, within(1e-5f));
        Fixture flat = new Fixture(null);
        flat.place(0, 15);
        flat.attack();
        flat.h.runTicks(6);
        assertThat(flat.shots.get(0).damage().pierce()).isEqualTo(6f);
    }

    private static final class Fixture {
        final SimHarness h;
        final MatchState s;
        final int archer;
        final int dummy;
        final int archerHandle;
        final int dummyHandle;
        final List<ShotRequested> shots = new ArrayList<>();

        Fixture(String highGround) {
            h = SimHarness.builder()
                    .size(96, 64)
                    .seed(Seeds.first("canary"))
                    .templates(templates(highGround))
                    .build();
            archerHandle = spawn(ARCHER, F0, 5.5);
            dummyHandle = spawn(DUMMY, F1, 40.5);
            s = KernelAccess.state(h.core());
            archer = Handle.index(archerHandle);
            dummy = Handle.index(dummyHandle);
            s.bus().subscribe(ShotRequested.class, shots::add);
        }

        private int spawn(TemplateId template, FactionSlot faction, double x) {
            h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, new Vec2d(x, 10.5), 1, 0));
            return h.step().events().stream()
                    .filter(UnitCreated.class::isInstance)
                    .map(UnitCreated.class::cast)
                    .mapToInt(UnitCreated::handle)
                    .findFirst()
                    .orElseThrow();
        }

        /** The archer {@code up} blocks above the dummy, their edges {@code distance - 0.6} apart. */
        void place(double up, double distance) {
            s.motion().set(archer, 10.5, up, 10.5, 0);
            s.motion().set(dummy, 10.5 + distance, 0, 10.5, 0);
        }

        void attack() {
            h.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(archerHandle), dummyHandle));
        }
    }

    private static TemplateSet templates(String highGround) {
        String body = """
                {"Identity":{},"UnitMotion":{"walkSpeed":0.25},
                 "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}}
                """;
        JsonObject archer = JsonParser.parseString(body).getAsJsonObject();
        JsonObject ranged = JsonParser.parseString("""
                {"pierce":6,"range":20,"prepare":1,"repeat":4,"speed":4,"spread":0}
                """).getAsJsonObject();
        if (highGround != null) ranged.add("highGround", JsonParser.parseString(highGround));
        JsonObject attack = new JsonObject();
        attack.add("Ranged", ranged);
        archer.add("Attack", attack);
        // Vision above every gap used, so engaging never ends for being too far
        archer.add("Vision", JsonParser.parseString("{\"range\":64}"));
        TemplateCompiler.Result result = TemplateCompiler.compile(List.of(
                new RawTemplate(ARCHER, "archer.json", archer),
                new RawTemplate(
                        DUMMY, "dummy.json", JsonParser.parseString(body).getAsJsonObject())));
        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        return result.set();
    }
}
