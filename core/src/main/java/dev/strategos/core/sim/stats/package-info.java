/**
 * The modifier pipeline (ADR-0013, 04 §8.1): every number a unit uses is its template's value at a value path, changed
 * by the modifiers of its faction and its own, evaluated in a canonical order and cached per template and faction.
 */
@NullMarked
package dev.strategos.core.sim.stats;

import org.jspecify.annotations.NullMarked;
