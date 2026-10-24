package dev.strategos.core.sim;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.ai.ComputerPlayer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Which factions the computer plays (AI-01), each a {@link ComputerPlayer} with its memory, and which of them think
 * early in the next tick (attacked, or a building lost). Hashed and saved once any is set.
 */
public final class AiSlots {
    /** One player as saved: its faction, profile, script and memory. */
    public record Saved(int faction, String profile, String script, List<Long> memory) {
        public Saved {
            memory = List.copyOf(memory);
        }
    }

    private final @Nullable ComputerPlayer[] players = new ComputerPlayer[MatchConfig.MAX_FACTIONS];
    private long wake;

    public @Nullable ComputerPlayer get(int faction) {
        return faction >= 0 && faction < players.length ? players[faction] : null;
    }

    public void set(int faction, @Nullable ComputerPlayer player) {
        players[faction] = player;
    }

    public boolean any() {
        for (ComputerPlayer p : players) {
            if (p != null) {
                return true;
            }
        }
        return false;
    }

    /** Faction {@code faction}'s player thinks in the next tick, not at its turn. */
    public void wake(int faction) {
        if (get(faction) != null) {
            wake |= 1L << faction;
        }
    }

    boolean takeWake(int faction) {
        boolean woken = (wake & (1L << faction)) != 0;
        wake &= ~(1L << faction);
        return woken;
    }

    public long wakeMask() {
        return wake;
    }

    public List<Saved> save() {
        List<Saved> out = new ArrayList<>();
        for (ComputerPlayer p : players) {
            if (p != null) {
                out.add(new Saved(
                        p.faction(), p.profile(), p.script(), p.memory().save()));
            }
        }
        return out;
    }

    public void restore(List<Saved> saved, long wakeMask) {
        for (Saved s : saved) {
            ComputerPlayer p = new ComputerPlayer(s.faction(), s.profile(), s.script());
            p.memory().restore(s.memory());
            players[s.faction()] = p;
        }
        wake = wakeMask;
    }

    long mix(long h) {
        if (!any()) {
            return h;
        }
        for (ComputerPlayer p : players) {
            if (p == null) {
                continue;
            }
            h = Hasher.mix(h, p.faction());
            h = Hasher.mix(h, p.profile().hashCode());
            h = Hasher.mix(h, p.script().hashCode());
            for (long v : p.memory().copy()) {
                h = Hasher.mix(h, v);
            }
        }
        return Hasher.mix(h, wake);
    }
}
