package dev.strategos.mod.server.net;

/** Per-recipient encoded bytes, replenished by the server tick and capped at one bounded burst. */
final class ChannelBudget {
    private final int bytesPerTick;
    private final int burst;
    private int tick;
    private int available;

    ChannelBudget(int bytesPerTick, int burst, int tick) {
        if (bytesPerTick <= 0 || burst < bytesPerTick) {
            throw new IllegalArgumentException("invalid channel allowance");
        }
        this.bytesPerTick = bytesPerTick;
        this.burst = burst;
        this.tick = tick;
        available = burst;
    }

    boolean take(int now, int bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative channel charge");
        long elapsed = Integer.toUnsignedLong(now - tick);
        available = (int) Math.min(burst, available + elapsed * bytesPerTick);
        tick = now;
        if (bytes > available) return false;
        available -= bytes;
        return true;
    }
}
