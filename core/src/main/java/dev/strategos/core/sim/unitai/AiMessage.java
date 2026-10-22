package dev.strategos.core.sim.unitai;

/**
 * A message a unit's machine can handle (04 §7.5). M1's are bus messages of {@code core.sim} that name their unit:
 * {@code OrderReceived}, {@code StopOrdered}, {@code OrderFinished}, {@code PathDelivered}, {@code PathInvalidated},
 * {@code Arrived} and {@code Stuck}; {@code StatsChanged} names a faction or a unit and reaches no machine until a state
 * handles it. Handlers match a message's exact class, as the bus does.
 */
public interface AiMessage {}
