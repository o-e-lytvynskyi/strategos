package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The layers of the movement overlay's feed (PATH-10, 04 §15.2): what a subscriber asks for, one bit each in {@link
 * DebugSubscribe#layers()}, and what one {@code debug_feed} payload carries, by its wire code. Surfaces and components
 * come a page at a time; paths, unit markers and the panel as everything in the subscriber's area at once.
 */
@CoreApi
public enum DebugLayer {
    /** Each surface's floor, kind and layer in its column. */
    SURFACES,
    /** Each surface's component (PATH-04). */
    COMPONENTS,
    /** Each unit's path ahead of it, and whether it waits for one. */
    PATHS,
    /** Each unit's marker: its activity tier, its ticks without progress and its stuck ladder rung. */
    UNITS,
    /** The match's numbers: the path queue, latency and compute time, the phase times. */
    PANEL;

    /** Every layer's bit. */
    public static final int ALL = (1 << 5) - 1;

    /** The wire code, fixed whatever the constants' order. */
    public int code() {
        return switch (this) {
            case SURFACES -> 0;
            case COMPONENTS -> 1;
            case PATHS -> 2;
            case UNITS -> 3;
            case PANEL -> 4;
        };
    }

    public int bit() {
        return 1 << code();
    }

    /** Whether {@code layers}, a set of bits, holds this layer. */
    public boolean in(int layers) {
        return (layers & bit()) != 0;
    }

    /** The lower-case name commands and the overlay use. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DebugLayer ofCode(int code) {
        for (DebugLayer layer : values()) {
            if (layer.code() == code) {
                return layer;
            }
        }
        throw new WireException("unknown debug layer " + code);
    }

    /** The layer named {@code id}, or null. */
    public static @Nullable DebugLayer byId(String id) {
        for (DebugLayer layer : values()) {
            if (layer.id().equals(id)) {
                return layer;
            }
        }
        return null;
    }
}
