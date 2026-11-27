package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** The phases in a real level: what reaching City does to the civic centre's territory and the arrows (TECH-04). */
public final class PhaseTests {
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    private static final TemplateId ARSENAL = new TemplateId("strategos:building/arsenal");
    private static final TemplateId TEMPLE = new TemplateId("strategos:building/temple");
    private static final TechId TOWN = new TechId("strategos:phase_town");
    private static final TechId CITY = new TechId("strategos:phase_city");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final int ROW = 4; // the match-local row of tiles level with the centre

    private PhaseTests() {}

    /**
     * TECH-04.3: a civic centre with five houses researches Town, three Town buildings go up, City is researched, and
     * the centre's territory grows: in this test's content the centre's radius is 10 and Town and City take a second,
     * so on the row z = {@value #ROW} some tiles pass from neutral to the faction's and none the other way, and the faction
     * is in phase 2. The arrows' +2 is checked in L1 ({@code CityTerritoryTest}): the queries give no building's stats.
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 600)
    public static void city_bonuses(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, F0, 4, 4, 0));
        for (int k = 0; k < 5; k++) {
            rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(HOUSE, F0, 4 + 8 * k, 22, 0));
        }
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(F0, resource, 5_000));
        }
        rt.queueSwap(new ContentSwap(withPhasesIn(1)));
        var state = new Object() {
            int centre = -1;
            int[] before;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(core(rt).queries().buildings().size() == 6, "the buildings are not up yet");
                    state.centre = core(rt).queries().buildings().stream()
                            .filter(v -> v.template().equals(CENTRE))
                            .findFirst()
                            .orElseThrow()
                            .handle();
                    rt.submit(P0, new Research(state.centre, TOWN));
                })
                .thenWaitUntil(() -> check(core(rt).queries().phase(F0) == 1, "Town is not researched yet"))
                .thenExecute(() -> {
                    rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(TOWER, F0, 4, 34, 0));
                    rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(ARSENAL, F0, 20, 34, 0));
                    rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(TEMPLE, F0, 34, 34, 0));
                })
                .thenWaitUntil(() -> {
                    check(core(rt).queries().buildings().size() == 9, "the Town buildings are not up yet");
                    state.before = row(rt);
                    check(owned(state.before) > 0, "the centre owns no tile of the row yet");
                    rt.submit(P0, new Research(state.centre, CITY));
                })
                .thenWaitUntil(() -> check(core(rt).queries().phase(F0) == 2, "City is not researched yet"))
                .thenWaitUntil(() -> {
                    int[] after = row(rt);
                    for (int x = 0; x < after.length; x++) {
                        check(!(state.before[x] == 0 && after[x] != 0), "tile " + x + " of the row was lost at City");
                    }
                    check(owned(after) > owned(state.before), "the row still has " + owned(after) + " owned tiles");
                })
                .thenSucceed();
    }

    /** The owner of every tile of the row. */
    private static int[] row(MatchRuntime rt) {
        int[] owners = new int[48];
        for (int x = 0; x < owners.length; x++) {
            owners[x] = core(rt).queries().territoryOwner(x, ROW);
        }
        return owners;
    }

    private static int owned(int[] owners) {
        int n = 0;
        for (int owner : owners) {
            n += owner == 0 ? 1 : 0;
        }
        return n;
    }

    /** The built-in set with Town and City researched in {@code seconds} each. */
    private static TemplateSet withPhasesIn(double seconds) {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            JsonObject json = b.flattened(t.id()).deepCopy();
            if (t.id().equals(CENTRE)) {
                json.getAsJsonObject("TerritoryInfluence").addProperty("radius", 10); // so that a third is visible
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        List<TemplateCompiler.TechFlat> techs = new ArrayList<>();
        for (TechDef t : b.techs()) {
            JsonObject json = t.toJson();
            if (t.id().equals(TOWN) || t.id().equals(CITY)) {
                json.addProperty("time", seconds);
            }
            techs.add(new TemplateCompiler.TechFlat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(flats, techs).set();
    }
}
