package dev.strategos.core.client.order;

import dev.strategos.core.client.hud.ClientFactionView;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.CancelBuilding;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.DeleteUnits;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.Handle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;

/**
 * The commands the order hotkeys send (UI-17): the all clear, garrison and delete, made from the selection and what
 * the client knows. The server checks every rule again; these only choose the targets.
 */
public final class HotkeyOrders {
    private HotkeyOrders() {}

    /** Whether own finished {@code building} holds a garrison, so a bell rings and an all clear sounds at it. */
    public static boolean holdsGarrison(TemplateSet templates, BuildingState.Entry building) {
        if (!building.own() || building.state() != CommandCard.COMPLETE || !templates.has(building.template())) {
            return false;
        }
        ValuePath capacity = templates.valuePaths().byPath(CorePaths.GARRISON_CAPACITY);
        return capacity != null && templates.value(building.template(), capacity.id()) > 0;
    }

    /**
     * Back to work: the all clear at {@code selected} when it is an own garrison holder, else at every own garrison
     * holder; none when there is no holder.
     */
    public static List<Command> backToWork(
            TemplateSet templates, BuildingState.@Nullable Entry selected, List<BuildingState.Entry> buildings) {
        List<Command> out = new ArrayList<>();
        if (selected != null && holdsGarrison(templates, selected)) {
            out.add(new AllClear(selected.handle()));
            return out;
        }
        for (BuildingState.Entry b : buildings) {
            if (holdsGarrison(templates, b)) {
                out.add(new AllClear(b.handle()));
            }
        }
        return out;
    }

    /**
     * Garrison: the selected units that some own holder takes go into the holder nearest their centre, among the
     * holders that admit at least one of them; null when none does. {@code unitOf} gives a unit's summary entry by its
     * index, null for a unit the summary lacks.
     */
    public static @Nullable Command garrison(
            TemplateSet templates,
            ClientFactionView view,
            List<Integer> selected,
            IntFunction<UnitSummary.@Nullable Entry> unitOf,
            List<BuildingState.Entry> buildings) {
        List<UnitSummary.Entry> units = new ArrayList<>();
        List<Integer> handles = new ArrayList<>();
        double cx = 0;
        double cz = 0;
        for (int handle : selected) {
            UnitSummary.Entry e = unitOf.apply(Handle.index(handle));
            if (e != null && templates.has(e.template())) {
                units.add(e);
                handles.add(handle);
                cx += e.x();
                cz += e.z();
            }
        }
        if (units.isEmpty()) {
            return null;
        }
        cx /= units.size();
        cz /= units.size();
        BuildingState.Entry best = null;
        double bestD = Double.MAX_VALUE;
        for (BuildingState.Entry b : buildings) {
            if (!holdsGarrison(templates, b)) {
                continue;
            }
            List<String> admitted = view.tokens(b.template(), CorePaths.GARRISON_CLASSES);
            boolean any = false;
            for (UnitSummary.Entry u : units) {
                any |= admits(admitted, templates, u.template());
            }
            double d = Math.hypot(b.x() - cx, b.z() - cz);
            if (any && d < bestD) {
                best = b;
                bestD = d;
            }
        }
        if (best == null) {
            return null;
        }
        List<String> admitted = view.tokens(best.template(), CorePaths.GARRISON_CLASSES);
        int[] inside = new int[handles.size()];
        int n = 0;
        for (int k = 0; k < handles.size(); k++) {
            if (admits(admitted, templates, units.get(k).template())) {
                inside[n++] = handles.get(k);
            }
        }
        return new Garrison(UnitSet.of(java.util.Arrays.copyOf(inside, n)), best.handle());
    }

    private static boolean admits(List<String> admitted, TemplateSet templates, int template) {
        for (String token : templates.byIndex(template).classes()) {
            if (admitted.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delete: the selected units die (one {@link DeleteUnits}) and the selected own building falls, demolished when it
     * is finished and cancelled when it is a site; empty for an empty selection.
     */
    public static List<Command> delete(List<Integer> selected, BuildingState.@Nullable Entry building) {
        List<Command> out = new ArrayList<>();
        if (!selected.isEmpty()) {
            out.add(new DeleteUnits(
                    UnitSet.of(selected.stream().mapToInt(Integer::intValue).toArray())));
        }
        if (building != null && building.own()) {
            out.add(
                    building.state() == CommandCard.COMPLETE
                            ? new Demolish(building.handle())
                            : new CancelBuilding(building.handle()));
        }
        return out;
    }
}
