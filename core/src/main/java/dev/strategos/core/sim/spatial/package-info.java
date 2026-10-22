/**
 * The spatial index (CORE-08, 04 §10.1): units in a grid of 8-block cells rebuilt at the end of every tick's
 * movement, static entities by page, and allocation-free queries that report in unit index order.
 */
@NullMarked
package dev.strategos.core.sim.spatial;

import org.jspecify.annotations.NullMarked;
