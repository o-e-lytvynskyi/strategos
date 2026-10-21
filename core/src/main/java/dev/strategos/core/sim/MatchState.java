package dev.strategos.core.sim;

/**
 * Everything a snapshot holds plus per-tick scratch, with no Minecraft reference. Not {@code @CoreApi}: the mod cannot
 * reach it (B2). T5a brings only the tick, which the scheduler reads; T5b adds the stores, the bus and the scratch
 * (M0 spec 1.6 row 44).
 */
public final class MatchState {
    private long tick;

    MatchState() {}

    public long tick() {
        return tick;
    }

    void setTick(long tick) {
        this.tick = tick;
    }
}
