package dev.strategos.core.sim.ai;

/**
 * A think's work cap in evaluation units (AI-01, 04 §14): each unit, building or node the view lists, and each command
 * sent, spends from it; once spent, the think stops where it is. Never time, so a think is deterministic.
 */
public final class AiBudget {
    /** The most a think may spend. */
    public static final int CAP = 5_000;

    private final int cap;
    private int spent;

    public AiBudget(int cap) {
        this.cap = cap;
    }

    /** Spends {@code units} if they fit; whether they did. */
    public boolean spend(int units) {
        if (spent + units > cap) {
            spent = cap;
            return false;
        }
        spent += units;
        return true;
    }

    /** Spends what is left: the think ends where it is (a level's commands per think, AI-05). */
    public void exhaust() {
        spent = cap;
    }

    public boolean exhausted() {
        return spent >= cap;
    }

    public int spent() {
        return spent;
    }

    public int cap() {
        return cap;
    }
}
