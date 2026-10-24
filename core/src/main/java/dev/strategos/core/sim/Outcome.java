package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import java.util.List;

/**
 * Who has lost and whether the match is over (MATCH-01): the factions that ever held a finished civic centre, those
 * defeated (their last centre fell, or they resigned), and once at most one stands, the end: its tick and the winner,
 * -1 for none. Hashed and saved once anything is set, so a match that never had a centre hashes as before.
 */
public final class Outcome {
    public static final int NONE = -1;

    private long held;
    private long defeated;
    private boolean ended;
    private int winner = NONE;
    private long endedAt;

    public boolean held(int faction) {
        return (held & (1L << faction)) != 0;
    }

    public void hold(int faction) {
        held |= 1L << faction;
    }

    public boolean defeated(int faction) {
        return (defeated & (1L << faction)) != 0;
    }

    public void defeat(int faction) {
        defeated |= 1L << faction;
    }

    public boolean ended() {
        return ended;
    }

    public int winner() {
        return winner;
    }

    public long endedAt() {
        return endedAt;
    }

    public void end(int winner, long tick) {
        this.ended = true;
        this.winner = winner;
        this.endedAt = tick;
    }

    /** Whether nothing is set: no centre ever held, nobody defeated. */
    public boolean empty() {
        return held == 0 && defeated == 0 && !ended;
    }

    public long mix(long h) {
        if (empty()) {
            return h;
        }
        h = Hasher.mix(h, held);
        h = Hasher.mix(h, defeated);
        h = Hasher.mix(h, ended ? 1 : 0);
        h = Hasher.mix(h, winner);
        return Hasher.mix(h, endedAt);
    }

    /** As saved: held, defeated, ended (1 or 0), winner, the end's tick; none while empty. */
    public List<Long> save() {
        return empty() ? List.of() : List.of(held, defeated, ended ? 1L : 0L, (long) winner, endedAt);
    }

    public void restore(List<Long> saved) {
        if (saved.size() == 5) {
            held = saved.get(0);
            defeated = saved.get(1);
            ended = saved.get(2) != 0;
            winner = saved.get(3).intValue();
            endedAt = saved.get(4);
        }
    }
}
