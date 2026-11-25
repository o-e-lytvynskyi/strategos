package dev.strategos.core.sim.prod;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchFactionView;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.build.BuildingLife;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.DamageStages;
import dev.strategos.core.sim.capture.CaptureSystem;
import dev.strategos.core.sim.garrison.Garrisons;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A building's upgrade (BLD-12, M9 spec §2.8): the template names the one it becomes ({@code Upgrade/to}), how long it
 * takes, the phase it needs and its cost. The upgrade waits in the building's queue as an entry that is a building
 * template's net index (a unit's entry is a unit's, a tech's is negative), takes its {@code Upgrade/time}, and at its end
 * the building is the new template in place: its health ratio, garrison, capture points and queue stay.
 */
@CoreApi
public final class Upgrades {
    private Upgrades() {}

    /** The net index of the template building {@code handle} upgrades to, or -1 when its template names none. */
    public static int target(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int path = s.stats().tokenPath(CorePaths.UPGRADE_TO);
        List<String> to = path < 0 ? null : s.stats().blockTokens(b.template(handle), b.faction(handle), path);
        if (to == null || to.size() != 1) {
            return -1;
        }
        UnitTemplate t = s.templates().byId(new TemplateId(to.get(0)));
        return t != null && t.isBuilding() ? t.netIndex() : -1;
    }

    /** Whether queue entry {@code entry} is an upgrade: a template's index that is a building's. */
    public static boolean isUpgrade(MatchState s, int entry) {
        return entry >= 0
                && s.templates().has(entry)
                && s.templates().byIndex(entry).isBuilding();
    }

    /** The ticks building {@code handle}'s upgrade takes: its {@code Upgrade/time} seconds, at least one tick. */
    public static long ticks(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int path = s.stats().path(CorePaths.UPGRADE_TIME);
        double seconds = path < 0 ? Double.NaN : s.stats().blockValue(b.template(handle), b.faction(handle), path);
        return Double.isFinite(seconds) && seconds > 0 ? Math.max(1, Math.round(seconds * 20)) : 1;
    }

    /** What building {@code handle}'s upgrade costs its faction, per resource in thousandths. */
    public static long[] cost(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        return s.costs().ofUpgrade(s.stats(), b.template(handle), b.faction(handle));
    }

    /** Whether the faction of building {@code handle} has every phase tech its upgrade {@code requires}. */
    public static boolean requirementsMet(MatchState s, int handle) {
        BuildingStore b = s.buildings();
        int path = s.stats().tokenPath(CorePaths.UPGRADE_REQUIRES);
        List<String> needs = path < 0 ? null : s.stats().blockTokens(b.template(handle), b.faction(handle), path);
        if (needs == null) {
            return true;
        }
        MatchFactionView view = new MatchFactionView(s, b.faction(handle));
        for (String need : needs) {
            if (!view.researched(new TechId(need))) {
                return false;
            }
        }
        return true;
    }

    /** Whether template {@code to} stands on building {@code handle}'s footprint as it is: the same size in blocks. */
    public static boolean fits(MatchState s, int handle, int to) {
        Blueprint now = s.blueprintOf(handle);
        Blueprint next = s.templates().blueprint(to);
        return next != null && next.sizeX() == now.sizeX() && next.sizeZ() == now.sizeZ();
    }

    /** Whether building {@code handle}'s queue already holds an upgrade. */
    public static boolean pending(MatchState s, int handle) {
        for (int entry : s.buildings().queues().queue(handle)) {
            if (isUpgrade(s, entry)) {
                return true;
            }
        }
        return false;
    }

    /** Puts an upgrade to {@code to} at the head of building {@code handle}'s queue; false when the queue is full. */
    public static boolean enqueue(MatchState s, int handle, int to) {
        BuildingStore b = s.buildings();
        int faction = b.faction(handle);
        int size = b.queues().queueSize(handle);
        if (size >= BuildingStore.QUEUE_MAX) {
            return false;
        }
        if (size > 0
                && b.queues()
                        .reserved(handle)) { // the old head loses its place and its progress: its housing goes back
            int old = b.queues().queue(handle)[0];
            s.population()
                    .remove(
                            faction,
                            s.templatePopulation(old, faction) * b.queues().batch(handle, 0));
        }
        return b.queues().enqueueHead(handle, to);
    }

    /** The upgrade at the head of building {@code handle}'s queue takes a tick; at its end the building changes. */
    static void step(MatchState s, int handle, int to) {
        BuildingStore b = s.buildings();
        long done = b.queues().trained(handle) + 1;
        b.queues().setTrained(handle, done);
        if (done < ticks(s, handle)) {
            return;
        }
        b.queues().dequeue(handle, 0);
        apply(s, handle, to);
    }

    /**
     * Building {@code handle} becomes template {@code to} in place: the health ratio and the capture points keep their
     * shares, the garrison stays but for whoever the new holder cannot take, housing, stats, territory and vision
     * follow the template, and the writer is asked for the new blueprint in the same footprint, its damage stage
     * worked out afresh.
     */
    public static void apply(MatchState s, int handle, int to) {
        BuildingStore b = s.buildings();
        int faction = b.faction(handle);
        boolean finished = b.state(handle) == BuildingStore.COMPLETE;
        double oldMax = s.buildingMaxHp(handle);
        long oldPoints = CaptureSystem.total(s, handle);
        if (!fits(s, handle, to)) {
            throw new IllegalStateException("an upgrade changes the footprint of building " + handle);
        }
        if (finished) {
            s.population().unhouse(faction, Production.housing(s, handle));
        }
        b.setTemplate(handle, to);
        if (finished) {
            s.population().house(faction, Production.housing(s, handle));
        }
        double newMax = s.buildingMaxHp(handle);
        b.setDamage(handle, oldMax > 0 ? b.damage(handle) * newMax / oldMax : 0);
        scaleCapture(s, handle, oldPoints, CaptureSystem.total(s, handle));
        ejectOver(s, handle);
        Blueprint bp = s.blueprintOf(handle);
        Footprint at = s.footprintOf(handle);
        b.setStage(handle, 0);
        BuildingLife.writeBlueprint(s, Handle.index(handle), handle, bp, at, b.baseY(handle));
        DamageStages.update(s, handle);
    }

    private static void scaleCapture(MatchState s, int handle, long oldTotal, long newTotal) {
        long @Nullable [] points = s.capture().points(handle);
        if (points == null || oldTotal <= 0) {
            return;
        }
        for (int f = 0; f < points.length; f++) {
            points[f] = Math.multiplyExact(points[f], newTotal) / oldTotal;
        }
    }

    /** The occupants beyond the new capacity, or of a class the new holder does not admit, come out at the door. */
    private static void ejectOver(MatchState s, int handle) {
        if (s.garrison().empty()) {
            return;
        }
        int room = Garrisons.capacity(s, handle);
        List<Integer> out = new ArrayList<>();
        for (int unit : s.garrison().occupants(handle)) {
            if (room > 0 && Garrisons.admits(s, Handle.index(unit), handle)) {
                room--;
            } else {
                out.add(unit);
            }
        }
        if (!out.isEmpty()) {
            Garrisons.release(
                    s, handle, out.stream().mapToInt(Integer::intValue).toArray());
            s.spatial().rebuild(s);
        }
    }
}
