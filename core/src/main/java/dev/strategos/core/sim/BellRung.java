package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A town bell was rung (BLD-16) at match-local ({@code x}, {@code z}) and height {@code y}, the middle of the building
 * that rang it: a bell's strike for those who see the cell.
 */
@CoreApi
public record BellRung(int x, int y, int z) implements MatchEvent {}
