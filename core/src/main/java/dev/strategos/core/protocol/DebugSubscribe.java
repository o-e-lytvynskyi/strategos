package dev.strategos.core.protocol;

import dev.strategos.core.shared.CoreApi;

/**
 * The {@code debug_subscribe} payload (PATH-10): the overlay layers an operator wants, as {@link DebugLayer} bits, and
 * its area, the square {@code radius} blocks around it each way. No layer at all ends the subscription.
 */
@CoreApi
public record DebugSubscribe(int layers, int radius) {
    public static final int MIN_RADIUS = 8;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 32;

    /** Ends a subscription. */
    public static final DebugSubscribe NONE = new DebugSubscribe(0, DEFAULT_RADIUS);

    /** The layers as a varint, then the radius: two bytes. */
    public static final WireCodec<DebugSubscribe> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, DebugSubscribe value) {
            out.writeVarInt(value.layers());
            out.writeVarInt(value.radius());
        }

        @Override
        public DebugSubscribe read(WireReader in) {
            int layers = in.readVarInt(DebugLayer.ALL);
            int radius = in.readVarInt(MAX_RADIUS);
            if (radius < MIN_RADIUS) {
                throw new WireException("radius " + radius + " below " + MIN_RADIUS);
            }
            return new DebugSubscribe(layers, radius);
        }

        @Override
        public int maxSize() {
            return 2;
        }
    };

    public DebugSubscribe {
        if ((layers & ~DebugLayer.ALL) != 0) {
            throw new IllegalArgumentException("unknown debug layer bits in " + layers);
        }
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius " + radius + " outside " + MIN_RADIUS + ".." + MAX_RADIUS);
        }
    }

    public boolean wants(DebugLayer layer) {
        return layer.in(layers);
    }
}
