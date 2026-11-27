package dev.strategos.core.sim.rank;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.PromotionDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.StatService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Per unit slot, the experience a unit has earned (CMB-11, fixed-point: {@link PromotionDef#SCALE} to the point) and
 * the rank it has reached. A unit that has earned nothing is 0 and 0, which hashes and saves as nothing: the store is
 * {@link #empty} (and then absent from the hash and the save) until some unit is credited. A slot is {@link #reset}
 * when its unit is created and when it leaves the match, so a handle never inherits another's rank. The rank's
 * modifiers are not saved: they follow from the template's data ({@link Ranks}).
 */
public final class ExperienceStore {
    /** What a save keeps: {@code earned} rows of (unit handle, milli-experience) and {@code ranks} rows of (unit handle, rank). */
    public record Saved(List<Long> earned, List<Long> ranks) {
        public static final Saved EMPTY = new Saved(List.of(), List.of());

        public Saved {
            earned = List.copyOf(earned);
            ranks = List.copyOf(ranks);
        }
    }

    private long[] experience = new long[0];
    private byte[] rank = new byte[0];
    private int active; // slots with experience or a rank

    public void ensureCapacity(int size) {
        if (experience.length < size) {
            int grown = Math.max(size, experience.length * 2);
            experience = Arrays.copyOf(experience, grown);
            rank = Arrays.copyOf(rank, grown);
        }
    }

    /** Whether no unit has earned anything. */
    public boolean empty() {
        return active == 0;
    }

    /** Unit {@code index}'s experience in thousandths of a point. */
    public long experience(int index) {
        return index < experience.length ? experience[index] : 0;
    }

    /** Unit {@code index}'s rank: 0 for none. */
    public int rank(int index) {
        return index < rank.length ? rank[index] : 0;
    }

    /** Sets unit {@code index}'s experience and rank. */
    void set(int index, long milli, int newRank) {
        boolean was = experience[index] != 0 || rank[index] != 0;
        experience[index] = milli;
        rank[index] = (byte) newRank;
        boolean is = milli != 0 || newRank != 0;
        active += (is ? 1 : 0) - (was ? 1 : 0);
    }

    /** Slot {@code index} starts, or ends, with nothing earned. */
    public void reset(int index) {
        if (index < experience.length && (experience[index] != 0 || rank[index] != 0)) {
            experience[index] = 0;
            rank[index] = 0;
            active--;
        }
    }

    /** The hash with every ranked or experienced unit mixed in, in slot order. */
    public long mix(long h, EntityStore alive) {
        for (int i = 0; i < alive.highWater(); i++) {
            if (alive.aliveAt(i) && (experience[i] != 0 || rank[i] != 0)) {
                h = Hasher.mix(h, alive.handleAt(i));
                h = Hasher.mix(h, experience[i]);
                h = Hasher.mix(h, rank[i]);
            }
        }
        return h;
    }

    /** The state as a save keeps it, in slot order; empty while nothing was earned. */
    public Saved save(EntityStore alive) {
        if (active == 0) {
            return Saved.EMPTY;
        }
        List<Long> earned = new ArrayList<>();
        List<Long> ranks = new ArrayList<>();
        for (int i = 0; i < alive.highWater(); i++) {
            if (!alive.aliveAt(i)) {
                continue;
            }
            if (experience[i] != 0) {
                earned.add((long) alive.handleAt(i));
                earned.add(experience[i]);
            }
            if (rank[i] != 0) {
                ranks.add((long) alive.handleAt(i));
                ranks.add((long) rank[i]);
            }
        }
        return new Saved(earned, ranks);
    }

    /** Forgets everything; a restore starts here. */
    void clear() {
        Arrays.fill(experience, 0);
        Arrays.fill(rank, (byte) 0);
        active = 0;
    }

    /** What {@link Ranks} derives from a template set, made once per set. */
    @SuppressWarnings("ReferenceEquality") // a swapped-in set is another object, whatever it equals
    void bind(TemplateSet set, StatService stats) {
        if (cachedFor != set) {
            cachedFor = set;
            valuePath = stats.path(CorePaths.PROMOTION_VALUE);
            sets = new Modifier[maxIndex(set) + 1][][];
        }
    }

    private static int maxIndex(TemplateSet set) {
        int top = -1;
        for (var t : set.all()) {
            top = Math.max(top, t.netIndex());
        }
        return top;
    }

    /** The value path of {@code Promotion/value} in the bound set, or -1. */
    int valuePath() {
        return valuePath;
    }

    /** Rank {@code r}'s modifiers of template {@code netIndex} in the bound set, made on first use. */
    Modifier[] modifiers(int netIndex, int r, PromotionDef def) {
        Modifier[][] byRank = sets[netIndex];
        if (byRank == null) {
            byRank = new Modifier[def.count() + 1][];
            sets[netIndex] = byRank;
        }
        Modifier[] made = byRank[r];
        if (made == null) {
            made = r == 0 || r > def.count()
                    ? new Modifier[0]
                    : Ranks.compile(def.ranks().get(r - 1));
            byRank[r] = made;
        }
        return made;
    }

    private @Nullable TemplateSet cachedFor;
    private int valuePath = -1;
    private Modifier[][][] sets = new Modifier[0][][]; // by net index, then rank; null until first used
}
