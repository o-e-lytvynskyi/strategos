package dev.strategos.mod;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The server's technical tunables (04 §15.4, spec §4.4): {@code strategos-server.toml}, a {@code SERVER} config, so
 * each world keeps its own in {@code serverconfig/}. Anything that changes a match's outcome is a {@code MatchRules}
 * value instead. NeoForge loads server configs just before {@code ServerAboutToStartEvent}
 * (ServerLifecycleHooks.java:96), where {@link StrategosSettings} reads them. Keys whose features come later are
 * defined now, so the file's shape is settled: {@code pathThreads} (T8), {@code journal} (T8), the rates (T15),
 * {@code maxMatches} and {@code debugPermission} (T15, T25).
 */
public final class StrategosConfig {
    public static final String FILE = "strategos-server.toml";

    public static final StrategosConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<StrategosConfig, ModConfigSpec> built = new ModConfigSpec.Builder().configure(StrategosConfig::new);
        INSTANCE = built.getLeft();
        SPEC = built.getRight();
    }

    /** Where a match's input journal goes. */
    public enum Journal {
        OFF,
        MEMORY,
        DISK
    }

    public final ModConfigSpec.IntValue pathThreads;
    public final ModConfigSpec.EnumValue<Journal> journal;
    public final ModConfigSpec.IntValue writerBudget;
    public final ModConfigSpec.IntValue importBudget;
    public final ModConfigSpec.IntValue commandRate;
    public final ModConfigSpec.IntValue commandBurst;
    public final ModConfigSpec.IntValue handleRate;
    public final ModConfigSpec.IntValue violationsPerMinute;
    public final ModConfigSpec.IntValue maxMatches;
    public final ModConfigSpec.IntValue debugPermission;

    private StrategosConfig(ModConfigSpec.Builder b) {
        pathThreads = b.comment("Path job threads: -1 picks from the cores, 0 runs jobs on the server thread.")
                .defineInRange("pathThreads", -1, -1, 4);
        journal = b.comment("A match's input journal: off, kept in memory, or also written to the world's folder.")
                .defineEnum("journal", Journal.MEMORY);
        writerBudget = b.comment("Blocks Strategos writes per tick across all matches.")
                .defineInRange("writerBudget", 64, 1, 4_096);
        importBudget = b.comment("Blocks a map import writes per tick while no match in its level runs.")
                .defineInRange("importBudget", 8_192, 64, 65_536);
        commandRate = b.comment("Commands a player may send per second, sustained.")
                .defineInRange("commandRate", 40, 1, 1_000);
        commandBurst = b.comment("Commands a player may send at once.").defineInRange("commandBurst", 80, 1, 10_000);
        handleRate = b.comment("Unit handles a player's commands may name per second.")
                .defineInRange("handleRate", 3_000, 1, 100_000);
        violationsPerMinute = b.comment("Malformed or refused messages a player may send per minute before a kick.")
                .defineInRange("violationsPerMinute", 100, 1, 10_000);
        maxMatches = b.comment("Matches the server runs at once.").defineInRange("maxMatches", 16, 1, 256);
        debugPermission = b.comment("The permission level of the debug commands and overlays.")
                .defineInRange("debugPermission", 2, 0, 4);
    }

    /** The config files this mod registers; the inventory test reads it. */
    public static List<String> files() {
        return List.of(FILE);
    }
}
