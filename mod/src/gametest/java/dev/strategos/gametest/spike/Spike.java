package dev.strategos.gametest.spike;

/** One spike's run on the spike server: {@link SpikeDriver} calls it every tick until it says it is done. */
interface Spike {
    /** Measured work, in {@code ServerTickEvent.Pre}: inside the tick whose time the samplers read. */
    void preTick();

    /** Bookkeeping, in {@code ServerTickEvent.Post}, after the tick's time is taken; true when the spike is done. */
    boolean postTick();

    /** Writes the report; {@code DONE} only when {@code complete}. */
    void finish(boolean complete);
}
