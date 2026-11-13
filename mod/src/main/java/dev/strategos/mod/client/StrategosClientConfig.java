package dev.strategos.mod.client;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The client's options (CAM-02, 04 §15.4): {@code strategos-client.toml}, a {@code CLIENT} config: how fast the RTS
 * camera pans and turns, as multipliers of the rig's speeds, whether the cursor at the window's edge pans, and the
 * first-match hints already seen (UI-14).
 */
public final class StrategosClientConfig {
    public static final String FILE = "strategos-client.toml";

    public static final StrategosClientConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<StrategosClientConfig, ModConfigSpec> built =
                new ModConfigSpec.Builder().configure(StrategosClientConfig::new);
        INSTANCE = built.getLeft();
        SPEC = built.getRight();
    }

    public final ModConfigSpec.DoubleValue panSpeed;
    public final ModConfigSpec.DoubleValue rotateSpeed;
    public final ModConfigSpec.BooleanValue edgePan;
    public final ModConfigSpec.ConfigValue<List<? extends String>> seenHints;

    private StrategosClientConfig(ModConfigSpec.Builder b) {
        panSpeed = b.comment(
                        "The RTS camera's pan speed, times the default; the eye still moves at most 5 blocks a tick.")
                .defineInRange("panSpeed", 1.0, 0.25, 4.0);
        rotateSpeed = b.comment("The RTS camera's turn speed on the keys, times the default.")
                .defineInRange("rotateSpeed", 1.0, 0.25, 4.0);
        edgePan = b.comment("Whether the cursor at the window's edge pans the RTS camera.")
                .define("edgePan", true);
        seenHints = b.comment("The first-match hints already shown and dismissed; empty them to see the hints again.")
                .defineListAllowEmpty("seenHints", List.of(), o -> o instanceof String);
    }
}
