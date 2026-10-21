package dev.strategos.mod;

import java.nio.file.Path;

/**
 * The server's switches: whether a match fault is rethrown after it is recorded (the dev and GameTest runs set
 * {@code strategos.faults.rethrow=true}; production contains faults), the bodies created per server tick, and where
 * fault reports go.
 */
public record StrategosSettings(boolean faultsRethrow, int spawnsPerTick, Path faultDir) {
    public static final int SPAWNS_PER_TICK = 64;

    public static StrategosSettings fromSystemProperties(Path serverDir) {
        return new StrategosSettings(
                Boolean.getBoolean("strategos.faults.rethrow"),
                SPAWNS_PER_TICK,
                serverDir.resolve("strategos").resolve("faults"));
    }
}
