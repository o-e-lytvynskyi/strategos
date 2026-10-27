package dev.strategos.core.sim.stats;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.HotPath;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchBus;
import dev.strategos.core.sim.StatsChanged;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The single source of every stat (ADR-0013, 04 §8.1). A unit's value at a path is its template's, changed by its
 * faction's modifiers and its own that reach its template: per stage ascending, the replacement of highest priority
 * (then lowest source id), then the products, then the sums, each in source-id order, clamped to the path's declared
 * range ({@link CorePaths}). Modifiers never give a template a path it lacks: that stays NaN, or no token list. Values
 * are cached per template and faction and, for a unit with modifiers of its own, in its overlay; adding or removing a
 * modifier invalidates exactly the blocks it reaches and posts {@link StatsChanged}. A cached read is an array index and
 * a version compare.
 */
@HotPath
public final class StatService {
    /**
     * Canonical order: one path's entries together, then by stage, operation and source id, then by every other field,
     * so the order modifiers were added in never changes a product or a sum.
     */
    private static final Comparator<Entry> ORDER = Comparator.comparingInt(
                    (Entry e) -> e.modifier().op() == ModifierOp.TOKENS ? 1 : 0)
            .thenComparingInt(Entry::path)
            .thenComparingInt(e -> e.modifier().stage())
            .thenComparing(e -> e.modifier().op())
            .thenComparingInt(e -> e.modifier().sourceId())
            .thenComparingInt(e -> -e.modifier().priority())
            .thenComparingDouble(e -> e.modifier().value())
            .thenComparing(e -> e.modifier().tokens())
            .thenComparing(e -> e.modifier().filter())
            .thenComparingInt(e -> e.own() ? 1 : 0);

    private final int factions;
    private final IdentityStore identity;
    private final MatchBus bus;
    // from the template set, rebuilt by a swap
    private TemplateSet templates;
    private int numbers;
    private int lists;
    private long[] classes = new long[0];
    private List<List<@Nullable List<String>>> baseTokens = List.of();
    private double[] min = new double[0];
    private double[] max = new double[0];
    private static final String IDENTITY_CLASSES = "Identity/classes";

    /** The class token of units that gather (UNIT-04). */
    public static final String GATHERER = "Gatherer";

    /** The class token of units that build and repair (UNIT-04). */
    public static final String BUILDER = "Builder";

    private int walkSpeed;
    private int health;
    private int[] resistance = new int[3];
    private int[] melee = new int[3];
    private int reach;
    private int prepare;
    private int repeat;
    private int[] ranged = new int[4];
    private int rangedRange;
    private int rangedPrepare;
    private int rangedRepeat;
    private int rangedSpeed;
    private int rangedSpread;
    private int vision;
    private int flee;
    private int classesPath;
    private int turnRate;
    private StatBlock[] blocks = new StatBlock[0];
    private final long[] versions;
    private final List<List<Entry>> factionModifiers = new ArrayList<>();
    private @Nullable Personal[] personal = new Personal[0];
    private final ArrayList<Entry> scratch = new ArrayList<>();
    private long evaluations;

    /** A modifier as the service keeps it: its path's id in this set (-1 when no template has it) and its filter. */
    private record Entry(Modifier modifier, int path, ClassFilter filter, boolean own) {}

    /** A unit's own modifiers and the overlay that evaluates them over its faction's. */
    private static final class Personal {
        final List<Entry> modifiers = new ArrayList<>();
        StatBlock block;
        long version;

        Personal(int numbers, int lists) {
            block = new StatBlock(numbers, lists);
        }
    }

    public StatService(TemplateSet templates, int factions, IdentityStore identity, MatchBus bus) {
        this.templates = templates;
        this.factions = factions;
        this.identity = identity;
        this.bus = bus;
        build(templates);
        this.versions = new long[factions];
        for (int f = 0; f < factions; f++) {
            factionModifiers.add(new ArrayList<>());
        }
    }

