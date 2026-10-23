/**
 * The client's model of its match (NET-08, 04 §13.5): what replication told it, updated by the payload handlers on the
 * client thread and read by the overlays and the HUD. Pure Java, without Minecraft.
 */
@NullMarked
package dev.strategos.core.client.model;

import org.jspecify.annotations.NullMarked;
