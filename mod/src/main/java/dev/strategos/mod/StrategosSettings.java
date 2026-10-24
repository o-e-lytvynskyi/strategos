package dev.strategos.mod;

import java.nio.file.Path;

/**
 * The server's switches: whether a match fault is rethrown after it is recorded (the dev and GameTest runs set
 * {@code strategos.faults.rethrow=true}; production contains faults), the bodies created per server tick, where fault
 * reports go, the block writer's budgets, the journal and the path threads setting from {@link StrategosConfig}, and a
 * dev hook for the
 * arena smoke: {@code
 * strategos.smoke.leakTickets} makes disposing an arena match keep its chunk tickets, as a crash would, so the next
 * start must drop them (MAP-07.4).
 */
public record StrategosSettings(
        boolean faultsRethrow,
        int spawnsPerTick,
        Path faultDir,
        boolean leakTickets,
        int writerBudget,
        int importBudget,
        StrategosConfig.Journal journal,
        int pathThreads,
        int journalRingMinutes) {
    public static final int SPAWNS_PER_TICK = 64;

    /** From the system properties and the server config, which NeoForge has loaded by {@code ServerAboutToStartEvent}. */
    public static StrategosSettings fromSystemProperties(Path serverDir) {
        StrategosConfig config = StrategosConfig.INSTANCE;
        return new StrategosSettings(
                Boolean.getBoolean("strategos.faults.rethrow"),
                SPAWNS_PER_TICK,
                serverDir.resolve("strategos").resolve("faults"),
                Boolean.getBoolean("strategos.smoke.leakTickets"),
                config.writerBudget.getAsInt(),
                config.importBudget.getAsInt(),
                config.journal.get(),
                config.pathThreads.getAsInt(),
                config.journalRingMinutes.getAsInt());
    }
}