    /**
     * A new template set (DATA-03's {@code ContentSwap}): the values come from it, each modifier finds its path in it
     * again, and every cached block and overlay is evaluated afresh on its next read. The modifiers themselves stay.
     */
    public void swap(TemplateSet set) {
        build(set);
        for (List<Entry> list : factionModifiers) {
            reentry(list);
        }
        for (Personal own : personal) {
            if (own != null) {
                reentry(own.modifiers);
                own.block = new StatBlock(numbers, lists);
                own.version++;
            }
        }
        for (int f = 0; f < factions; f++) {
            versions[f]++;
        }
    }

    /** What a set gives: its paths, each template's classes and token lists, and a fresh block per template and faction. */
    private void build(TemplateSet set) {
        this.templates = set;
        this.numbers = set.valuePaths().size();
        this.lists = set.tokenPaths().size();
        int count = set.indexCount();
        this.classes = new long[count];
        this.baseTokens = new ArrayList<>(count);
        for (int t = 0; t < count; t++) {
            classes[t] = set.has(t) ? set.tokens().bits(set.byIndex(t).classes()) : 0;
            List<@Nullable List<String>> tokens = new ArrayList<>(lists);
            for (int k = 0; k < lists; k++) {
                String text = set.has(t) ? set.text(t, k) : null;
                tokens.add(text == null ? null : TokenEdit.split(text));
            }
            baseTokens.add(tokens);
        }
        this.min = new double[numbers];
        this.max = new double[numbers];
        for (ValuePath p : set.valuePaths().all()) {
            min[p.id()] = p.min();
            max[p.id()] = p.max();
        }
        this.walkSpeed = path(CorePaths.WALK_SPEED);
        this.turnRate = path(CorePaths.TURN_RATE);
        this.health = path(CorePaths.HEALTH);
        this.resistance =
                new int[] {path(CorePaths.RESIST_HACK), path(CorePaths.RESIST_PIERCE), path(CorePaths.RESIST_CRUSH)};
        this.melee = new int[] {path(CorePaths.MELEE_HACK), path(CorePaths.MELEE_PIERCE), path(CorePaths.MELEE_CRUSH)};
        this.reach = path(CorePaths.MELEE_REACH);
        this.prepare = path(CorePaths.MELEE_PREPARE);
        this.repeat = path(CorePaths.MELEE_REPEAT);
        this.ranged = new int[] {
            path(CorePaths.RANGED_HACK),
            path(CorePaths.RANGED_PIERCE),
            path(CorePaths.RANGED_CRUSH),
            path(CorePaths.RANGED_FIRE)
        };
        this.rangedRange = path(CorePaths.RANGED_RANGE);
        this.rangedPrepare = path(CorePaths.RANGED_PREPARE);
        this.rangedRepeat = path(CorePaths.RANGED_REPEAT);
        this.rangedSpeed = path(CorePaths.RANGED_SPEED);
        this.rangedSpread = path(CorePaths.RANGED_SPREAD);
        this.vision = path(CorePaths.VISION);
        this.flee = path(CorePaths.FLEE);
        this.classesPath = tokenPath(IDENTITY_CLASSES);
        this.blocks = new StatBlock[count * factions];
        for (int k = 0; k < blocks.length; k++) {
            blocks[k] = new StatBlock(numbers, lists);
        }
    }

    /** Each entry's path and filter found again in the current set, in canonical order. */
    private void reentry(List<Entry> list) {
        list.replaceAll(e -> entry(e.modifier(), e.own()));
        list.sort(ORDER);
    }

    /** A value path's id in this set, or -1. */
    public int path(String path) {
        ValuePath p = templates.valuePaths().byPath(path);
        return p == null ? -1 : p.id();
    }

    /** A token path's id in this set, or -1. */
    public int tokenPath(String path) {
        ValuePath p = templates.tokenPaths().byPath(path);
        return p == null ? -1 : p.id();
    }

