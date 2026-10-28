package dev.strategos.core.sim.replicate;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.UnitView;

/**
 * Direct, directional union of tick-frozen solo queries; owns no grid or hysteresis. This cold contract is not
 * installed in the runtime. AFTER-M6: adapt the authoritative solo VisibilityIndex and visible/explored grids.
 * AFTER-M7: freeze diplomacy per tick, persist received exploration and materialize O(1) views for the tracking gate.
 */
@CoreApi
public final class TeamVision implements Visibility {
    @FunctionalInterface
    public interface Sharing {
        boolean shares(FactionSlot source, FactionSlot viewer);
    }

    @FunctionalInterface
    public interface Cells {
        boolean contains(FactionSlot source, int cellX, int cellZ);
    }

    private final int factions;
    private final Sharing sharing;
    private final Visibility solo;
    private final Cells visible;
    private final Cells explored;

    public TeamVision(int factions, Sharing sharing, Visibility solo, Cells visible, Cells explored) {
        if (factions < 1 || factions > FactionSlot.MAX + 1) {
            throw new IllegalArgumentException("participant count outside 1..16");
        }
        this.factions = factions;
        this.sharing = sharing;
        this.solo = solo;
        this.visible = visible;
        this.explored = explored;
    }

    @Override
    public boolean sees(FactionSlot viewer, UnitView unit) {
        if (!participant(viewer)) {
            return false;
        }
        if (participant(unit.faction()) && contributes(unit.faction(), viewer)) {
            return true;
        }
        for (int slot = 0; slot < factions; slot++) {
            FactionSlot source = new FactionSlot(slot);
            if (contributes(source, viewer) && solo.sees(source, unit)) {
                return true;
            }
        }
        return false;
    }

    public boolean visible(FactionSlot viewer, int cellX, int cellZ) {
        return union(visible, viewer, cellX, cellZ);
    }

    /** Current sources' explored union; already received exploration must be persisted separately by the adapter. */
    public boolean explored(FactionSlot viewer, int cellX, int cellZ) {
        return union(explored, viewer, cellX, cellZ);
    }

    private boolean union(Cells cells, FactionSlot viewer, int x, int z) {
        if (!participant(viewer)) {
            return false;
        }
        for (int slot = 0; slot < factions; slot++) {
            FactionSlot source = new FactionSlot(slot);
            if (contributes(source, viewer) && cells.contains(source, x, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean contributes(FactionSlot source, FactionSlot viewer) {
        return source.equals(viewer) || sharing.shares(source, viewer);
    }

    private boolean participant(FactionSlot faction) {
        return faction.value() < factions;
    }
}
