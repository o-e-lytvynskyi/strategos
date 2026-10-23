/**
 * The input action layer (UI-01, 04 §13.3): pure and L1-tested. Raw keys and mouse buttons become our {@link
 * dev.strategos.core.client.input.KeyCode}s, {@link dev.strategos.core.client.input.Binding}s map them to {@link
 * dev.strategos.core.client.input.Action}s per {@link dev.strategos.core.client.input.ActionContext}, and {@link
 * dev.strategos.core.client.input.InputState} turns a frame's raw events into action events with gestures. Handlers
 * turn actions into intents, never into game logic.
 */
@NullMarked
package dev.strategos.core.client.input;

import org.jspecify.annotations.NullMarked;