    /** Unit {@code index}'s walk speed in blocks per tick. */
    public double walkSpeed(int index) {
        return read(index, walkSpeed);
    }

    /** Unit {@code index}'s turn rate in degrees per tick. */
    public double turnRate(int index) {
        return read(index, turnRate);
    }

    /** Unit {@code index}'s most hit points (CMB-01). */
    public double maxHealth(int index) {
        return read(index, health);
    }

    /** Unit {@code index}'s resistance level against damage type {@code type}: 0 hack, 1 pierce, 2 crush. */
    public double resistance(int index, int type) {
        return read(index, resistance[type]);
    }

    /** Unit {@code index}'s melee damage of type {@code type} (0 hack, 1 pierce, 2 crush) per hit (CMB-02). */
    public double meleeDamage(int index, int type) {
        return read(index, melee[type]);
    }

    /** Whether unit {@code index} has a melee attack: some damage per hit. */
    public boolean canMelee(int index) {
        return meleeDamage(index, 0) > 0 || meleeDamage(index, 1) > 0 || meleeDamage(index, 2) > 0;
    }

    /** Ranged damage of type 0 hack, 1 pierce, 2 crush or 3 fire, after modifiers. */
    public double rangedDamage(int index, int type) {
        double value = read(index, ranged[type]);
        return Double.isNaN(value) ? 0 : value;
    }

    /** Whether the unit has a ranged weapon with positive range and damage. */
    public boolean canRanged(int index) {
        return rangedRange(index) > 0
                && (rangedDamage(index, 0) > 0
                        || rangedDamage(index, 1) > 0
                        || rangedDamage(index, 2) > 0
                        || rangedDamage(index, 3) > 0);
    }

    /** Whether the unit can attack by either supported weapon. */
    public boolean canAttack(int index) {
        return canMelee(index) || canRanged(index);
    }

    /** Ranged reach in blocks. Zero disables a ranged weapon. */
    public double rangedRange(int index) {
        double value = read(index, rangedRange);
        return Double.isNaN(value) ? 0 : value;
    }

    /** Ticks from starting a ranged attack to its first shot. */
    public int rangedPrepare(int index) {
        return (int) Math.round(read(index, rangedPrepare));
    }

    /** Ticks between ranged shots. */
    public int rangedRepeat(int index) {
        return (int) Math.max(1, Math.round(read(index, rangedRepeat)));
    }

    /** Projectile speed in blocks per tick. */
    public double rangedSpeed(int index) {
        double value = read(index, rangedSpeed);
        return Double.isNaN(value) ? CorePaths.min(CorePaths.RANGED_SPEED) : value;
    }

    /** Horizontal shot spread, in percent of distance. */
    public double rangedSpread(int index) {
        double value = read(index, rangedSpread);
        return Double.isNaN(value) ? 0 : value;
    }

    /** Unit {@code index}'s melee reach, in blocks between its edge and its target's. */
    public double meleeReach(int index) {
        return read(index, reach);
    }

    /** The ticks from the start of unit {@code index}'s attack to its first hit. */
    public int meleePrepare(int index) {
        return (int) Math.round(read(index, prepare));
    }

    /** The ticks between unit {@code index}'s hits. */
    public int meleeRepeat(int index) {
        return (int) Math.max(1, Math.round(read(index, repeat)));
    }

    /** How far unit {@code index} notices enemies, in blocks. */
    public double vision(int index) {
        return read(index, vision);
    }

    /** How far unit {@code index} runs from a blow when it cannot hit back, in blocks; 0 for none (UNIT-04). */
    public double fleeDistance(int index) {
        return read(index, flee);
    }

    /** Whether unit {@code index} gathers: its classes hold {@value #GATHERER} (D-02, UNIT-04). */
    public boolean canGather(int index) {
        return classes(index).contains(GATHERER);
    }

    /** Whether unit {@code index} builds and repairs: its classes hold {@value #BUILDER} (D-02, UNIT-04). */
    public boolean canBuild(int index) {
        return classes(index).contains(BUILDER);
    }

