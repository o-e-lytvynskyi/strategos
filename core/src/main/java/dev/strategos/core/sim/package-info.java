/**
 * The match kernel: the driving port ({@code MatchCore}) and its records, the phase scheduler, the per-match bus and
 * the struct-of-arrays stores. Deterministic: no wall clock, no unseeded randomness, no hash-ordered collections, no
 * threads (ArchUnit rule B4).
 */
@NullMarked
package dev.strategos.core.sim;

import org.jspecify.annotations.NullMarked;
