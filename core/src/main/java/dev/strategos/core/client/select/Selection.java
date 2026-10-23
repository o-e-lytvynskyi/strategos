package dev.strategos.core.client.select;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * The player's selection (UI-03, 04 §13.4): own units only, at most {@value #MOST}, in handle order. A click selects one
 * own unit or inspects an enemy without selecting it; Shift with a click adds or removes it; a box takes the own
 * units whose feet or head project inside it, Shift adding to what is selected; a double click takes every own unit
 * of that template on screen. An empty click or box clears, unless Shift is held. A click on a building (UI-06) selects
 * an own one alone, or inspects another faction's; taking units drops it.
 */
public final class Selection {
    public static final int MOST = 300;

    private final int faction;
    private final TreeSet<Integer> selected = new TreeSet<>();
    private @Nullable Integer inspected;
    private @Nullable Integer building;
    private boolean ownBuilding;

    /** A selection for the player who commands {@code faction}. */
    public Selection(int faction) {
        this.faction = faction;
    }

    public int faction() {
        return faction;
    }

    public void click(@Nullable Seen unit, boolean shift) {
        if (unit == null) {
            if (!shift) {
                clear();
            }
            return;
        }
        building = null;
        if (unit.faction() != faction) {
            inspected = unit.handle();
            if (!shift) {
                selected.clear();
            }
            return;
        }
        inspected = null;
        if (shift) {
            if (!selected.remove(unit.handle()) && selected.size() < MOST) {
                selected.add(unit.handle());
            }
            return;
        }
        selected.clear();
        selected.add(unit.handle());
    }

    /** A click on building {@code handle} of {@code faction}: an own one is selected alone, another inspected. */
    public void clickBuilding(int handle, int faction) {
        selected.clear();
        inspected = null;
        building = handle;
        ownBuilding = faction == this.faction;
    }

    public void box(List<Seen> seen, double x0, double y0, double x1, double y1, boolean shift) {
        List<Seen> own = new ArrayList<>();
        for (Seen s : seen) {
            if (s.faction() == faction && s.inside(x0, y0, x1, y1)) {
                own.add(s);
            }
        }
        replaceOrAdd(own, shift);
    }

    /** Every own unit of {@code unit}'s template among {@code seen}; an enemy is only inspected. */
    public void allOfType(List<Seen> seen, Seen unit, boolean shift) {
        if (unit.faction() != faction) {
            click(unit, shift);
            return;
        }
        List<Seen> same = new ArrayList<>();
        for (Seen s : seen) {
            if (s.faction() == faction && s.template() == unit.template()) {
                same.add(s);
            }
        }
        replaceOrAdd(same, shift);
    }

    /** Replaces the selection with {@code handles}, own ones as given, as a control group recalls it. */
    public void set(List<Integer> handles) {
        selected.clear();
        inspected = null;
        building = null;
        for (int h : handles) {
            if (selected.size() < MOST) {
                selected.add(h);
            }
        }
    }

    public void add(List<Integer> handles) {
        if (!handles.isEmpty()) {
            building = null;
        }
        for (int h : handles) {
            if (selected.size() < MOST) {
                selected.add(h);
            }
        }
    }

    /** Drops the handles {@code alive} no longer holds. */
    public void prune(IntPredicate alive) {
        selected.removeIf(h -> !alive.test(h));
        if (inspected != null && !alive.test(inspected)) {
            inspected = null;
        }
    }

    /** Drops the building if {@code standing} no longer holds it. */
    public void pruneBuilding(IntPredicate standing) {
        if (building != null && !standing.test(building)) {
            building = null;
        }
    }

    public void clear() {
        selected.clear();
        inspected = null;
        building = null;
    }

    /** The selected handles, ascending. */
    public List<Integer> handles() {
        return List.copyOf(selected);
    }

    public boolean contains(int handle) {
        return selected.contains(handle);
    }

    /** The own building selected, alone; null when none. */
    public @Nullable Integer building() {
        return ownBuilding ? building : null;
    }

    /** The building shown in the panel, own or inspected; null when none. */
    public @Nullable Integer shownBuilding() {
        return building;
    }

    /** The enemy last clicked, shown but never commanded; null when none. */
    public @Nullable Integer inspected() {
        return inspected;
    }

    private void replaceOrAdd(List<Seen> units, boolean shift) {
        if (!shift) {
            selected.clear();
        }
        inspected = null;
        if (!shift || !units.isEmpty()) {
            building = null;
        }
        units.sort((a, b) -> Integer.compare(a.handle(), b.handle()));
        for (Seen s : units) {
            if (selected.size() >= MOST) {
                break;
            }
            selected.add(s.handle());
        }
    }
}
