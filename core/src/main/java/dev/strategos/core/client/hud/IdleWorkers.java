package dev.strategos.core.client.hud;

import dev.strategos.core.client.select.UnitRole;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.UnitSummary;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The idle-unit keys (UI-10, UI-17): the faction's idle workers or idle soldiers, from the last {@code unit_summary}
 * (its own units of the role, standing idle), in index order; each press takes the next after the one it took last,
 * round to the first, so a press visits every idle unit once before any twice, however others join or leave the set.
 * A unit that holds a standing order (the summary's flag), a guard beside its charge, reports idle but is not idle here.
 */
public final class IdleWorkers {
    /** The state code of an idle unit in a summary entry. */
    public static final int IDLE = 0;

    private final UnitRole role;
    private int last = -1;

    /** The idle-worker key. */
    public IdleWorkers() {
        this(UnitRole.WORKER);
    }

    /** The key for {@code role}'s idle units. */
    public IdleWorkers(UnitRole role) {
        this.role = role;
    }

    /** Faction {@code faction}'s idle workers in {@code units}, in index order. */
    public static List<UnitSummary.Entry> idle(List<UnitSummary.Entry> units, int faction, TemplateSet templates) {
        return idle(units, faction, templates, UnitRole.WORKER);
    }

    /** Faction {@code faction}'s idle units of {@code role} in {@code units}, in index order, none on a standing order. */
    public static List<UnitSummary.Entry> idle(
            List<UnitSummary.Entry> units, int faction, TemplateSet templates, UnitRole role) {
        List<UnitSummary.Entry> out = new ArrayList<>();
        for (UnitSummary.Entry e : units) {
            if (e.faction() == faction && e.state() == IDLE && !e.standing() && role.has(templates, e.template())) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return out;
    }

    /** The next idle unit after the last taken, round to the first; null when none is idle. */
    public UnitSummary.@Nullable Entry next(List<UnitSummary.Entry> units, int faction, TemplateSet templates) {
        List<UnitSummary.Entry> idle = idle(units, faction, templates, role);
        if (idle.isEmpty()) {
            return null;
        }
        UnitSummary.Entry pick = idle.get(0);
        for (UnitSummary.Entry e : idle) {
            if (e.index() > last) {
                pick = e;
                break;
            }
        }
        last = pick.index();
        return pick;
    }
}
