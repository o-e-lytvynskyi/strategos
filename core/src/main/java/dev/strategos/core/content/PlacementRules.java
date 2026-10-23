package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.DetMath;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where a building may be placed (BLD-02, 04 §11.6, M3 spec §1.6 rows 12–13): one class for the client's prediction
 * and the server's verdict, which differ only in the {@link World} they read. The rules, in the order they are told:
 * every footprint column inside the match space, free of footprints (foundations included), nodes and reserved cells,
 * on land and explored; a floor spread of at most {@value #MAX_SPREAD} blocks; a civic centre at least its spacing from
 * every other, any other building within its reach of one of its faction's civic centres; and the cost paid. The
 * column rules are told per cell, so a ghost colours each; the others fail every cell. A column under a building is
 * told as taken, not as without land, though the server's surfaces have none there.
 */
@CoreApi
public final class PlacementRules {
    /** The most a footprint's floor may rise across its columns, in blocks: flat sites until BLD-04 levels them. */
    public static final double MAX_SPREAD = 2;

    /** No standing floor in a column: water, or no ground at all. */
    public static final double NO_LAND = Double.NaN;

    /** The rules, in the order a verdict tells the first that fails. */
    public enum Rule {
        INSIDE,
        FREE,
        LAND,
        EXPLORED,
        FLAT,
        SPACING,
        REACH,
        AFFORDABLE;

        /** Where it comes in the order rules are told, 0 first; the declaration order, written out. */
        public int rank() {
            return switch (this) {
                case INSIDE -> 0;
                case FREE -> 1;
                case LAND -> 2;
                case EXPLORED -> 3;
                case FLAT -> 4;
                case SPACING -> 5;
                case REACH -> 6;
                case AFFORDABLE -> 7;
            };
        }

        /** Whether the rule is judged per footprint column. */
        public boolean perCell() {
            return this == INSIDE || this == LAND || this == FREE || this == EXPLORED;
        }
    }

    /** A civic centre that stands or is being built, and its faction. */
    public record Centre(Footprint at, int faction) {}

    /** What the rules read of the world. */
    public interface World {
        int sizeX();

        int sizeZ();

        /** The height a unit stands at in column (x, z), in blocks, or {@link #NO_LAND}. */
        double floor(int x, int z);

        /** Whether a footprint (a foundation's too), a node's block or a reserved cell takes column (x, z). */
        boolean occupied(int x, int z);

        boolean explored(int x, int z);

        /** Every civic centre of the match, foundations included. */
        List<Centre> civicCentres();
    }

    /**
     * What the template being placed asks: whether it is a civic centre, the spacing civic centres keep between their
     * centres, how far from an own civic centre's footprint it may stand (0 for no such rule), and whether its faction
     * can pay for it.
     */
    public record Params(boolean civicCentre, double spacing, double civicReach, boolean affordable) {}

    /**
     * A verdict: the first rule that fails, null for a placement that may go ahead, and per footprint column ({@code
     * cells}, each {@code x << 16 | z}, ascending) the first rule that fails there, null where none does.
     */
    public record Verdict(@Nullable Rule failed, List<Integer> cells, List<@Nullable Rule> perCell) {
        public Verdict {
            cells = List.copyOf(cells);
            perCell = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(perCell));
        }

        public boolean ok() {
            return failed == null;
        }
    }

    private PlacementRules() {}

    /** The verdict for blueprint {@code b} at {@code at} for {@code faction}, as {@code params} asks. */
    public static Verdict check(World world, Blueprint b, Footprint at, int faction, Params params) {
        if (at.anchorX() < 0 || at.anchorZ() < 0) {
            return new Verdict(Rule.INSIDE, List.of(), List.of()); // columns are packed unsigned: none to tell
        }
        int[] cells = at.columns(b);
        Rule[] rules = new Rule[cells.length];
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < cells.length; k++) {
            int x = cells[k] >> 16;
            int z = cells[k] & 0xFFFF;
            if (x >= world.sizeX() || z >= world.sizeZ()) {
                rules[k] = Rule.INSIDE;
                continue;
            }
            if (world.occupied(x, z)) {
                rules[k] = Rule.FREE;
                continue;
            }
            double floor = world.floor(x, z);
            if (Double.isNaN(floor)) {
                rules[k] = Rule.LAND;
                continue;
            }
            low = Math.min(low, floor);
            high = Math.max(high, floor);
            if (!world.explored(x, z)) {
                rules[k] = Rule.EXPLORED;
            }
        }
        Rule failed = null;
        for (Rule r : rules) {
            if (r != null && (failed == null || r.rank() < failed.rank())) {
                failed = r;
            }
        }
        Rule whole = null;
        if (high - low > MAX_SPREAD + 1e-9) {
            whole = Rule.FLAT;
        } else if (params.civicCentre() && tooClose(world, at, params.spacing())) {
            whole = Rule.SPACING;
        } else if (!params.civicCentre() && params.civicReach() > 0 && !inReach(world, at, faction, params)) {
            whole = Rule.REACH;
        } else if (!params.affordable()) {
            whole = Rule.AFFORDABLE;
        }
        if (whole != null && (failed == null || whole.rank() < failed.rank())) {
            failed = whole;
        }
        if (whole != null) {
            for (int k = 0; k < rules.length; k++) {
                if (rules[k] == null || whole.rank() < rules[k].rank()) {
                    rules[k] = whole;
                }
            }
        }
        List<Integer> cellList = Arrays.stream(cells).boxed().toList();
        return new Verdict(failed, cellList, Arrays.asList(rules));
    }

    /** Whether another civic centre's centre is nearer than {@code spacing} to this footprint's. */
    private static boolean tooClose(World world, Footprint at, double spacing) {
        for (Centre c : world.civicCentres()) {
            double dx = centreX(c.at()) - centreX(at);
            double dz = centreZ(c.at()) - centreZ(at);
            if (DetMath.length(dx, dz) < spacing) {
                return true;
            }
        }
        return false;
    }

    /** Whether this footprint comes within the reach of one of the faction's civic centres' footprints. */
    private static boolean inReach(World world, Footprint at, int faction, Params params) {
        for (Centre c : world.civicCentres()) {
            if (c.faction() == faction && gap(c.at(), at) <= params.civicReach() + 1e-9) {
                return true;
            }
        }
        return false;
    }

    /** The distance between two footprints' edges, 0 where they touch or overlap. */
    public static double gap(Footprint a, Footprint b) {
        double dx =
                Math.max(0, Math.max(a.anchorX() - (b.anchorX() + b.spanX()), b.anchorX() - (a.anchorX() + a.spanX())));
        double dz =
                Math.max(0, Math.max(a.anchorZ() - (b.anchorZ() + b.spanZ()), b.anchorZ() - (a.anchorZ() + a.spanZ())));
        return DetMath.length(dx, dz);
    }

    private static double centreX(Footprint f) {
        return f.anchorX() + f.spanX() / 2.0;
    }

    private static double centreZ(Footprint f) {
        return f.anchorZ() + f.spanZ() / 2.0;
    }
}