    /** Unit {@code index}'s class tokens, as modifiers leave them; empty for a template without classes. */
    public List<String> classes(int index) {
        List<String> tokens = classesPath < 0 ? null : tokens(index, classesPath);
        return tokens == null ? List.of() : tokens;
    }

    /** Unit {@code index}'s value at value path {@code path}. */
    public double read(int index, int path) {
        return block(index).values[path];
    }

    /** Unit {@code index}'s token list at token path {@code path}, or null when its template has none. */
    public @Nullable List<String> tokens(int index, int path) {
        return block(index).tokens.get(path);
    }

    /** The value a unit of {@code template} in {@code faction} without modifiers of its own has at {@code path}. */
    public double blockValue(int template, int faction, int path) {
        return block(template, faction).values[path];
    }

    public @Nullable List<String> blockTokens(int template, int faction, int path) {
        return block(template, faction).tokens.get(path);
    }

    /** How unit {@code index}'s value at {@code path} comes about; null for a path the set lacks. */
    public @Nullable StatBreakdown breakdown(int index, String path) {
        StatBlock block = block(index);
        return breakdown(identity.templateIndex(index), identity.faction(index), own(index), path, block);
    }

    public @Nullable StatBreakdown blockBreakdown(int template, int faction, String path) {
        StatBlock block = block(template, faction);
        return breakdown(template, faction, null, path, block);
    }

    /** A modifier for every unit of {@code faction} its filter matches. */
    public void add(FactionSlot faction, Modifier modifier) {
        int f = faction(faction);
        Entry e = entry(modifier, false);
        insert(factionModifiers.get(f), e);
        factionChanged(f, List.of(e));
    }

    /** Removes every modifier {@code sourceId} gave {@code faction}. */
    public void remove(FactionSlot faction, int sourceId) {
        int f = faction(faction);
        factionChanged(f, take(factionModifiers.get(f), sourceId));
    }

    /** A modifier of unit {@code handle}'s own, such as a rank or a status. */
    public void addPersonal(int handle, Modifier modifier) {
        int i = Handle.index(handle);
        if (i >= personal.length) {
            personal = Arrays.copyOf(personal, Math.max(i + 1, personal.length * 2));
        }
        Personal own = personal[i];
        if (own == null) {
            own = new Personal(numbers, lists);
            personal[i] = own;
        }
        Entry e = entry(modifier, true);
        insert(own.modifiers, e);
        personalChanged(handle, own, List.of(e));
    }

    /** Removes every modifier {@code sourceId} gave unit {@code handle}. */
    public void removePersonal(int handle, int sourceId) {
        int i = Handle.index(handle);
        Personal own = own(i);
        if (own == null) {
            return;
        }
        List<Entry> removed = take(own.modifiers, sourceId);
        if (own.modifiers.isEmpty()) {
            personal[i] = null; // back to its template's block
        }
        personalChanged(handle, own, removed);
    }

    /** A unit created in slot {@code index} starts without modifiers of its own. */
    public void clearPersonal(int index) {
        if (index < personal.length) {
            personal[index] = null;
        }
    }

    /** How many blocks and overlays were evaluated since the match started. */
    public long evaluations() {
        return evaluations;
    }

    private StatBlock block(int index) {
        int f = identity.faction(index);
        Personal own = own(index);
        if (own == null) {
            return block(identity.templateIndex(index), f);
        }
        StatBlock b = own.block;
        if (b.version != versions[f] || b.personal != own.version) {
            evaluate(b, identity.templateIndex(index), f, own);
            b.version = versions[f];
            b.personal = own.version;
        }
        return b;
    }

    private StatBlock block(int template, int faction) {
        StatBlock b = blocks[template * factions + faction];
        if (b.version != versions[faction]) {
            evaluate(b, template, faction, null);
            b.version = versions[faction];
        }
        return b;
    }

