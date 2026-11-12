/**
 * The client's fog shroud (FOW-07, 04 §13.6): translucent quads over unexplored and explored terrain from
 * {@code ClientMatchModel}'s vision grid. Solo fog for M6; M8's team vision and mirages build on this, they do not
 * replace it.
 */
@NullMarked
package dev.strategos.mod.client.fog;

import org.jspecify.annotations.NullMarked;
