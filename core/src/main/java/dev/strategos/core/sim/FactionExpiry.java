package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import java.util.Objects;

/** Trusted adapter fact: every primary human reservation for this faction expired. Never a client command. */
@CoreApi
public record FactionExpiry(FactionSlot faction) implements InputEvent {
    public FactionExpiry {
        Objects.requireNonNull(faction, "faction");
    }
}
