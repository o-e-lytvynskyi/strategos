/**
 * World picking for the RTS screen (CAM-04, 04 §13.4): rays from the cursor through a frame's own matrices, the cursor
 * in window points as GLFW and Minecraft's mouse report it (never framebuffer pixels, twice as many on Retina), and
 * box selection over projected points.
 */
@NullMarked
package dev.strategos.core.client.pick;

import org.jspecify.annotations.NullMarked;
