package dev.strategos.core.client.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.PlacementRule;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.content.WallLine;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The client's side of a wall line (BLD-13): the segments {@link WallLine} cuts a drag into and each one's verdict from
 * {@link PlacementRules}, as the server's {@code PlaceWall} will judge it, so a ghost shows red where a segment will be
 * refused and a gap will stand. A segment is paid for before the next is judged, as the server does.
 */
@CoreApi
public final class ClientWall {
    private ClientWall() {}

    /** One segment of the preview: where it stands, as which template, and whether it may be laid. */
    @CoreApi
    public record Segment(
            WallLine.Segment line, int template, Footprint at, int baseY, PlacementRules.Verdict verdict) {}

    /** The blocks a segment of {@code template} is long, its {@code Wall/segment}; 0 when it is no wall. */
    public static int segmentLength(TemplateSet templates, int template) {
        ValuePath p = templates.valuePaths().byPath(WallLine.SEGMENT);
        double v = p == null || !templates.has(template) ? Double.NaN : templates.value(template, p.id());
        return Double.isFinite(v) && v > 0 ? (int) Math.round(v) : 0;
    }

    /** The net index of {@code wall}'s gate template, the one whose id adds {@code _gate}; -1 for none. */
    public static int gateOf(TemplateSet templates, int wall) {
        UnitTemplate gate =
                templates.byId(new TemplateId(templates.byIndex(wall).id().value() + WallLine.GATE_SUFFIX));
        return gate == null ? -1 : gate.netIndex();
    }

    /**
     * The preview of the run from ({@code x1}, {@code z1}) to ({@code x2}, {@code z2}) of {@code wall}'s segments, the
     * {@code gates} indices of its gate template's; empty when {@code wall} is no wall, or the line is too long for one
     * command, or a gate is asked for and {@code wall} has none. {@code stock} (null: it can pay) is the faction's per
     * resource of {@code resources}, in thousandths. Each segment is judged by {@code rule}, the faction's civilisation's
     * placement rule (CIV-05), its civic centres by {@code centreClass}.
     */
    public static List<Segment> preview(
            PlacementRule rule,
            TemplateSet templates,
            int wall,
            int x1,
            int z1,
            int x2,
            int z2,
            List<Integer> gates,
            PlacementRules.World world,
            int faction,
            List<String> resources,
            @Nullable List<Long> stock,
            String centreClass) {
        int length = segmentLength(templates, wall);
        int gate = gateOf(templates, wall);
        Blueprint wallBlueprint = length > 0 ? templates.blueprint(wall) : null;
        if (wallBlueprint == null
                || WallLine.count(x1, z1, x2, z2, length) > dev.strategos.core.protocol.PlaceWall.MAX_SEGMENTS
                || (!gates.isEmpty() && (gate < 0 || templates.blueprint(gate) == null))) {
            return List.of();
        }
        List<Long> left = stock == null ? null : new ArrayList<>(stock);
        List<Segment> out = new ArrayList<>();
        for (WallLine.Segment s : WallLine.of(x1, z1, x2, z2, length, gates, world::floor)) {
            int template = s.gate() ? gate : wall;
            Blueprint b = s.gate() ? templates.blueprint(gate) : wallBlueprint;
            if (b == null) {
                return List.of();
            }
            Footprint at = Footprint.of(b, s.x(), s.z(), s.rotation());
            PlacementRules.Params base = ClientPlacement.params(templates, template, resources, left, centreClass);
            PlacementRules.Verdict verdict = PlacementRules.check(rule, world, b, at, faction, base);
            if (verdict.ok() && left != null) {
                for (int r = 0; r < resources.size() && r < left.size(); r++) {
                    long cost = Math.round(
                            ClientPlacement.value(templates, template, "Cost/resources/" + resources.get(r)) * 1_000);
                    left.set(r, left.get(r) - cost);
                }
            }
            int baseY = s.baseY() == Integer.MIN_VALUE ? 0 : s.baseY();
            out.add(new Segment(s, template, at, baseY, verdict));
        }
        return out;
    }
}
