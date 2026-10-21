package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/** Something a step tells the mod: a command's result or a match event. */
@CoreApi
public sealed interface OutputEvent permits CommandResult, MatchEvent {}
