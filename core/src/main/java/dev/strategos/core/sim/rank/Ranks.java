package dev.strategos.core.sim.rank;

import dev.strategos.core.content.PromotionDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitRanked;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.ModifierOp;
import dev.strategos.core.sim.stats.StatService;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The rank rules (CMB-11, M10 spec §2.8). A unit earns {@code dealt × the target's Promotion/value ÷ the target's most
 * hit points} for each hit it deals to an enemy, so killing a unit is worth its whole value, however many hit it
 * (the last hit's share is the hit points that were left). When the total reaches the next threshold of the unit's
 * template the rank rises, and the rank's modifier set is given to the unit as personal modifiers under {@link
 * #SOURCE}, taking the previous rank's off: ranks REPLACE each other, they never compound, so a rank's set is the
 * whole of what the rank gives. The unit's most hit points may change with it, and its hit-point ratio is kept.
 * Allocation-free per hit; a rise allocates, a few times in a unit's life.
 */
public final class Ranks {
    /** The personal modifier source of ranks (M10 spec §0.3); an ability's timed modifiers take {@code MIN_VALUE + 1 + index}. */
    public static final int SOURCE = Integer.MIN_VALUE;

    private Ranks() {}

    /**
     * Credits {@code attacker} with what its hit of {@code dealt} on unit {@code target} (most hit points {@code max})
     * earns. Nothing for a building or a gone attacker, for a hit on a friend or on Gaia, an attacker whose template has
     * no ranks or a target worth nothing.
     */
    public static void credit(MatchState s, int attacker, int target, double dealt, double max) {
        credit(s, attacker, target, dealt, max, null);
    }

    /**
     * As {@link #credit(MatchState, int, int, double, double)}, with {@code dying} the bitset (by unit index) of the
     * units whose hit points reached 0 this tick, so "a dying attacker earns nothing" reads no stat; {@code null} reads
     * the attacker's hit points instead.
     */
    public static void credit(
            MatchState s, int attacker, int target, double dealt, double max, long @Nullable [] dying) {
        TemplateSet set = s.templates();
        if (!set.hasPromotions() || attacker == Handle.NONE || !(dealt > 0) || !(max > 0)) {
            return;
        }
        if (BuildingStore.isBuilding(attacker)) {
            return;
        }
        int a = Handle.index(attacker);
        long[] thresholds = set.promotionThresholds(s.identity().templateIndex(a));
        if (thresholds == null || !s.entities().isAlive(attacker)) {
            return;
        }
        ExperienceStore store = s.play().experience();
        store.bind(set, s.stats());
        int path = store.valuePath();
        int t = Handle.index(target);
        if (path < 0
                || !s.allies()
                        .enemies(
                                s.identity().faction(a),
                                s.identity().faction(t),
                                s.config().factions())) {
            return;
        }
        StatService stats = s.stats();
        double value = stats.read(t, path); // modifiers may move Promotion/value, so it is read, not the template's
        if (!(value > 0)) {
            return;
        }
        long gain = Math.round(dealt * value / max * PromotionDef.SCALE);
        if (gain <= 0 || (dying != null ? isSet(dying, a) : s.health().damage(a) >= stats.maxHealth(a))) {
            return; // (a unit dying this tick only leaves at the death pass, and the dead earn nothing)
        }
        long total = store.experience(a) + gain;
        int rank = store.rank(a);
        int was = rank;
        while (rank < thresholds.length && total >= thresholds[rank]) {
            rank++;
        }
        store.set(a, total, rank);
        if (rank != was) {
            apply(s, attacker, rank, true);
            s.outputs().add(new UnitRanked(attacker, Math.min(rank, thresholds.length)));
        }
    }

    private static boolean isSet(long[] bits, int index) {
        int word = index >>> 6;
        return word < bits.length && (bits[word] & (1L << index)) != 0;
    }

    /**
     * The rank unit {@code unit} (an index) shows and applies: the earned rank, at most the rank count of its template's
     * list (0 without ranks). The earned rank itself is state and stays as earned, as a content swap or a restore may
     * leave it above a shorter list.
     */
    public static int effectiveRank(MatchState s, int unit) {
        int rank = s.play().experience().rank(unit);
        if (rank == 0) {
            return 0;
        }
        PromotionDef def = s.templates().promotion(s.identity().templateIndex(unit));
        return def == null ? 0 : Math.min(rank, def.count());
    }

    /**
     * Gives unit {@code handle} rank {@code rank}'s modifiers in place of what the rank before it gave. With {@code
     * keepRatio} its damage is scaled by the change of its most hit points, so a unit at half health stays at half; a
     * restore passes false, for it brings the damage back as it was saved.
     */
    public static void apply(MatchState s, int handle, int rank, boolean keepRatio) {
        int i = Handle.index(handle);
        StatService stats = s.stats();
        TemplateSet set = s.templates();
        int template = s.identity().templateIndex(i);
        PromotionDef def = set.promotion(template);
        double before = stats.maxHealth(i);
        stats.removePersonal(handle, SOURCE);
        if (def == null) {
            return;
        }
        ExperienceStore store = s.play().experience();
        store.bind(set, stats);
        for (Modifier m : store.modifiers(template, rank, def)) {
            stats.addPersonal(handle, m);
        }
        double after = stats.maxHealth(i);
        if (keepRatio && before > 0 && after != before) {
            double damage = s.health().damage(i);
            double scaled = damage * after / before;
            // never across the lethal line: a unit about to die stays lethal (it is in this tick's dying set), a live
            // one stays below the new most
            s.health()
                    .setDamage(i, damage >= before ? Math.max(after, scaled) : Math.min(scaled, Math.nextDown(after)));
        }
    }

    /**
     * After a content swap (as for the civilisations' bonuses): every ranked unit gets its rank's set again from the
     * new template set, the old set's modifiers coming off. A rank the new list is too short for keeps the highest
     * set the list has (a unit never loses what its template still offers); a template that no longer has ranks gives
     * none. The rank and the experience themselves stay as earned.
     */
    public static void reapply(MatchState s) {
        ExperienceStore store = s.play().experience();
        if (store.empty()) {
            return;
        }
        EntityStore alive = s.entities();
        for (int i = 0; i < alive.highWater(); i++) {
            int rank = alive.aliveAt(i) ? store.rank(i) : 0;
            if (rank > 0) {
                PromotionDef def = s.templates().promotion(s.identity().templateIndex(i));
                apply(s, alive.handleAt(i), def == null ? 0 : Math.min(rank, def.count()), true);
            }
        }
    }

    /** The modifiers of one rank's data, all under {@link #SOURCE}. */
    static Modifier[] compile(List<PromotionDef.Mod> mods) {
        Modifier[] out = new Modifier[mods.size()];
        for (int k = 0; k < out.length; k++) {
            PromotionDef.Mod m = mods.get(k);
            ModifierOp op = switch (m.op()) {
                case "mul" -> ModifierOp.MUL;
                case "replace" -> ModifierOp.REPLACE;
                default -> ModifierOp.ADD;
            };
            out[k] = new Modifier(m.path(), op, m.value(), "", "", 0, 0, SOURCE);
        }
        return out;
    }

    /**
     * Replaces the store with a saved one and gives each ranked unit its rank's modifiers again; a row for a unit that
     * is not alive, or a rank below 1 or experience below 0, refuses the restore; a rank the set's list is too short for stays as earned and gets the highest set, as after a swap. Called on fresh stats.
     */
    public static void restore(MatchState s, ExperienceStore.Saved saved) {
        ExperienceStore store = s.play().experience();
        EntityStore alive = s.entities();
        store.clear();
        if (saved.earned().size() % 2 != 0 || saved.ranks().size() % 2 != 0) {
            throw new IllegalArgumentException("experience: rows of two numbers expected");
        }
        for (int k = 0; k < saved.earned().size(); k += 2) {
            int i = unit(saved.earned().get(k), alive);
            long experience = saved.earned().get(k + 1);
            if (experience < 0) {
                throw new IllegalArgumentException("experience: " + experience + " is no experience");
            }
            store.set(i, experience, store.rank(i));
        }
        for (int k = 0; k < saved.ranks().size(); k += 2) {
            int i = unit(saved.ranks().get(k), alive);
            long rank = saved.ranks().get(k + 1);
            if (rank < 1 || rank > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("experience: rank " + rank + " is no rank");
            }
            PromotionDef def = s.templates().promotion(s.identity().templateIndex(i));
            store.set(i, store.experience(i), (int) rank); // as earned, as after a swap
            apply(
                    s,
                    alive.handleAt(i),
                    def == null ? 0 : Math.min((int) rank, def.count()),
                    false); // as {@link #reapply}
        }
    }

    private static int unit(long handle, EntityStore alive) {
        if (handle != (int) handle || !alive.isAlive((int) handle)) {
            throw new IllegalArgumentException("experience: a row for " + handle + ", which is no live unit");
        }
        return Handle.index((int) handle);
    }
}
