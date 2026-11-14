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

    /** One more than the handle of the enemy the wave focuses (AI-03); 0 while none. */
    static final int FOCUS = 11;
    /** The wave's state (AI-03): 0 none out, 1 out, 2 going home. */
    static final int FIGHT = 12;
    /** One more than the tick the composition was last planned; 0 before. */
    static final int PLAN_AT = 13;
    /** What the plan wants trained, in permille: spearmen, archers << 16, horsemen << 32. */
    static final int WANT = 14;
    /** The retreats ordered so far. */
    static final int RETREATS = 15;

    /** 1 when the last think of the wave's fight stood at the enemy centre and saw no enemy soldier near it (the way is clear), else 0. */
    static final int CLEAR = 16;

    /** One more than the index of the opening drawn at the first think (AI-04); 0 before the draw. */
    static final int OPENING = 17;
    /** One more than the tick of the last wave's leaving (an attack's tick); 0 before any. */
    static final int LAST_ATTACK = 18;
    /** One more than the tick of the last lost building the rebuilder handled; 0 before any. */
    static final int REBUILD = 19;
    /** One more than the packed corner of the wave's camp (its cosmetic blocks stand); 0 while none. */
    static final int CAMP = 20;
    /** One more than the camp's ground level, a block row. */
    static final int CAMP_Y = 21;
    /** One more than the tick the bell was rung (AI-04's defence); 0 while it is not. */
    static final int BELL = 22;
    /** One more than the tick an enemy was last seen within 32 blocks of the base's centre; 0 before. */
    static final int NEAR = 23;
    /** One more than the tick the defence last had a fight at home (a blow, or an enemy soldier near); 0 before. */
    static final int THREAT = 24;

    private Slots() {}
}
