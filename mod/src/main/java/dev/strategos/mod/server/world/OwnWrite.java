package dev.strategos.mod.server.world;

/**
 * Whether the block writer is writing right now (BLD-15): set around each of its writes, on the server thread, so the
 * block-change router (T7) ignores Strategos's own edits; the core updates its own overlays for them. A field of the
 * writer rather than a thread-local, since every write runs on the server thread.
 */
public final class OwnWrite {
    private boolean active;

    public boolean active() {
        return active;
    }

    void around(Runnable write) {
        if (active) {
            throw new IllegalStateException("a block write inside a block write");
        }
        active = true;
        try {
            write.run();
        } finally {
            active = false;
        }
    }
}
