package dev.strategos.core.sim.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.econ.NodeStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The server's side of placement (BLD-02): the match as {@link PlacementRules.World} reads it (the surface grid's top
 * floors, water no land; every building's footprint, foundations included, and every standing node block taken), a
 * template's {@link PlacementRules.Params}, the height a building's floor layer takes, and its builder slots.
 */
@CoreApi
public final class Placing {
    /** The class token of a civic centre (BLD-02's spacing and reach). */
    public static final String CIVIC_CENTRE = "CivicCentre";

    public static final String SPACING = "Placement/spacing";
    public static final String CIVIC_REACH = "Placement/civicReach";

    private Placing() {}

    /** The match as the rules read it, taken now: footprints and nodes as they stand. */
    public static PlacementRules.World world(MatchState s) {
        MatchBounds bounds = s.config().bounds();
        BitSet taken = new BitSet(bounds.sizeX() * bounds.sizeZ());
        List<PlacementRules.Centre> centres = new ArrayList<>();
        BuildingStore buildings = s.buildings();
        for (int handle : buildings.handles()) {
            Footprint at = s.footprintOf(handle);
            Blueprint b = s.blueprintOf(handle);
            for (int cell : at.columns(b)) {
                int x = cell >>> 16;
                int z = cell & 0xFFFF;
                if (x < bounds.sizeX() && z < bounds.sizeZ()) {
                    taken.set(x * bounds.sizeZ() + z);
                }
            }
            if (s.templates().byIndex(buildings.template(handle)).classes().contains(CIVIC_CENTRE)) {
                centres.add(new PlacementRules.Centre(at, buildings.faction(handle)));
            }
        }
        for (int id = 1; id <= s.nodes().count(); id++) {
            long[] blocks = s.nodes().blocks(id);
            for (int k = s.nodes().gone(id); k < blocks.length; k++) {
                int x = NodeStore.x(blocks[k]);
                int z = NodeStore.z(blocks[k]);
                if (x < bounds.sizeX() && z < bounds.sizeZ()) {
                    taken.set(x * bounds.sizeZ() + z);
                }
            }
        }
        SurfaceGrid grid = s.surfaces();
        List<PlacementRules.Centre> civic = List.copyOf(centres);
        return new PlacementRules.World() {
            @Override
            public int sizeX() {
                return bounds.sizeX();
            }

            @Override
            public int sizeZ() {
                return bounds.sizeZ();
            }

            @Override
            public double floor(int x, int z) {
                return Placing.floor(grid, bounds, x, z);
            }

            @Override
            public boolean occupied(int x, int z) {
                return taken.get(x * bounds.sizeZ() + z);
            }

            @Override
            public boolean explored(int x, int z) {
                return true; // every cell until fog (M6), as CAM-05's drop point (M2 spec §1.6 row 44)
            }

            @Override
            public List<PlacementRules.Centre> civicCentres() {
                return civic;
            }
        };
    }

    /** Whether a building of {@code template} blocks units on its columns: {@code Footprint/obstructs}, yes unless 0. */
    public static boolean obstructs(MatchState s, int template, int faction) {
        int p = s.stats().path("Footprint/obstructs");
        double v = p < 0 ? Double.NaN : s.stats().blockValue(template, faction, p);
        return !Double.isFinite(v) || v != 0;
    }

    /** The height a unit stands at on column (x, z)'s top surface, or no land under water or with no surface. */
    public static double floor(SurfaceGrid grid, MatchBounds bounds, int x, int z) {
        if (grid.version() == 0) {
            return bounds.flatFloorY(); // a match without terrain is flat ground everywhere
        }
        int n = grid.count(x, z);
        if (n == 0) {
            return PlacementRules.NO_LAND;
        }
        int top = grid.surface(x, z, n - 1);
        int kind = Surface.kind(top);
        if (kind == Surface.SHALLOW_WATER || kind == Surface.DEEP_WATER) {
            return PlacementRules.NO_LAND;
        }
        return bounds.minY() + Surface.floor(top) / 16.0;
    }

