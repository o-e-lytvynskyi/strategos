package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TechId;

/**
 * Faction {@code faction} finished researching {@code tech} (TECH-01): its effects already apply. The feed and the
 * statistics read it.
 */
@CoreApi
public record TechResearched(FactionSlot faction, TechId tech) implements MatchEvent {}
