package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/** What an {@code fx} event shows or sounds (AUD-01): a unit hit, a unit's death. */
@CoreApi
public enum FxKind {
    UNIT_HIT,
    UNIT_DIED
}
