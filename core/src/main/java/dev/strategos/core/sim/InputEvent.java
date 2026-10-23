package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * Something entering a match in a tick's inputs: a command, a rebuilt terrain page, the agents in the space, damage a
 * body took, the path jobs an {@code ADAPTIVE} adapter found finished, or a new template set.
 */
@CoreApi
public sealed interface InputEvent
        permits CommandEnvelope, TerrainPage, AgentUpdate, ExternalDamage, JobDelivery, ContentSwap {}
