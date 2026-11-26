package dev.strategos.core.sim;

import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.shared.CoreApi;
import java.util.Objects;

/** Trusted, journaled first-tick public faction presentation; never a client-authorized setup. */
@CoreApi
public record PresentationSetup(FactionPresentation presentation) implements InputEvent {
    public PresentationSetup {
        Objects.requireNonNull(presentation);
    }
}
