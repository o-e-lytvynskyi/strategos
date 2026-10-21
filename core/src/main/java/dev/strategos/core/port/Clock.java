package dev.strategos.core.port;

/** The only time source of the core (04 §2.2): measurements read it, the simulation never does. */
@FunctionalInterface
public interface Clock {
    long nanoTime();
}
