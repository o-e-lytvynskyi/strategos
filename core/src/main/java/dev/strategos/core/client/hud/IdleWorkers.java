package dev.strategos.core.client.hud;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.UnitSummary;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The idle-worker key (UI-10): the faction's idle workers, from the last {@code unit_summary} (its own units whose
 * template gathers, standing idle), in index order; each press takes the next after the one it took last, round to the
 * first, so a press visits every idle worker once before any twice, however others join or leave the set.
 */
public final class IdleWorkers {
    /** The state code of an idle unit in a summary entry. */
    public static final int IDLE = 0;

    private int last = -1;

    /** Faction {@code faction}'s idle workers in {@code units}, in index order. */
    public static List<UnitSummary.Entry> idle(List<UnitSummary.Entry> units, int faction, TemplateSet templates) {
        List<UnitSummary.Entry> out = new ArrayList<>();
        for (UnitSummary.Entry e : units) {
            if (e.faction() == faction
                    && e.state() == IDLE
                    && templates.has(e.template())
                    && templates.byIndex(e.template()).classes().contains("Gatherer")) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return out;
    }

    /** The next idle worker after the last taken, round to the first; null when none is idle. */
    public UnitSummary.@Nullable Entry next(List<UnitSummary.Entry> units, int faction, TemplateSet templates) {
        List<UnitSummary.Entry> idle = idle(units, faction, templates);
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
