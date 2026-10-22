/**
 * The terrain model of pathfinding (ADR-0015, 04 §9): block classes, voxel slices, pages of layered surfaces and the
 * grid they form. Pure code: slices come from the adapter's captures, pages are built in jobs.
 */
@NullMarked
package dev.strategos.core.sim.nav;

import org.jspecify.annotations.NullMarked;
