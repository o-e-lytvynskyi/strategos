/**
 * The RTS camera rig (CAM-02, ADR-0017, 04 §13.2): pure maths, stepped once a client tick and interpolated per frame,
 * so the same input gives the same pose at any frame rate.
 */
@NullMarked
package dev.strategos.core.client.camera;

import org.jspecify.annotations.NullMarked;
