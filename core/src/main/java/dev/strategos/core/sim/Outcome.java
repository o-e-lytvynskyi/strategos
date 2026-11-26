package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.Winners;
import java.util.List;

/**
 * Who has lost and whether the match is over (MATCH-01): the factions that ever held a finished civic centre, those
 * defeated (their last centre fell, or they resigned), and the terminal tick and ordered surviving winners.
 * An empty winner set explicitly represents a draw. Hashed and saved once anything is set, so a match that never had a centre hashes as before.
 */
public final class Outcome {
    public static final int NONE = -1;

    private long held;
    private long defeated;
    private boolean ended;
    private int winner = NONE;
    private int winnerMask;
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

    public List<Integer> winners() {
        return Winners.fromMask(winnerMask);
    }

    public long endedAt() {
        return endedAt;
    }

    public void end(int winner, long tick) {
        end(Winners.legacy(winner), tick);
    }

    public void end(List<Integer> winners, long tick) {
        if (ended) return;
        this.winnerMask = Winners.mask(winners);
        this.ended = true;
        this.winner = Winners.first(winnerMask);
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
        h = Hasher.mix(h, endedAt);
        // Released individual outcomes keep their hash. A coalition adds an unambiguous extension.
        if (Integer.bitCount(winnerMask) > 1) {
            h = Hasher.mix(h, 0x57494E4E455253L);
            h = Hasher.mix(h, winnerMask);
        }
        return h;
    }

    /** Schema 6: held, defeated, ended, first winner, end tick, full winner mask; none while empty. */
    public List<Long> save() {
        return empty()
                ? List.of()
                : List.of(held, defeated, ended ? 1L : 0L, (long) winner, endedAt, (long) winnerMask);
    }

    public void restore(List<Long> saved) {
        if (saved.size() == 5 || saved.size() == 6) {
            held = saved.get(0);
            defeated = saved.get(1);
            ended = saved.get(2) != 0;
            winner = Math.toIntExact(saved.get(3));
            endedAt = saved.get(4);
            winnerMask = saved.size() == 6 ? Math.toIntExact(saved.get(5)) : Winners.mask(Winners.legacy(winner));
            Winners.fromMask(winnerMask);
            if (winner != Winners.first(winnerMask) || (!ended && winnerMask != 0)) {
                throw new IllegalArgumentException("outcome winner disagrees with its terminal set");
            }
        } else if (!saved.isEmpty()) {
            throw new IllegalArgumentException("invalid outcome shape");
        }
    }
}
