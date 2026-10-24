package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A damaged building's block at match-local ({@code x}, {@code z}) and height {@code y} fell (BLD-05): the dust of a
 * broken {@code state} there, for those who see it; the writer takes the block away.
 */
@CoreApi
public record BlockFell(int x, int y, int z, String state) implements MatchEvent {}
