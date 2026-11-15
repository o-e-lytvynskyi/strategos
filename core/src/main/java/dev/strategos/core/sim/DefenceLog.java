package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * What a computer player is told about its losses (AI-04), kept by {@link Alerts} for factions a computer plays: the
 * blows on its units and buildings of the last {@value Alerts#BLOW_KEEP} ticks, and its destroyed buildings until its
 * rebuilder has handled them.
 */
@CoreApi
public final class DefenceLog {
    private DefenceLog() {}

    /** A blow on own unit or building {@code handle} at {@code tick}. */
    public record Blow(int handle, long tick) {}

    /** A building of template net index {@code template}, its corner at ({@code x}, {@code z}) turned {@code rotation}, destroyed at {@code tick}. */
    public record Loss(int template, int x, int z, int rotation, long tick) {}
}
