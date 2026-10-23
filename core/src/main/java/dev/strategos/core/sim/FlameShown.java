package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A torch's blow shows flames at the block at match-local ({@code x}, {@code z}) and height {@code y} of the building it
 * burns (CMB-03): particles and a crackle for those who see the cell, never a fire block.
 */
@CoreApi
public record FlameShown(int x, int y, int z) implements MatchEvent {}
