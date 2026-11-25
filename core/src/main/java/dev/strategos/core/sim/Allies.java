package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Who is a friend (M9 spec §2.1): the one relation question M9's systems ask — capture, garrisons, gates, territory
 * colours — instead of comparing faction numbers. Today a faction is allied only with itself; Gaia (the faction after
 * the players') is allied with nobody, itself included.
 *
 * <p>AFTER-M7: an adapter over MATCH-06's {@code DiplomacyView.allied(FactionSlot, FactionSlot)} replaces {@link
 * #sameFaction} in {@link MatchState#allies()}; nothing else in M9 changes. Its relation changes must call {@code gates().touch()}: {@code GateLinks.Pass}
 * caches per faction with these answers baked in.
 */
@CoreApi
@FunctionalInterface
public interface Allies {
    /** True when {@code a} and {@code b} are the same player faction, until MATCH-06's teams exist. */
    static Allies sameFaction(int players) {
        return (a, b) -> a == b && a >= 0 && a < players;
    }

    /** Whether two factions are allied; symmetric; false whenever either is Gaia or outside the match. */
    boolean allied(int a, int b);

    /** Two player factions that are not allied. */
    default boolean enemies(int a, int b, int players) {
        return a >= 0 && a < players && b >= 0 && b < players && !allied(a, b);
    }
}
