package dev.strategos.mod.server.fog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The bodies each player is paired with (NET-01): vanilla's tracking reports a pairing through {@code
 * startSeenByPlayer} and its end through {@code stopSeenByPlayer} (Entity.java:3085, 3091), which unit bodies forward
 * here by match and handle. Replication (NET-08) reads it; tests assert pairings with it.
 */
public final class InterestSets {
    private final Map<UUID, TreeSet<Long>> paired = new HashMap<>();

    public void start(UUID player, int match, int handle) {
        paired.computeIfAbsent(player, k -> new TreeSet<>()).add(key(match, handle));
    }

    public void stop(UUID player, int match, int handle) {
        TreeSet<Long> set = paired.get(player);
        if (set != null) {
            set.remove(key(match, handle));
        }
    }

    /** The handles of {@code match}'s bodies {@code player} is paired with, in order. */
    public List<Integer> handles(UUID player, int match) {
        TreeSet<Long> set = paired.get(player);
        if (set == null) {
            return List.of();
        }
        long first = (long) match << 32; // a handle's 32 bits, unsigned: a late generation's handle is negative
        return set.subSet(first, true, first | 0xFFFF_FFFFL, true).stream()
                .map(k -> (int) (long) k)
                .toList();
    }

    /** A player who left is paired with nothing. */
    public void forget(UUID player) {
        paired.remove(player);
    }

    private static long key(int match, int handle) {
        return ((long) match << 32) | (handle & 0xFFFF_FFFFL);
    }
}
