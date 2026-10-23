package dev.strategos.core.client.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.protocol.BuildingState;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The client's side of placement (BLD-02): the match as {@link PlacementRules.World} reads it from what a client has,
 * the buildings of its {@code building_state} and the blocks of its loaded chunks ({@link Columns}), and a template's
 * {@link PlacementRules.Params} from its own copy of the set and its faction's stock. Equal inputs give the server's
 * verdict ({@code PlacementParityTest}); a client's view a tick or two behind may differ, and the server's word stands.
 */
public final class ClientPlacement {
    /** The class token of a civic centre, as the server's {@code Placing}. */
    public static final String CIVIC_CENTRE = "CivicCentre";

    private ClientPlacement() {}

    /** What a client reads of a column from its loaded blocks. */
    public interface Columns {
        /** The height a unit stands at, or {@link PlacementRules#NO_LAND}. */
        double floor(int x, int z);

        /** Whether a block of a node stands in the column: a log or a forage plant, by its resource entry. */
        boolean node(int x, int z);
    }

    /** The match of {@code sizeX} × {@code sizeZ} as the rules read it on a client. */
    public static PlacementRules.World world(
            TemplateSet templates, List<BuildingState.Entry> buildings, int sizeX, int sizeZ, Columns columns) {
        BitSet taken = new BitSet(sizeX * sizeZ);
        List<PlacementRules.Centre> centres = new ArrayList<>();
        for (BuildingState.Entry e : buildings) {
            Blueprint b = e.template() < 0 ? null : templates.blueprint(e.template());
            if (b == null) {
                continue;
            }
            Footprint at = Footprint.of(b, e.x(), e.z(), e.rotation());
            for (int cell : at.columns(b)) {
                int x = cell >>> 16;
                int z = cell & 0xFFFF;
                if (x < sizeX && z < sizeZ) {
                    taken.set(x * sizeZ + z);
                }
            }
            if (templates.has(e.template())
                    && templates.byIndex(e.template()).classes().contains(CIVIC_CENTRE)) {
                centres.add(new PlacementRules.Centre(at, e.faction()));
            }
        }
        List<PlacementRules.Centre> civic = List.copyOf(centres);
        return new PlacementRules.World() {
            @Override
            public int sizeX() {
                return sizeX;
            }

            @Override
            public int sizeZ() {
                return sizeZ;
            }

            @Override
            public double floor(int x, int z) {
                return columns.floor(x, z);
            }

            @Override
            public boolean occupied(int x, int z) {
                return taken.get(x * sizeZ + z) || columns.node(x, z);
            }

            @Override
            public boolean explored(int x, int z) {
                return true; // every cell until fog (M6), as on the server
            }

            @Override
            public List<PlacementRules.Centre> civicCentres() {
                return civic;
            }
        };
    }

    /**
     * What template {@code template} asks, from the client's set: its values without modifiers, which the server adds,
     * and whether {@code stock}, the faction's per resource of {@code resources} in thousandths, covers its cost; a
     * client with no stock yet predicts it can pay.
     */
    public static PlacementRules.Params params(
            TemplateSet templates, int template, List<String> resources, @Nullable List<Long> stock) {
        UnitTemplate t = templates.byIndex(template);
        boolean affordable = true;
        if (stock != null) {
            for (int r = 0; r < resources.size() && r < stock.size(); r++) {
                long cost = Math.round(value(templates, template, "Cost/resources/" + resources.get(r)) * 1_000);
                if (cost > stock.get(r)) {
                    affordable = false;
                }
            }
        }
        return new PlacementRules.Params(
                t.classes().contains(CIVIC_CENTRE),
                value(templates, template, "Placement/spacing"),
                value(templates, template, "Placement/civicReach"),
                affordable);
    }

    private static double value(TemplateSet templates, int template, String path) {
        ValuePath p = templates.valuePaths().byPath(path);
        double v = p == null ? Double.NaN : templates.value(template, p.id());
        return Double.isFinite(v) ? Math.max(0, v) : 0;
    }
}
