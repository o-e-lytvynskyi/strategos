package dev.strategos.mod.server.body;

/** How many bodies may still be created this server tick, across every match (64 by default). */
public final class SpawnBudget {
    private int remaining;

    public void reset(int perTick) {
        remaining = perTick;
    }

    /** Takes one spawn; false once the tick's budget is spent. */
    public boolean tryTake() {
        if (remaining <= 0) {
            return false;
        }
        remaining--;
        return true;
    }

    public int remaining() {
        return remaining;
    }
}
