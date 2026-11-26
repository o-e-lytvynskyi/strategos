package dev.strategos.mod.server.world;

import dev.strategos.core.sim.DayClock;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The sky of a match with day and night (MATCH-16). World time belongs to the level and vanilla sends it to every player
 * in it, so a match that is alone in its level (D-20: one match per world) sets it itself: {@link Mode#WORLD_TIME}, the
 * level's day time follows the match's {@link DayClock} and vanilla's own synchronisation carries it to the players.
 * With more than one match in the level the night of one must not leak into another: {@link Mode#TINT}, the level's
 * time is left alone and the client draws the match's darkness over the world from the replicated clock.
 */
public final class MatchSky {
    /** How a match with the rule on shows its night. */
    public enum Mode {
        /** The rule is off: nothing is shown. */
        NONE,
        /** The level's time follows the match: it is alone in its level. */
        WORLD_TIME,
        /** A client-side darkening: the level holds other matches, whose sky is not this one's. */
        TINT
    }

    /** How often the level's time is set, in match ticks: once a second; vanilla synchronises it every 20 server ticks. */
    static final int SET_EVERY = 20;

    /** The levels whose time it drives, so it hands a level back to noon once, when its match stops needing it. */
    private final Set<ResourceKey<Level>> driven = new HashSet<>();

    /** The mode for a match whose rule is {@code ruleOn}, with {@code activeInLevel} matches running in its level. */
    public static Mode mode(boolean ruleOn, int activeInLevel) {
        if (!ruleOn) {
            return Mode.NONE;
        }
        return activeInLevel <= 1 ? Mode.WORLD_TIME : Mode.TINT;
    }

    /** The mode of {@code rt} now, among the matches of {@code matches}. */
    public static Mode of(MatchRuntime rt, MatchRegistry matches) {
        MatchCore core = rt.core();
        if (core == null || !core.queries().config().rules().play().dayNight()) {
            return Mode.NONE;
        }
        int active = 0;
        for (MatchRuntime other : matches.all()) {
            if (isActive(other) && other.space().level().equals(rt.space().level())) {
                active++;
            }
        }
        return mode(true, active);
    }

    private static boolean isActive(MatchRuntime rt) {
        return rt.lifecycle() == MatchLifecycle.RUNNING || rt.lifecycle() == MatchLifecycle.PAUSED;
    }

    /**
     * After {@code level}'s matches stepped: when exactly one runs there and its rule is on, the level's day time is set
     * from its clock (every {@link #SET_EVERY} ticks, and at once when the level was not yet driven); when none does any
     * more, an arena level this class drove goes back to noon, its own time.
     */
    public void drive(ServerLevel level, List<MatchRuntime> active) {
        MatchRuntime owner = active.size() == 1 ? active.get(0) : null;
        MatchCore core = owner == null ? null : owner.core();
        if (core != null && core.queries().config().rules().play().dayNight()) {
            long tick = core.queries().tick();
            if (driven.add(level.dimension()) || tick % SET_EVERY == 0) {
                level.setDayTime(DayClock.timeOfDay(tick));
            }
        } else if (driven.remove(level.dimension()) && ArenaWorld.isArena(level.getServer())) {
            level.setDayTime(ArenaWorld.NOON); // an arena keeps noon; another world's time is its own, left where it is
        }
    }
}
