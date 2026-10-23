package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.CoreApi;

/**
 * A new template set for the match (DATA-03, ADR-0012), from a good reload: INGEST swaps it in before the tick's
 * commands, so every unit takes its new values from this tick on. It is journaled with the set itself, so a replay
 * swaps the same set at the same tick. The core refuses a set that lacks the template of a live unit, as the server
 * does before it sends one.
 */
@CoreApi
public record ContentSwap(TemplateSet set) implements InputEvent {}