    /**
     * As {@link #floor}, but no land where a unit cannot stand: a top surface it never enters, such as a berry bush's
     * or lava's, is ground to the placement rules and none to walk on.
     */
    public static double standFloor(SurfaceGrid grid, MatchBounds bounds, int x, int z) {
        double f = floor(grid, bounds, x, z);
        if (Double.isNaN(f) || grid.version() == 0 || Surface.passable(grid.surface(x, z, grid.count(x, z) - 1))) {
            return f;
        }
        return PlacementRules.NO_LAND;
    }

    /** What template {@code template} asks of a placement for {@code faction}, its cost against the treasury. */
    public static PlacementRules.Params params(MatchState s, int template, int faction) {
        UnitTemplate t = s.templates().byIndex(template);
        long[] cost = s.costs().of(s.stats(), template, faction);
        return new PlacementRules.Params(
                t.classes().contains(CIVIC_CENTRE),
                value(s, template, faction, SPACING),
                value(s, template, faction, CIVIC_REACH),
                s.treasury().missing(faction, cost) == null);
    }

    private static double value(MatchState s, int template, int faction, String path) {
        int p = s.stats().path(path);
        double v = p < 0 ? Double.NaN : s.stats().blockValue(template, faction, p);
        return Double.isFinite(v) ? Math.max(0, v) : 0;
    }

    /** The most buildings of {@code template} faction {@code faction} may have, its {@code Limit/count}; 0 for no limit. */
    public static int limit(MatchState s, int template, int faction) {
        int p = s.stats().path("Limit/count");
        double v = p < 0 ? Double.NaN : s.stats().blockValue(template, faction, p);
        return Double.isFinite(v) && v > 0 ? (int) Math.round(v) : 0;
    }

    /** How many buildings of {@code template} faction {@code faction} has, foundations and sites among them. */
    public static int owned(MatchState s, int template, int faction) {
        BuildingStore b = s.buildings();
        int n = 0;
        for (int handle : b.handles()) {
            n += b.template(handle) == template && b.faction(handle) == faction ? 1 : 0;
        }
        return n;
    }

    /**
     * The height of a building's floor layer at {@code at}: it replaces the ground's top block, at the highest
     * standing height under its columns, or the flat floor's in a match without terrain.
     */
    public static int baseY(MatchState s, Blueprint b, Footprint at) {
        MatchBounds bounds = s.config().bounds();
        double stand = Double.NEGATIVE_INFINITY;
        for (int cell : at.columns(b)) {
            double f = floor(s.surfaces(), bounds, cell >>> 16, cell & 0xFFFF);
            if (!Double.isNaN(f)) {
                stand = Math.max(stand, f);
            }
        }
        if (stand == Double.NEGATIVE_INFINITY) {
            stand = bounds.flatFloorY();
        }
        return (int) Math.round(stand) - 1;
    }

    /**
     * The builder slots of a footprint: the cells just outside it that touch it along an edge or at a corner, inside
     * the match, ascending; whether a unit can stand there is the builder's check.
     */
    public static int[] slots(MatchState s, Footprint at) {
        MatchBounds bounds = s.config().bounds();
        List<Integer> out = new ArrayList<>();
        for (int x = at.anchorX() - 1; x <= at.anchorX() + at.spanX(); x++) {
            for (int z = at.anchorZ() - 1; z <= at.anchorZ() + at.spanZ(); z++) {
                boolean inside = x >= at.anchorX()
                        && x < at.anchorX() + at.spanX()
                        && z >= at.anchorZ()
                        && z < at.anchorZ() + at.spanZ();
                if (!inside && x >= 0 && z >= 0 && x < bounds.sizeX() && z < bounds.sizeZ()) {
                    out.add((x << 16) | z);
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
