package dev.strategos.core.sim;

/** A message on the per-match {@link MatchBus}. */
public sealed interface BusMessage permits OrderReceived, StopOrdered, Arrived, StatsChanged {}
