package dev.strategos.core.protocol;

import dev.strategos.core.shared.Hashable;

/**
 * A command into a match. Records stay permissive: the command rules reject bad arguments with a reason
 * ({@link RejectReason#INVALID_ARGUMENT}) rather than the constructor throwing, so a journal always decodes.
 */
public sealed interface Command extends Hashable permits Move, Stop, SpawnUnits, Attack, AttackMove, Follow, Grant {}
