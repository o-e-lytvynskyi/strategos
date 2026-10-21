package dev.strategos.gametest.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.strategos.core.shared.Handle;
import dev.strategos.mod.Strategos;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The L5 server smoke: boots a dedicated server, checks it, writes the marker the Gradle guard reads, and stops the
 * server. Acts only when {@code strategos.smoke} is {@code true} (run 1; the restart run of T9b uses {@code restart}).
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class SmokeDriver {
    private static final Logger LOG = LoggerFactory.getLogger(SmokeDriver.class);

    private SmokeDriver() {}

    /** Lowest priority, so it runs after the holder's own listeners. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onServerStarted(ServerStartedEvent event) {
        if (!"true".equals(System.getProperty("strategos.smoke"))) {
            return;
        }
        MinecraftServer server = event.getServer();
        // Step 1: the mod is loaded and core runs inside the mod module. The match steps join in T8.
        String problem = null;
        if (!ModList.get().isLoaded(Strategos.MOD_ID)) {
            problem = "strategos is not loaded";
        } else if (Handle.class.getModule() != Strategos.class.getModule()) {
            problem = "core runs in module " + Handle.class.getModule().getName() + ", the mod in "
                    + Strategos.class.getModule().getName();
        }
        writeMarker(problem);
        server.halt(false); // never true: this handler runs on the server thread
    }

    private static void writeMarker(@Nullable String problem) {
        JsonObject marker = new JsonObject();
        marker.addProperty("ok", problem == null);
        if (problem != null) {
            marker.addProperty("reason", problem);
            LOG.error("Server smoke failed: {}", problem);
        }
        String path = System.getProperty("strategos.smoke.marker");
        if (path == null) {
            throw new IllegalStateException("strategos.smoke.marker is not set");
        }
        try {
            Path file = Path.of(path);
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(
                    file, new GsonBuilder().setPrettyPrinting().create().toJson(marker) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the smoke marker " + path, e);
        }
    }
}
