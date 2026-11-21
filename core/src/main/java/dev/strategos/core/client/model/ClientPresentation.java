package dev.strategos.core.client.model;

import dev.strategos.core.client.look.FactionPalette;
import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.protocol.MatchPresentation;
import org.jspecify.annotations.Nullable;

/** Public recipient context; independent from the core clock so paused roster changes remain ordered. */
public final class ClientPresentation {
    private static final FactionPalette LEGACY = new FactionPalette(FactionPresentation.legacy(16));
    private int match;
    private int factions;
    private @Nullable MatchPresentation view;
    private FactionPalette palette = LEGACY;

    public void joined(int matchId, int factionCount) {
        if (matchId <= 0 || factionCount < 1 || factionCount > 16)
            throw new IllegalArgumentException("invalid joined public context");
        clear();
        match = matchId;
        factions = factionCount;
    }

    public void clear() {
        match = 0;
        factions = 0;
        view = null;
        palette = LEGACY;
    }

    public boolean accept(MatchPresentation next) {
        if (match == 0 || next.matchId() != match || next.factions().styles().size() != factions) return false;
        MatchPresentation previous = view;
        if (previous != null && next.revision() <= previous.revision()) return false;
        view = next;
        palette = new FactionPalette(next.factions());
        return true;
    }

    public @Nullable MatchPresentation view() {
        return view;
    }

    public FactionPalette palette() {
        return palette;
    }
}
