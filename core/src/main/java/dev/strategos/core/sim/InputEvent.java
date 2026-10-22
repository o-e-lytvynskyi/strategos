package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Something entering a match in a tick's inputs: a command, a rebuilt terrain page, the agents in the space, or damage a
 * body took.
 */
@CoreApi
public sealed interface InputEvent permits CommandEnvelope, TerrainPage, AgentUpdate, ExternalDamage {}