    private @Nullable Personal own(int index) {
        return index < personal.length ? personal[index] : null;
    }

    private void evaluate(StatBlock block, int t, int f, @Nullable Personal own) {
        evaluations++;
        double[] v = block.values;
        for (int k = 0; k < numbers; k++) {
            v[k] = templates.value(t, k);
        }
        List<@Nullable List<String>> base = baseTokens.get(t);
        for (int k = 0; k < lists; k++) {
            block.tokens.set(k, base.get(k));
        }
        reaching(t, f, own);
        int n = scratch.size();
        int a = 0;
        while (a < n) {
            int b = run(a);
            Entry first = scratch.get(a);
            if (first.modifier().op() == ModifierOp.TOKENS) {
                block.tokens.set(first.path(), edit(base.get(first.path()), a, b));
            } else {
                v[first.path()] = number(v[first.path()], first.path(), a, b, null);
            }
            a = b;
        }
    }

    /** Fills the scratch with the modifiers that reach template {@code t}, in canonical order. */
    private void reaching(int t, int f, @Nullable Personal own) {
        scratch.clear();
        collect(factionModifiers.get(f), t);
        if (own != null) {
            collect(own.modifiers, t);
        }
        scratch.sort(ORDER);
    }

    private void collect(List<Entry> list, int t) {
        for (int k = 0; k < list.size(); k++) {
            Entry e = list.get(k);
            if (reaches(e, t)) {
                scratch.add(e);
            }
        }
    }

    /** Whether {@code e} changes a value of template {@code t}: its filter matches and the template has its path. */
    private boolean reaches(Entry e, int t) {
        if (e.path() < 0 || !e.filter().matches(classes[t])) {
            return false;
        }
        return e.modifier().op() == ModifierOp.TOKENS
                ? baseTokens.get(t).get(e.path()) != null
                : !Double.isNaN(templates.value(t, e.path()));
    }

    /** The end of the scratch's run of one path from {@code a}. */
    private int run(int a) {
        Entry first = scratch.get(a);
        boolean tokens = first.modifier().op() == ModifierOp.TOKENS;
        int b = a + 1;
        while (b < scratch.size()
                && scratch.get(b).path() == first.path()
                && (scratch.get(b).modifier().op() == ModifierOp.TOKENS) == tokens) {
            b++;
        }
        return b;
    }

    /** The scratch's run {@code [from, to)} of one path applied to {@code base}; {@code taken} collects its part. */
    private double number(double base, int path, int from, int to, @Nullable List<Modifier> taken) {
        double v = base;
        int i = from;
        while (i < to) {
            int stage = scratch.get(i).modifier().stage();
            int end = i;
            while (end < to && scratch.get(end).modifier().stage() == stage) {
                end++;
            }
            Modifier replacement = null;
            for (int k = i; k < end; k++) {
                Modifier m = scratch.get(k).modifier();
                if (m.op() == ModifierOp.REPLACE && (replacement == null || wins(m, replacement))) {
                    replacement = m;
                }
            }
            if (replacement != null) {
                v = replacement.value();
                if (taken != null) {
                    taken.add(replacement);
                }
            }
            for (int k = i; k < end; k++) {
                Modifier m = scratch.get(k).modifier();
                if (m.op() == ModifierOp.MUL) {
                    v *= m.value();
                    if (taken != null) {
                        taken.add(m);
                    }
                }
            }
            for (int k = i; k < end; k++) {
                Modifier m = scratch.get(k).modifier();
                if (m.op() == ModifierOp.ADD) {
                    v += m.value();
                    if (taken != null) {
                        taken.add(m);
                    }
                }
            }
            v = Math.max(min[path], Math.min(max[path], v));
            i = end;
        }
        return v;
    }

    /** The higher priority wins, then the lower source id, then the lower value. */
    private static boolean wins(Modifier m, Modifier best) {
        if (m.priority() != best.priority()) {
            return m.priority() > best.priority();
        }
        if (m.sourceId() != best.sourceId()) {
            return m.sourceId() < best.sourceId();
        }
        return Double.compare(m.value(), best.value()) < 0;
    }

