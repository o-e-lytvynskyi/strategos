package dev.strategos.mod.archfixture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** B4 (mod)'s violating fixture: a thread pool outside mod.server.jobs. */
public final class ThreadFixture {
    private ThreadFixture() {}

    static ExecutorService pool() {
        return Executors.newFixedThreadPool(2);
    }
}
