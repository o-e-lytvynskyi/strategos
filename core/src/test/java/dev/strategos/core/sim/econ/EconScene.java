package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.nav.BlockClass;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * An economy test's match (ECO-05): a flat world with trees and bushes drawn in, published whole so discovery finds
 * them, civic centres spawned as a scenario does, and workers given gather orders through the real command path.
 */
final class EconScene {
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final short BUSH =
            BlockClass.of(0, BlockClass.PASSABLE | BlockClass.HAZARD); // as a sweet berry bush classifies
    static final int FOOD = 0;
    static final int WOOD = 1;
    static final PlayerSlot P0 = new PlayerSlot(0);

    final VoxelWorld world;
    SimHarness h;
    MatchState s;

    private EconScene(VoxelWorld world) {
        this.world = world;
    }

    /** A flat world {@code sizeX × sizeZ}, 32 high, the floor at y 1. */
    static EconScene flat(int sizeX, int sizeZ) {
        return new EconScene(VoxelFixtures.flat(sizeX, sizeZ, 32, 1));
    }

    EconScene tree(TreeFixtures.Species species, int x, int z) {
        TreeFixtures.draw(world, species, x, 1, z);
        return this;
    }

    /** A berry bush at (x, 1, z) holding {@code food} units and regrowing {@code regrow} a second. */
    EconScene bush(int x, int z, double food, double regrow) {
        world.set(x, 1, z, BUSH);
        world.material(x, 1, z, new Material(MaterialKind.FORAGE, "food", "fruit", Amount.of(food), Amount.of(regrow)));
        return this;
    }

    /** Builds the match with the built-in templates, publishes every page and steps once: the nodes are found. */
    EconScene build() {
        return build(TemplateSet.builtin(), SimHarness.DEFAULT_SEED);
    }

    EconScene build(TemplateSet templates, long seed) {
        h = SimHarness.builder()
                .size(world.sizeX(), world.sizeZ())
                .yBand(0, world.height() - 1, 1)
                .templates(templates)
                .seed(seed)
                .build();
        h.publishAll(world);
        h.step();
        s = KernelAccess.state(h.core());
        assertThat(s.nodes().discovered()).isTrue();
        return this;
    }

    /** A finished civic centre of {@code faction}, its corner at (x, z); its handle. */
    int civicCentre(int faction, int x, int z) {
        return building(CIVIC_CENTRE, faction, x, z);
    }

    int building(TemplateId template, int faction, int x, int z) {
        int before = s.buildings().handles().length;
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        assertThat(handles).as("the building stands").hasSize(before + 1);
        return handles[handles.length - 1];
    }

    /** {@code count} units of {@code template} for {@code faction} round {@code at}; their handles. */
    int[] units(TemplateId template, int faction, Vec2d at, int count, double spread) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), at, count, spread));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    int[] workers(int faction, Vec2d at, int count, double spread) {
        return units(TemplateSet.WORKER, faction, at, count, spread);
    }

    /** Faction 0's player orders {@code units} to gather at the block (x, y, z). */
    void gather(int[] units, int x, int y, int z) {
        h.submit(
                new PlayerSlot(s.identity().faction(dev.strategos.core.shared.Handle.index(units[0]))),
                Gather.replace(UnitSet.of(units), x, y, z));
    }

    long gathered(int faction, int resource) {
        return s.treasury().gathered(faction, resource);
    }

    int index(int handle) {
        return dev.strategos.core.shared.Handle.index(handle);
    }

    /** The built-in set with {@code edit} applied to the flattened JSON of each template, at the same net indices. */
    static TemplateSet edited(BiConsumer<TemplateId, JsonObject> edit) {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            JsonObject json = TemplateSet.builtin().flattened(t.id());
            assertThat(json).isNotNull();
            edit.accept(t.id(), json);
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(flats);
        assertThat(result.problems()).isEmpty();
        return result.set();
    }

    /** The built-in set whose spearman also gathers wood, at {@code rate} a second, ten a trip (D-02 as data). */
    static TemplateSet gatheringSpearmen(double rate) {
        return edited((id, json) -> {
            if (id.equals(TemplateSet.INFANTRY)) {
                JsonObject identity = json.getAsJsonObject("Identity");
                identity.addProperty("classes", identity.get("classes").getAsString() + " Gatherer");
                json.add(
                        "ResourceGatherer",
                        JsonParser.parseString("{\"rates\": {\"wood\": {\"tree\": " + rate
                                        + "}}, \"capacities\": {\"wood\": 10}}")
                                .getAsJsonObject());
            }
        });
    }
}
