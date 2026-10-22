package dev.strategos.core.sim.spatial;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MotionStore;
import java.util.List;
import java.util.Random;

/** Units in bare stores for the spatial tests: four templates over the classes Cavalry, Infantry, Melee and Ranged. */
final class SpatialWorld {
    static final TemplateSet TEMPLATES = new TemplateSet(List.of(
            template(0, "a", List.of("Infantry", "Melee")),
            template(1, "b", List.of("Infantry", "Ranged")),
            template(2, "c", List.of("Cavalry", "Melee")),
            template(3, "d", List.of())));

    final EntityStore entities = new EntityStore();
    final MotionStore motion = new MotionStore();
    final IdentityStore identity = new IdentityStore();

    /** A new unit at (x, z); returns its handle. */
    int add(double x, double z, int template, int faction) {
        int handle = entities.create();
        int i = Handle.index(handle);
        motion.ensureCapacity(entities.highWater());
        identity.ensureCapacity(entities.highWater());
        motion.set(i, x, 0, z, 0);
        identity.set(i, template, faction);
        return handle;
    }

    /** {@code n} units on a {@code sizeX} × {@code sizeZ} space, a few up to 10 blocks outside it; some destroyed. */
    static SpatialWorld random(Random r, int sizeX, int sizeZ, int n) {
        SpatialWorld w = new SpatialWorld();
        int[] handles = new int[n];
        for (int k = 0; k < n; k++) {
            handles[k] = w.add(coordinate(r, sizeX), coordinate(r, sizeZ), r.nextInt(4), r.nextInt(4));
        }
        for (int k = 0; k < n; k++) {
            if (r.nextInt(8) == 0) {
                w.entities.destroy(handles[k]);
            }
        }
        for (int k = 0; k < n / 10; k++) { // reused slots, a generation on
            w.add(coordinate(r, sizeX), coordinate(r, sizeZ), r.nextInt(4), r.nextInt(4));
        }
        return w;
    }

    static double coordinate(Random r, int size) {
        return r.nextInt(20) == 0 ? -10 + r.nextDouble() * (size + 20) : r.nextDouble() * size;
    }

    UnitGrid grid(int sizeX, int sizeZ) {
        UnitGrid grid = new UnitGrid(sizeX, sizeZ, TEMPLATES);
        grid.rebuild(entities, motion, identity);
        return grid;
    }

    long classes(int index) {
        return TEMPLATES
                .tokens()
                .bits(TEMPLATES.byIndex(identity.templateIndex(index)).classes());
    }

    private static UnitTemplate template(int index, String name, List<String> classes) {
        return new UnitTemplate(
                new TemplateId("strategos:unit/" + name),
                index,
                name,
                classes,
                0.25,
                45,
                "infantry",
                "strategos:unit_humanoid",
                "humanoid",
                "",
                0.6f,
                1.8f,
                0.35);
    }
}