    private @Nullable List<String> edit(@Nullable List<String> base, int from, int to) {
        if (base == null) {
            return null;
        }
        List<String> list = new ArrayList<>(base);
        for (int k = from; k < to; k++) {
            TokenEdit.apply(list, scratch.get(k).modifier().tokens());
        }
        return List.copyOf(list);
    }

    private @Nullable StatBreakdown breakdown(int t, int f, @Nullable Personal own, String path, StatBlock block) {
        ValuePath p = templates.valuePaths().byPath(path);
        if (p == null) {
            return null;
        }
        double base = templates.value(t, p.id());
        List<Modifier> taken = new ArrayList<>();
        reaching(t, f, own);
        int a = 0;
        while (a < scratch.size()) {
            int b = run(a);
            Entry first = scratch.get(a);
            if (first.path() == p.id() && first.modifier().op() != ModifierOp.TOKENS) {
                number(base, p.id(), a, b, taken);
            }
            a = b;
        }
        return new StatBreakdown(path, base, taken, block.values[p.id()]);
    }

    private Entry entry(Modifier m, boolean own) {
        int id = m.op() == ModifierOp.TOKENS ? tokenPath(m.path()) : path(m.path());
        return new Entry(m, id, ClassFilter.compile(m.filter(), templates.tokens()), own);
    }

    private static void insert(List<Entry> list, Entry e) {
        list.add(e);
        list.sort(ORDER);
    }

    private static List<Entry> take(List<Entry> list, int sourceId) {
        List<Entry> taken = new ArrayList<>();
        for (int k = list.size() - 1; k >= 0; k--) {
            if (list.get(k).modifier().sourceId() == sourceId) {
                taken.add(list.remove(k));
            }
        }
        return taken;
    }

    private int faction(FactionSlot faction) {
        if (faction.value() >= factions) {
            throw new IllegalArgumentException("faction " + faction.value() + " outside the match's " + factions);
        }
        return faction.value();
    }

    /**
     * A faction's modifiers changed: its version moves on, and every block and overlay of it that none of {@code
     * changed} reaches keeps its values, stamped fresh; the rest are evaluated again on their next read.
     */
    private void factionChanged(int f, List<Entry> changed) {
        BitSet reached = new BitSet(classes.length);
        TreeSet<String> paths = new TreeSet<>();
        for (int k = 0; k < changed.size(); k++) {
            Entry e = changed.get(k);
            for (int t = 0; t < classes.length; t++) {
                if (reaches(e, t)) {
                    reached.set(t);
                    paths.add(e.modifier().path());
                }
            }
        }
        long old = versions[f];
        versions[f] = old + 1;
        for (int t = 0; t < classes.length; t++) {
            StatBlock b = blocks[t * factions + f];
            if (b.version == old && !reached.get(t)) {
                b.version = old + 1;
            }
        }
        for (int i = 0; i < personal.length; i++) {
            Personal own = personal[i];
            if (own != null
                    && own.block.version == old
                    && identity.faction(i) == f
                    && !reached.get(identity.templateIndex(i))) {
                own.block.version = old + 1;
            }
        }
        if (!paths.isEmpty()) {
            bus.publish(new StatsChanged(StatScope.FACTION, f, new ArrayList<>(paths)));
        }
    }

    private void personalChanged(int handle, Personal own, List<Entry> changed) {
        int t = identity.templateIndex(Handle.index(handle));
        TreeSet<String> paths = new TreeSet<>();
        for (int k = 0; k < changed.size(); k++) {
            Entry e = changed.get(k);
            if (reaches(e, t)) {
                paths.add(e.modifier().path());
            }
        }
        if (!paths.isEmpty()) {
            own.version++;
            bus.publish(new StatsChanged(StatScope.UNIT, handle, new ArrayList<>(paths)));
        }
    }
}
