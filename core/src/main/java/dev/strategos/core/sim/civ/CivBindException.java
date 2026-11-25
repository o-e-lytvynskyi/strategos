package dev.strategos.core.sim.civ;

import dev.strategos.core.shared.CoreApi;

/** A match's civilisations cannot be bound to a template set (CIV-01): one is missing, or the default is. */
@CoreApi
public final class CivBindException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public CivBindException(String message) {
        super(message);
    }
}
