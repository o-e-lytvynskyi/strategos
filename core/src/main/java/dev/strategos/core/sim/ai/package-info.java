/**
 * The computer players (AI-01, AI-17, 04 §14): a player like any other. A {@link ComputerPlayer} reads an {@link
 * AiView}, a read-only facade over the match's queries, and writes only ordinary commands through a {@link
 * CommandSink}; they enter the next tick and pass the same validators as a human's. Work per think is capped in
 * evaluation units by an {@link AiBudget}. Nothing here touches the match's state: the core's {@code AiSystem}
 * schedules the thinks and keeps each player's {@link AiMemory}.
 */
@NullMarked
package dev.strategos.core.sim.ai;

import org.jspecify.annotations.NullMarked;
