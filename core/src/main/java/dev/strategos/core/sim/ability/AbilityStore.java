package dev.strategos.core.sim.ability;

import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.spatial.UnitFilter;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.StatService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The abilities' state in one match (UNIT-10): per (unit handle, ability id) the tick the ability is ready again, and
 * the {@link TimedModifiers}. Sorted by (handle, id), so the hash and the save do not depend on history; empty (and then
 * absent from the hash and the save) while no ability has been used. A cooldown that has run out is dropped by {@link
 * #sweep}, so an old match hashes like one that never used one.
 */
public final class AbilityStore {
    /** What a save keeps (the M9 pattern: optional, written only when something is in it). */
    public record Saved(List<String> ids, List<Long> cooldowns, List<Long> timed) {
        public static final Saved EMPTY = new Saved(List.of(), List.of(), List.of());

        public Saved {
            ids = List.copyOf(ids);
            cooldowns = List.copyOf(cooldowns);
            timed = List.copyOf(timed);
        }
    }

    private int[] handles = new int[4];
    private String[] ids = new String[4];
    private long[] ready = new long[4];
    private int count;
    private final TimedModifiers timed = new TimedModifiers();
    /** One ability's compiled effects and the modifiers of its timed effect, made once per set. */
    private record Compiled(List<AbilityEffects.Effect> effects, List<Modifier> modifiers) {}

    private @Nullable TemplateSet cachedFor;
    private Compiled[] compiled = new Compiled[0]; // by ability index in cachedFor, null until first used
    private @Nullable UnitFilter[] enemies = new UnitFilter[0]; // by faction
    private boolean moved; // a unit was put elsewhere since the spatial grid was last rebuilt

    public boolean empty() {
        return count == 0 && timed.empty();
    }

    /** The timed modifiers. */
    public TimedModifiers timed() {
        return timed;
    }

    /** The tick on which unit {@code handle} may use ability {@code id} again; 0 when it has not used it lately. */
    public long readyAt(int handle, String id) {
        int at = find(handle, id);
        return at >= 0 ? ready[at] : 0;
    }

    /** Unit {@code handle} used ability {@code id}: it is ready again at tick {@code readyTick}. */
    public void start(int handle, String id, long readyTick) {
        int at = find(handle, id);
        if (at >= 0) {
            ready[at] = readyTick;
            return;
        }
        at = -at - 1;
        if (count == handles.length) {
            int size = count * 2;
            handles = Arrays.copyOf(handles, size);
            ids = Arrays.copyOf(ids, size);
            ready = Arrays.copyOf(ready, size);
        }
        System.arraycopy(handles, at, handles, at + 1, count - at);
        System.arraycopy(ids, at, ids, at + 1, count - at);
        System.arraycopy(ready, at, ready, at + 1, count - at);
        handles[at] = handle;
        ids[at] = id;
        ready[at] = readyTick;
        count++;
    }

    /** At the tick boundary: cooldowns that have run out, and those of units that are gone, are dropped; then the timed modifiers expire. */
    public void sweep(long tick, StatService stats, EntityStore alive) {
        int kept = 0;
        for (int k = 0; k < count; k++) {
            if (ready[k] > tick && alive.isAlive(handles[k])) {
                if (kept != k) {
                    handles[kept] = handles[k];
                    ids[kept] = ids[k];
                    ready[kept] = ready[k];
                }
                kept++;
            }
        }
        for (int k = kept; k < count; k++) {
            ids[k] = "";
        }
        count = kept;
        timed.expire(tick, stats, alive);
    }

    /** The compiled effects of {@code def}, made once per template set. */
    List<AbilityEffects.Effect> effects(TemplateSet templates, AbilityDef def) {
        return compiled(templates, def).effects();
    }

    /** The modifiers of {@code def}'s timed effect under its source in {@code templates}, made once per set. */
    List<Modifier> modifiers(TemplateSet templates, AbilityDef def) {
        return compiled(templates, def).modifiers();
    }

    @SuppressWarnings("ReferenceEquality") // a swapped-in set is another object, whatever it equals
    private Compiled compiled(TemplateSet templates, AbilityDef def) {
        int index = templates.abilityIndex(def.id());
        if (index < 0) {
            return new Compiled(List.of(AbilityEffects.compile(def)), AbilityEffects.modifiers(def, 0));
        }
        if (cachedFor != templates) {
            cachedFor = templates;
            compiled = new Compiled[templates.abilities().size()];
        }
        Compiled made = compiled[index];
        if (made == null) {
            made = new Compiled(
                    List.of(AbilityEffects.compile(def)), AbilityEffects.modifiers(def, AbilityEffects.source(index)));
            compiled[index] = made;
        }
        return made;
    }

    /** The filter of the units hostile to {@code faction} in a match of {@code players}, made once. */
    UnitFilter enemiesOf(int faction, int players) {
        if (faction >= enemies.length) {
            enemies = Arrays.copyOf(enemies, faction + 1);
        }
        UnitFilter filter = enemies[faction];
        if (filter == null) {
            filter = UnitFilter.enemiesOf(faction, players);
            enemies[faction] = filter;
        }
        return filter;
    }

    /** A unit was put elsewhere: the spatial grid is out of date until it is rebuilt. */
    public void markMoved() {
        moved = true;
    }

    /** Whether a unit was put elsewhere since the last call; the flag is cleared. */
    public boolean takeMoved() {
        boolean was = moved;
        moved = false;
        return was;
    }

    /** The ids of the abilities some unit is on cooldown from or under the timed modifiers of, ascending, distinct. */
    public List<String> idsInUse(EntityStore alive) {
        Map<String, Integer> table = new TreeMap<>();
        for (int k = 0; k < count; k++) {
            if (alive.isAlive(handles[k])) {
                table.put(ids[k], 0);
            }
        }
        for (TimedModifiers.Entry e : timed.entries(alive)) {
            table.put(e.id(), 0);
        }
        return new ArrayList<>(table.keySet());
    }

    /** The hash with the abilities of living units mixed in. */
    public long mix(long h, EntityStore alive) {
        for (int k = 0; k < count; k++) {
            if (alive.isAlive(handles[k])) {
                h = Hasher.mix(h, handles[k]);
                h = Hasher.mix(h, ids[k].length());
                for (int c = 0; c < ids[k].length(); c++) {
                    h = Hasher.mix(h, ids[k].charAt(c));
                }
                h = Hasher.mix(h, ready[k]);
            }
        }
        return timed.mix(h, alive);
    }

    /**
     * The state of living units, as a save keeps it: the ability table of the set (its ids ascending, which the rows
     * and the saved ability orders index into), the cooldown rows and the timed rows; empty while no unit has a
     * cooldown, a timed effect or an ability order.
     */
    public Saved save(MatchState s) {
        EntityStore alive = s.entities();
        List<TimedModifiers.Entry> entries = timed.entries(alive);
        boolean any = !entries.isEmpty() || hasOrders(s);
        for (int k = 0; k < count && !any; k++) {
            any = alive.isAlive(handles[k]);
        }
        if (!any) {
            return Saved.EMPTY;
        }
        List<String> table = new ArrayList<>();
        for (AbilityDef def : s.templates().abilities()) {
            table.add(def.id());
        }
        List<Long> cooldowns = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            int at = alive.isAlive(handles[k]) ? Collections.binarySearch(table, ids[k]) : -1;
            if (at >= 0) {
                cooldowns.add((long) handles[k]);
                cooldowns.add((long) at);
                cooldowns.add(ready[k]);
            }
        }
        List<Long> timedRows = new ArrayList<>();
        for (TimedModifiers.Entry e : entries) {
            int at = Collections.binarySearch(table, e.id());
            if (at >= 0) {
                timedRows.add((long) e.handle());
                timedRows.add((long) at);
                timedRows.add(e.expiry());
            }
        }
        return new Saved(table, cooldowns, timedRows);
    }

    /** Whether some living unit has an ability order anywhere in its queue. */
    private static boolean hasOrders(MatchState s) {
        OrderQueues orders = s.orders();
        for (int i = 0; i < s.entities().highWater(); i++) {
            if (s.entities().aliveAt(i)) {
                for (int k = 0; k < orders.size(i); k++) {
                    if (orders.order(i, k).kind() == OrderKind.ABILITY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Replaces the state with a saved one, the modifiers of the timed rows added again from {@code templates}'
     * definitions to units that are alive; a row for an ability the set lacks is dropped. Called on fresh stats.
     */
    public void restore(Saved saved, TemplateSet templates, StatService stats, EntityStore alive) {
        count = 0;
        timed.clear();
        if (saved.cooldowns().size() % 3 != 0 || saved.timed().size() % 3 != 0) {
            throw new IllegalArgumentException("abilities: rows of three numbers expected");
        }
        for (int k = 0; k < saved.cooldowns().size(); k += 3) {
            String id = id(saved, saved.cooldowns().get(k + 1));
            int handle = handle(saved.cooldowns().get(k), alive);
            if (templates.ability(id) != null) {
                start(handle, id, saved.cooldowns().get(k + 2));
            }
        }
        for (int k = 0; k < saved.timed().size(); k += 3) {
            String id = id(saved, saved.timed().get(k + 1));
            int handle = handle(saved.timed().get(k), alive);
            AbilityDef def = templates.ability(id);
            if (def != null && def.timed() != null) {
                timed.apply(
                        stats,
                        handle,
                        id,
                        AbilityEffects.source(templates.abilityIndex(id)),
                        saved.timed().get(k + 2),
                        modifiers(templates, def));
            }
        }
    }

    private static String id(Saved saved, long index) {
        if (index < 0 || index >= saved.ids().size()) {
            throw new IllegalArgumentException("abilities: no id at " + index);
        }
        return saved.ids().get((int) index);
    }

    private static int handle(long value, EntityStore alive) {
        if (value != (int) value || !alive.isAlive((int) value)) {
            throw new IllegalArgumentException("abilities: a row for " + value + ", which is no live unit");
        }
        return (int) value;
    }

    private int find(int handle, String id) {
        int lo = 0;
        int hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = Integer.compare(handles[mid], handle);
            if (c == 0) {
                c = ids[mid].compareTo(id);
            }
            if (c == 0) {
                return mid;
            }
            if (c < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -(lo + 1);
    }
}
