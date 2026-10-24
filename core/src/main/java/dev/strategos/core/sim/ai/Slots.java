package dev.strategos.core.sim.ai;

/** What each number of a scripted player's {@link AiMemory} holds. */
final class Slots {
    /** The build order's step. */
    static final int STEP = 0;
    /** One more than the count of the step's building when the step began; 0 before. */
    static final int STEP_BASE = 1;
    /** The next site to try. */
    static final int SITE = 2;
    /** One more than the tick a building was last placed; 0 before. */
    static final int PLACED = 3;
    /** The waves launched. */
    static final int WAVE = 4;
    /** One more than the tick the current wave mustered; 0 while none musters. */
    static final int MUSTER = 5;
    /** The tick of the last command sent. */
    static final int LAST_COMMAND = 6;
    /** The commands sent so far. */
    static final int COMMANDS = 7;
    /** One more than the packed corner of the last site found (AI-02), skipped at the next search; 0 before. */
    static final int LAST_SITE = 8;
    /** One more than the tick the build order's next building came due unplaced (AI-02's reserve); 0 while none. */
    static final int DUE_SINCE = 9;
    /** One more than the tick a dropsite by a far node came due unaffordable (AI-02's reserve); 0 while none. */
    static final int EXPAND_SINCE = 10;

    private Slots() {}
}
