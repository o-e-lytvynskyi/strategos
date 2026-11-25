package dev.strategos.core.sim.build;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.nav.GateDoors;
import dev.strategos.core.sim.nav.GateOverlay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The gates of finished palisade gates (BLD-13, M9 spec §2.10): a building whose template has {@code Wall/gate} stands
 * as a gate of its owner's over its middle two columns once finished, in {@link GateOverlay}
 * as {@link MatchState#addGate} keeps it, and is gone from it when the building falls or changes hands. The overlay
 * is saved with the match on its own, so the building and its gate are tied only by their columns.
 */
public final class WallGates {
    public static final String GATE = "Wall/gate";

    private WallGates() {}

    private static double value(MatchState s, int handle, String path) {
        int p = s.stats().path(path);
        double v = p < 0
                ? Double.NaN
                : s.stats()
                        .blockValue(
                                s.buildings().template(handle), s.buildings().faction(handle), p);
        return Double.isFinite(v) ? v : 0;
    }

    /** Whether building {@code handle}'s template is a gate. */
    public static boolean isGate(MatchState s, int handle) {
        return value(s, handle, GATE) > 0;
    }

    /** The columns of building {@code handle}'s gate: the middle two of its footprint along its run, ascending. */
    public static int[] columns(MatchState s, int handle) {
        Blueprint b = s.blueprintOf(handle);
        int[] all = s.footprintOf(handle).columns(b);
        if (all.length < 2) {
            return all;
        }
        int mid = all.length / 2;
        return Arrays.copyOfRange(all, mid - 1, mid + 1);
    }

    /** The id of the gate over {@code columns} (ascending), or -1. */
    public static int find(MatchState s, int[] columns) {
        for (int id : s.gates().ids()) {
            int[] have = s.gates().columns(id);
            Arrays.sort(have);
            if (Arrays.equals(have, columns)) {
                return id;
            }
        }
        return -1;
    }

    /** Building {@code handle} now stands finished or changed hands: a gate template becomes its owner's gate. */
    public static void finished(MatchState s, int handle) {
        if (!isGate(s, handle) || s.buildings().state(handle) != BuildingStore.COMPLETE) {
            return;
        }
        int[] columns = columns(s, handle);
        if (find(s, columns) < 0 && s.gates().count() < GateOverlay.MAX_GATES) {
            s.addGate(s.buildings().faction(handle), columns);
        }
    }

    /** Building {@code handle} falls or changes hands: its gate goes. */
    public static void lost(MatchState s, int handle) {
        if (!isGate(s, handle) || s.gates().empty()) {
            return;
        }
        int id = find(s, columns(s, handle));
        if (id >= 0) {
            s.removeGate(id);
        }
    }

    /** The finished gates and whether their doors stand open now, in handle order; none while no gate stands. */
    public static List<GateDoor> doors(MatchState s) {
        if (s.gates().empty()) {
            return List.of();
        }
        List<GateDoor> out = new ArrayList<>();
        BuildingStore b = s.buildings();
        for (int handle : b.handles()) {
            if (b.state(handle) != BuildingStore.COMPLETE || !isGate(s, handle)) {
                continue;
            }
            int[] columns = columns(s, handle);
            int id = find(s, columns);
            if (id < 0) {
                continue;
            }
            out.add(new GateDoor(
                    handle,
                    GateDoors.open(s, id),
                    b.baseY(handle),
                    s.footprintOf(handle).rotation(),
                    Arrays.stream(columns).boxed().toList()));
        }
        return out;
    }
}
