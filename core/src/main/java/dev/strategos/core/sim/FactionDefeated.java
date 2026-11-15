package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;

/** Faction {@code faction} lost (MATCH-01): its last civic centre fell, it resigned, or nothing of it was left ({@code why}). */
@CoreApi
public record FactionDefeated(FactionSlot faction, int why) implements MatchEvent {
    public static final int CENTRE_LOST = 0;
    public static final int RESIGNED = 1;
    public static final int CONQUERED = 2;
}
