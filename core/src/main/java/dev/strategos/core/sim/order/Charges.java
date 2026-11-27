package dev.strategos.core.sim.order;

import dev.strategos.core.content.Footprint;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.build.BuildingStore;

/** What a guard order asks of its charge (ORD-07): a unit or a building, told apart by its handle. */
public final class Charges {
    private Charges() {}

    /** Whether handle {@code charge} names a unit or building that is alive. */
    public static boolean alive(MatchState s, int charge) {
        return BuildingStore.isBuilding(charge)
                ? s.buildings().alive(charge)
                : s.entities().isAlive(charge);
    }

    /** The faction that holds handle {@code charge}, a unit or a building. */
    public static int factionOf(MatchState s, int charge) {
        return BuildingStore.isBuilding(charge)
                ? s.buildings().faction(charge)
                : s.identity().faction(Handle.index(charge));
    }

    /** The charge's centre on the plane, {x, z}: a unit's place, a building's footprint's middle. */
    public static double[] centre(MatchState s, int charge) {
        if (BuildingStore.isBuilding(charge)) {
            Footprint at = s.footprintOf(charge);
            return new double[] {at.anchorX() + at.spanX() / 2.0, at.anchorZ() + at.spanZ() / 2.0};
        }
        MotionStore m = s.motion();
        int c = Handle.index(charge);
        return new double[] {m.x(c), m.z(c)};
    }
}
