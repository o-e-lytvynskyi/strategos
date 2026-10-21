package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** Something entering a match in a tick's inputs; M1+ add world changes and terrain pages. */
@CoreApi
public sealed interface InputEvent permits CommandEnvelope {}
