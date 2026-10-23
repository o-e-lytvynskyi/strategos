package dev.strategos.core.sim;

import dev.strategos.core.sim.unitai.AiMessage;

/** Unit {@code handle} was hit by unit {@code attacker} this tick and lives (CMB-02): an aggressive unit hits back. */
public record Attacked(int handle, int attacker) implements BusMessage, AiMessage {}
