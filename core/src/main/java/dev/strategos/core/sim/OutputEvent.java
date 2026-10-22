package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** Something a step tells the mod: a command's result, a match event or a block to write. */
@CoreApi
public sealed interface OutputEvent permits CommandResult, MatchEvent, WorldEffect, Notice {}
