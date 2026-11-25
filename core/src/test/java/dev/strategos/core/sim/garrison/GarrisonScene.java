package dev.strategos.core.sim.garrison;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;

/** BLD-10's tests' match: a flat 96 × 64 with three factions, buildings and units spawned by the admin. */
final class GarrisonScene {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final PlayerSlot P0 = new PlayerSlot(0);

    final SimHarness h =
            SimHarness.builder().size(96, 64).yBand(0, 31, 1).factions(3).build();
    final MatchState s;

    GarrisonScene() {
        h.publishAll(VoxelFixtures.flat(96, 64, 32, 1));
        h.step();
        s = KernelAccess.state(h.core());
    }

    int building(int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(faction), x, z, 0));
        h.step();
        int[] handles = s.buildings().handles();
        return handles[handles.length - 1];
    }

    int[] units(TemplateId template, int faction, double x, double z, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, new FactionSlot(faction), new Vec2d(x, z), count, 1.5));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    /** {@code count} units of {@code template} near building {@code centre}'s door, walked into it. */
    int[] garrisoned(TemplateId template, int faction, int centre, int count) {
        int[] door = Garrisons.door(s, centre);
        int[] made = units(template, faction, door[0] + 0.5, door[1] + 6.5, count);
        h.submit(P0, new Garrison(UnitSet.of(made), centre));
        h.step();
        if (!h.runUntil(() -> s.garrison().count(centre) == count, 400)) {
            throw new AssertionError("the units did not go in");
        }
        return made;
    }

    boolean inside(int unit) {
        return s.garrison().inside(Handle.index(unit));
    }

    static TemplateId infantry() {
        return TemplateSet.INFANTRY;
    }
}
