package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** Something entering a match in a tick's inputs: a command or a rebuilt terrain page. */
@CoreApi
public sealed interface InputEvent permits CommandEnvelope, TerrainPage {}
